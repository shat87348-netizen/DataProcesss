package com.example.dataprocess.collection;

import com.example.dataprocess.collection.receive.CollectInterfaceReceiver;
import com.example.dataprocess.collection.receive.TcpInterfaceReceiver;
import com.example.dataprocess.collection.receive.UdpInterfaceReceiver;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.entity.CalibrationChannelRuntimeConfig;
import com.example.dataprocess.entity.PdxpFrameSource;
import com.example.dataprocess.mapper.TelemetryCodeMappingMapper;
import com.example.dataprocess.protocol.rpc.PdxpProtocolField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.dataprocess.mapper.CollectInterfaceRuntimeMapper;
import com.example.dataprocess.processing.handler.ProtocolHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** 根据数据库配置动态启动和停止采集接口。 */
@Component
public class CollectInterfaceManager implements DisposableBean {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;

    /** 提交异常上下文，采集线程不直接写数据库。 */
    private void alarm(com.example.dataprocess.entity.CollectInterfaceRuntimeConfig config,
            com.example.common.enums.DataAlarmType type, String detail) {
        if (alarmRecorder != null) { alarmRecorder.record(config, type, detail); }
    }


    /** 接口管理日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CollectInterfaceManager.class);
    /** UDP传输方式的数据库枚举值。 */
    private static final int TRANSFER_TYPE_UDP = 1;
    /** TCP传输方式的数据库枚举值。 */
    private static final int TRANSFER_TYPE_TCP = 2;
    /** 接口运行配置数据访问组件。 */
    private final CollectInterfaceRuntimeMapper runtimeMapper;
    /** 采集接口内存统计组件。 */
    private final CollectInterfaceStatistics statistics;
    /** 网络数据采集线程池。 */
    private final ThreadPoolExecutor collectExecutor;
    /** 协议处理器注册表。 */
    private final ProtocolHandlerRegistry protocolHandlerRegistry;
    /** 当前任务校准通道配置加载组件。 */
    private final CalibrationRuntimeConfigManager calibrationConfigManager;
    /** 当前已经启动的接口。 */
    private final Map<Long, RunningInterface> runningInterfaces =
            new HashMap<Long, RunningInterface>();
    /** 数据处理服务是否正在停止。 */
    private volatile boolean stopping;

    /** 提交采集任务前使用的目录加载器。 */
    private BaselineSatelliteDirectory satelliteDirectory;
    /** 启动采集接口时加载PDXP设备身份和解析字段。 */
    private TelemetryCodeMappingMapper telemetryMappingMapper;
    /** 读取协议配置中的设备卫星主键。 */
    private ObjectMapper objectMapper;

    @Autowired
    public void setTelemetryMappingMapper(TelemetryCodeMappingMapper mapper) {
        this.telemetryMappingMapper = mapper;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 注入目录加载器，保留已有离线直接构造入口。 */
    @Autowired
    public void setSatelliteDirectory(BaselineSatelliteDirectory satelliteDirectory) {
        this.satelliteDirectory = satelliteDirectory;
    }

    public CollectInterfaceManager(
            CollectInterfaceRuntimeMapper runtimeMapper,
            CollectInterfaceStatistics statistics,
            @Qualifier("dataCollectExecutor")
            ThreadPoolExecutor collectExecutor,
            ProtocolHandlerRegistry protocolHandlerRegistry,
            CalibrationRuntimeConfigManager calibrationConfigManager) {
        this.runtimeMapper = runtimeMapper;
        this.statistics = statistics;
        this.collectExecutor = collectExecutor;
        this.protocolHandlerRegistry = protocolHandlerRegistry;
        this.calibrationConfigManager = calibrationConfigManager;
    }

    /** 数据处理服务启动完成后自动恢复全部启用采集接口。 */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startEnabledInterfacesAfterStartup() {
        if (stopping) {
            return;
        }
        List<CollectInterfaceRuntimeConfig> enabledConfigs = new ArrayList<CollectInterfaceRuntimeConfig>();
        try {
            // 第一步：从数据库读取全部启用且未删除的接口运行配置。
            enabledConfigs =
                    runtimeMapper.findAllEnabledInterfaces();
            // 第二步：接口启动前按各自任务加载有效校准通道配置。
            attachCalibrationConfigs(enabledConfigs);
            // 第三步：逐个提交接收任务，单个接口失败不会阻断其他接口启动。
            synchronizeRunningInterfaces(enabledConfigs);
            LOGGER.info("数据处理服务启动后采集接口恢复完成，启用接口数量：{}",
                    enabledConfigs.size());
        } catch (Exception exception) {
            for (CollectInterfaceRuntimeConfig config : enabledConfigs) {
                alarm(config, com.example.common.enums.DataAlarmType.INTERFACE_START,
                        "启动配置加载失败，采集接口尚未恢复：" + exception.getMessage());
            }
            // 数据库整体查询失败时保留服务进程，等待后续管理服务再次同步。
            LOGGER.error("数据处理服务启动后恢复采集接口失败，原因：{}",
                    exception.getMessage(), exception);
        }
    }

    /** 按Admin通知的完整接口主键列表同步当前运行接口。 */
    public synchronized List<Long> syncInterfaces(List<Long> interfaceIds) {
        if (stopping) {
            throw new IllegalStateException("数据处理服务正在停止，不能同步采集接口");
        }
        // 第一步：去除空主键和重复主键，得到Admin要求运行的完整列表。
        List<Long> normalizedIds = normalizeInterfaceIds(interfaceIds);
        // 第二步：空列表表示停止全部接口，不执行无意义的数据库IN查询。
        List<CollectInterfaceRuntimeConfig> configs = normalizedIds.isEmpty()
                ? new ArrayList<CollectInterfaceRuntimeConfig>()
                : runtimeMapper.findEnabledInterfacesByIds(normalizedIds);
        // 第三步：接口同步前按各自任务刷新有效校准通道配置。
        attachCalibrationConfigs(configs);
        // 第四步：按数据库实际配置启动、更新和停止接口。
        synchronizeRunningInterfaces(configs);
        LOGGER.info("采集接口同步完成，通知数量：{}，实际启用数量：{}",
                normalizedIds.size(), configs.size());
        List<Long> runningIds = new ArrayList<Long>();
        for (Map.Entry<Long, RunningInterface> entry : runningInterfaces.entrySet()) {
            if (!entry.getValue().future.isDone()) { runningIds.add(entry.getKey()); }
        }
        java.util.Collections.sort(runningIds);
        return runningIds;
    }

    /** 按任务加载有效校准通道，同一任务的接口共用配置快照。 */
    private void attachCalibrationConfigs(
            List<CollectInterfaceRuntimeConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        // 第一步：同一批接口允许分属不同试验任务，每个任务只查询一次校准配置。
        Map<String, Map<String, CalibrationChannelRuntimeConfig>> configsByTask =
                new HashMap<>();
        for (CollectInterfaceRuntimeConfig config : configs) {
            String taskId = config.getTaskId();
            if (taskId == null || taskId.trim().isEmpty()) {
                throw new IllegalArgumentException("启用采集接口未配置试验任务编号");
            }
            Map<String, CalibrationChannelRuntimeConfig> calibrationConfigs =
                    configsByTask.computeIfAbsent(taskId, calibrationConfigManager::load);
            config.setCalibrationConfigMap(calibrationConfigs);
        }
        // 第二步：各接口按自身任务和设备加载解析配置，处理线程不再逐帧查询参数表。
        attachPdxpFields(configs);
    }

    /** 每批同步仅为PDXP本地处理加载设备身份和解析字段。 */
    private void attachPdxpFields(List<CollectInterfaceRuntimeConfig> configs) {
        Map<Long, List<PdxpProtocolField>> fieldsByDevice = new HashMap<>();
        Map<Long, PdxpFrameSource> sourceByDevice = new HashMap<>();
        for (CollectInterfaceRuntimeConfig config : configs) {
            if (!Integer.valueOf(2).equals(config.getTransferProtocol())) {
                continue;
            }
            // RPC以卫星和通道代号确定身份，清理旧快照且不查询本地设备或参数。
            if (Integer.valueOf(1).equals(config.getRpcEnabled())) {
                config.setDeviceSatelliteId(null);
                config.setDeviceSatelliteName(null);
                config.setPdxpFields(java.util.Collections.emptyList());
                continue;
            }
            Long deviceId = protocolDeviceId(config.getProtocolConfigParams());
            if (deviceId == null || deviceId <= 0) {
                throw new IllegalArgumentException("PDXP协议配置缺少设备卫星主键");
            }
            PdxpFrameSource source = sourceByDevice.computeIfAbsent(deviceId,
                    id -> telemetryMappingMapper.findFrameSourceByDeviceId(config.getTaskId(), id));
            if (source == null) {
                throw new IllegalArgumentException("PDXP协议关联的设备卫星不存在：" + deviceId);
            }
            config.setDeviceSatelliteId(deviceId);
            config.setDeviceSatelliteName(source.getName());
            // 本地处理加载按位宽解析的参数快照，处理线程不再查询参数表。
            List<PdxpProtocolField> fields = fieldsByDevice.computeIfAbsent(deviceId,
                    id -> telemetryMappingMapper.findPdxpFieldsByDeviceId(config.getTaskId(), id));
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("PDXP协议关联的设备没有有效参数：" + deviceId);
            }
            config.setPdxpFields(fields);
        }
    }

    /** 从协议配置根对象读取设备卫星主键。 */
    private Long protocolDeviceId(String configParams) {
        try {
            JsonNode root = objectMapper.readTree(configParams);
            JsonNode id = root == null ? null : root.get("deviceSatelliteId");
            return id == null || id.isNull() ? null : id.asLong();
        } catch (Exception exception) {
            throw new IllegalArgumentException("PDXP协议动态参数格式不正确", exception);
        }
    }

    /** 清理Admin通知列表中的空值和重复值。 */
    private List<Long> normalizeInterfaceIds(List<Long> interfaceIds) {
        if (interfaceIds == null || interfaceIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<Long>();
        for (Long interfaceId : interfaceIds) {
            if (interfaceId != null) {
                normalizedIds.add(interfaceId);
            }
        }
        return new ArrayList<Long>(normalizedIds);
    }

    /** 根据数据库结果同步当前运行接口集合。 */
    private synchronized void synchronizeRunningInterfaces(
            List<CollectInterfaceRuntimeConfig> enabledConfigs) {
        Set<Long> enabledIds = new HashSet<Long>();
        for (CollectInterfaceRuntimeConfig config : enabledConfigs) {
            enabledIds.add(config.getInterfaceId());
            startOrRestart(config);
        }

        // 数据库中已禁用、删除或未出现在通知列表中的接口需要停止。
        Set<Long> currentIds = new HashSet<Long>(runningInterfaces.keySet());
        for (Long interfaceId : currentIds) {
            if (!enabledIds.contains(interfaceId)) {
                stopInterface(interfaceId, "接口已禁用或配置已移除");
            }
        }
    }

    /** 启动新接口，关键运行配置变化时先停止再重新启动。 */
    private void startOrRestart(CollectInterfaceRuntimeConfig config) {
        // 基线查询在启动或同步线程完成，采集和处理线程仅接收查询结果。
        if (satelliteDirectory != null) {
            config.setBaselineSatellites(satelliteDirectory.load(config));
        }
        RunningInterface current = runningInterfaces.get(config.getInterfaceId());
        if (current != null
                && current.getConfig().hasSameRuntimeSettings(config)) {
            // 监听参数未变化时直接替换校准Map和规则开关，避免重启网络监听。
            current.getConfig().setCalibrationConfigMap(
                    config.getCalibrationConfigMap());
            current.getConfig().setParameterRangeCheckEnabled(
                    config.getParameterRangeCheckEnabled());
            // 不重启监听时也刷新目录，后续帧使用最新名称快照。
            current.getConfig().setBaselineSatellites(config.getBaselineSatellites());
            current.getConfig().setDeviceSatelliteId(config.getDeviceSatelliteId());
            current.getConfig().setDeviceSatelliteName(config.getDeviceSatelliteName());
            current.getConfig().setPdxpFields(config.getPdxpFields());
            return;
        }
        if (current != null) {
            stopInterface(config.getInterfaceId(), "接口运行配置发生变化");
        }

        CollectInterfaceReceiver receiver = null;
        try {
            // 第一步：本管理器只根据传输方式创建UDP或TCP接收任务。
            Integer transferType = config.getTransferType();
            if (transferType == null) {
                throw new IllegalArgumentException("采集接口没有配置传输方式");
            }
            switch (transferType) {
                case TRANSFER_TYPE_UDP:
                    // UDP接收器内部完成端口绑定和协议会话组装。
                    receiver = UdpInterfaceReceiver.create(
                            config, protocolHandlerRegistry);
                    break;
                case TRANSFER_TYPE_TCP:
                    // TCP接收器内部完成端口绑定和协议会话组装。
                    receiver = TcpInterfaceReceiver.create(
                            config, protocolHandlerRegistry);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "暂不支持的采集传输方式：" + transferType);
            }

            // 第二步：这里是进入采集线程的切换点，由采集线程池调用receiver.run()。
            // submit立即返回Future任务句柄，Admin请求线程不会进入长期阻塞的网络接收循环。
            if (receiver instanceof UdpInterfaceReceiver) {
                ((UdpInterfaceReceiver) receiver).setAlarmRecorder(alarmRecorder);
            } else if (receiver instanceof TcpInterfaceReceiver) {
                ((TcpInterfaceReceiver) receiver).setAlarmRecorder(alarmRecorder);
            }
            Future<?> future = collectExecutor.submit(receiver);
            // 第三步：保存接口配置、接收任务和Future，供配置变更和停止接口使用。
            runningInterfaces.put(
                    config.getInterfaceId(),
                    new RunningInterface(config, receiver, future));
            if (alarmRecorder != null) {
                alarmRecorder.recover(config, com.example.common.enums.DataAlarmType.INTERFACE_START, null, null);
                alarmRecorder.recover(config, com.example.common.enums.DataAlarmType.INTERFACE_STOP, null, null);
            }
            LOGGER.info("采集接口已启动，接口编号：{}，传输方式：{}，传输协议：{}，地址：{}，端口：{}，组播地址：{}",
                    config.getInterfaceId(), config.getTransferType(),
                    config.getTransferProtocol(), config.getHost(), config.getPort(),
                    config.getMulticastIp());
        } catch (RejectedExecutionException exception) {
            // 接收任务未进入线程池时立即释放已经绑定的网络端口。
            stopCreatedReceiver(receiver);
            alarm(config, com.example.common.enums.DataAlarmType.INTERFACE_START, "采集线程池容量不足，接口未启动");
            LOGGER.error("采集线程池容量不足，接口编号：{}",
                    config.getInterfaceId(), exception);
        } catch (Exception exception) {
            // 启动中途失败时释放已经创建的接收器，避免端口残留占用。
            stopCreatedReceiver(receiver);
            alarm(config, com.example.common.enums.DataAlarmType.INTERFACE_START, "接口启动失败：" + exception.getMessage());
            LOGGER.error("采集接口启动失败，接口编号：{}",
                    config.getInterfaceId(), exception);
        }
    }

    /** 停止尚未登记到运行集合中的接收器。 */
    private void stopCreatedReceiver(CollectInterfaceReceiver receiver) {
        // 接收器尚未创建时无需执行清理。
        if (receiver != null) {
            receiver.stop();
        }
    }

    /** 停止指定接口并释放网络端口。 */
    private void stopInterface(Long interfaceId, String reason) {
        RunningInterface runningInterface = runningInterfaces.remove(interfaceId);
        if (runningInterface == null) {
            return;
        }
        // 先关闭接收器解除网络阻塞，再取消线程池中的接收任务。
        runningInterface.stop();
        LOGGER.info("采集接口已停止，接口编号：{}，原因：{}", interfaceId, reason);
    }

    /** 应用关闭时停止全部运行接口并最后回写统计。 */
    @Override
    public synchronized void destroy() {
        stopping = true;
        Set<Long> interfaceIds = new HashSet<Long>(runningInterfaces.keySet());
        for (Long interfaceId : interfaceIds) {
            stopInterface(interfaceId, "数据处理服务正在关闭");
        }
        statistics.flush();
    }

    /** 一个已经启动的采集接口运行句柄。 */
    private static final class RunningInterface {

        /** 启动时的接口配置快照。 */
        private final CollectInterfaceRuntimeConfig config;
        /** 负责该接口收包的任务。 */
        private final CollectInterfaceReceiver receiver;
        /** 采集线程池任务句柄。 */
        private final Future<?> future;

        private RunningInterface(
                CollectInterfaceRuntimeConfig config,
                CollectInterfaceReceiver receiver,
                Future<?> future) {
            this.config = config;
            this.receiver = receiver;
            this.future = future;
        }

        private CollectInterfaceRuntimeConfig getConfig() {
            return config;
        }

        /** 停止接收任务。 */
        private void stop() {
            receiver.stop();
            future.cancel(true);
        }
    }
}
