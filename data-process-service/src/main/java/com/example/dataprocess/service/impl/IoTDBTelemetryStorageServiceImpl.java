package com.example.dataprocess.service.impl;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.Telemetry;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryType;
import com.example.common.tool.IoTDBPathTool;
import com.example.common.enums.TelemetryFrameStatus;
import com.example.dataprocess.config.PdxpFramingProperties;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.service.IoTDBTelemetryStorageService;
import com.example.dataprocess.protocol.rpc.PdxpDataPayloadParser;
import com.example.dataprocess.tool.PdxpParser;
import com.google.protobuf.Timestamp;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.utils.Binary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 参数按任务与设备卫星身份保存，完整采集帧额外按采集接口隔离。 */
@Service
@ConditionalOnProperty(prefix = "iotdb", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class IoTDBTelemetryStorageServiceImpl implements IoTDBTelemetryStorageService {
    /** 业务数据固定数据库路径。 */
    private static final String ROOT_PATH = "root.db";
    /** 正常帧和异常帧统一使用的四个测点。 */
    private static final List<String> FRAME_MEASUREMENTS = Arrays.asList(
            "deviceSatelliteName", "channelName", "raw", "checkResultName");
    /** 统一整帧字段类型，原码始终保存为二进制。 */
    private static final List<TSDataType> FRAME_DATA_TYPES = Arrays.asList(
            TSDataType.TEXT, TSDataType.TEXT, TSDataType.BLOB, TSDataType.TEXT);
    private final SessionPool sessionPool;
    /** 异常帧回退复用接收端同一份长度配置，离线构造沿用默认值。 */
    private PdxpFramingProperties framingProperties = new PdxpFramingProperties();

    @org.springframework.beans.factory.annotation.Autowired
    public void setFramingProperties(PdxpFramingProperties framingProperties) {
        this.framingProperties = framingProperties;
    }

    public IoTDBTelemetryStorageServiceImpl(SessionPool sessionPool) {
        this.sessionPool = sessionPool;
    }

    /** 服务启动时创建不存在的IoTDB数据库。 */
    @PostConstruct
    public void initializeDatabase() throws Exception {
        try {
            sessionPool.createDatabase(ROOT_PATH);
        } catch (StatementExecutionException exception) {
            if (exception.getStatusCode()
                    != TSStatusCode.DATABASE_ALREADY_EXISTS.getStatusCode()) {
                throw exception;
            }
        }
    }

    /** 没有最终Proto时不写IoTDB。 */
    @Override
    public void savePdxpRawFrame(CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet) {
        // 当前存储结构要求有效的设备主键和最终数据域。
    }

    /** 本地参数解析失败时，保存能够确认身份且包含各类头的异常整帧。 */
    @Override
    public void markPdxpRawFrameAbnormal(CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet) throws Exception {
        // 第一步：异常回退仅用于本地处理，复用正常帧的任务和设备路径校验。
        String parameterRoot = parameterRoot(config, TelemetryType.TM_TYPE_DEVICE_TM);
        if (packet == null || Integer.valueOf(1).equals(config.getRpcEnabled())) {
            throw new IllegalArgumentException("异常整帧回退需要有效的本地处理PDXP包");
        }
        // 第二步：设备名称必须能拆出通道名称，缺少完整身份时不创建替代路径。
        String deviceName = config.getDeviceSatelliteName() == null
                ? "" : config.getDeviceSatelliteName().trim();
        int separator = deviceName.indexOf('_');
        if (separator <= 0 || separator == deviceName.length() - 1
                || deviceName.substring(0, separator).trim().isEmpty()
                || deviceName.substring(separator + 1).trim().isEmpty()) {
            throw new IllegalArgumentException("异常整帧缺少有效的设备名称和通道名称：" + deviceName);
        }
        String channelName = deviceName.substring(0, separator).trim();
        // 第三步：保持数据域可拆分的回退条件，原码则保留完整采集帧及各类头。
        PdxpDataPayloadParser.parse(packet.getData(), framingProperties.getCustomDataLength(),
                framingProperties.getTelemetryHeaderLength());
        byte[] raw = packet.getRawPacket();
        long timestamp = packet.getSendDate().atStartOfDay()
                .plus(packet.getTimeSinceMidnight())
                .toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        // 第四步：异常整帧按采集接口保存，继续只写四个测点，不生成参数或遥测消息。
        sessionPool.insertRecord(frameDevicePath(config, parameterRoot), timestamp,
                FRAME_MEASUREMENTS, FRAME_DATA_TYPES,
                frameValues(config.getDeviceSatelliteName(), channelName,
                        raw, TelemetryFrameStatus.ABNORMAL));
    }

    /** 旧独立写帧入口缺少设备主键，由统一批量入口处理。 */
    @Override
    public void saveRawFrame(Long interfaceId, TelemetryMessage message) {
        // 处理成功后由saveProcessedParameters一次保存整帧和参数。
    }

    /** 一次写入完整采集帧和最终Proto的全部遥测参数。 */
    @Override
    public void saveProcessedParameters(CollectInterfaceRuntimeConfig config,
            TelemetryMessage message, byte[] rawFrame) throws Exception {
        if (message == null || !message.hasProtoHead()) {
            throw new IllegalArgumentException("IoTDB写入缺少任务或遥测消息");
        }
        // 完整帧必须由采集处理链路传入，不使用Proto去头数据域代替。
        if (rawFrame == null || rawFrame.length == 0) {
            throw new IllegalArgumentException("IoTDB整帧写入缺少完整采集原码");
        }
        // 本地结果按设备主键保存，RPC结果按返回的卫星和通道代号保存。
        String parameterRoot = processedParameterRoot(config, message);
        String taskId = message.getProtoHead().getTaskId();
        if (!config.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("遥测消息任务编号与采集接口不一致");
        }
        long timestamp = sourceTimeMillis(message);
        int recordCount = message.getTelemetriesCount() + 1;
        List<String> deviceIds = new ArrayList<>(recordCount);
        List<Long> timestamps = new ArrayList<>(recordCount);
        List<List<String>> measurementsList = new ArrayList<>(recordCount);
        List<List<TSDataType>> dataTypesList = new ArrayList<>(recordCount);
        List<List<Object>> valuesList = new ArrayList<>(recordCount);

        // 整帧路径增加接口身份，测点仍为设备名称、通道名称、完整原码和检查结果。
        deviceIds.add(frameDevicePath(config, parameterRoot));
        timestamps.add(timestamp);
        measurementsList.add(FRAME_MEASUREMENTS);
        dataTypesList.add(FRAME_DATA_TYPES);
        // RPC帧检查失败写异常，本地正常结果保持正常，参数野值不改变整帧检查结果。
        TelemetryFrameStatus frameStatus = Integer.valueOf(1).equals(config.getRpcEnabled())
                && !message.getFrameCheckStatus()
                ? TelemetryFrameStatus.ABNORMAL : TelemetryFrameStatus.NORMAL;
        // RPC显示名称来自最终消息，名称缺失时显示返回编码，不读取本地导入设备。
        String deviceName = Integer.valueOf(1).equals(config.getRpcEnabled())
                ? displayName(message.getSatName(), message.getSatCode())
                : config.getDeviceSatelliteName();
        String channelName = Integer.valueOf(1).equals(config.getRpcEnabled())
                ? displayName(message.getChannelName(), message.getChannelCode())
                : message.getChannelName();
        valuesList.add(frameValues(deviceName, channelName, rawFrame, frameStatus));

        // 参数数值严格取Proto的value，不再使用valueText或派生状态索引。
        for (Map.Entry<String, Telemetry> entry : message.getTelemetriesMap().entrySet()) {
            Telemetry telemetry = entry.getValue();
            String telemetryCode = telemetry.getTmSymbol().trim().isEmpty()
                    ? entry.getKey() : telemetry.getTmSymbol();
            deviceIds.add(parameterRoot + "."
                    + IoTDBPathTool.pathNode(telemetryCode, "unknown_tm"));
            timestamps.add(timestamp);
            measurementsList.add(Arrays.asList(
                    "tmName", "value", "raw", "stateName", "processedAt"));
            dataTypesList.add(Arrays.asList(
                    TSDataType.TEXT, TSDataType.DOUBLE, TSDataType.BLOB,
                    TSDataType.TEXT, TSDataType.INT64));
            valuesList.add(Arrays.<Object>asList(
                    text(telemetry.getTmName()), telemetry.getValue(),
                    new Binary(telemetry.getRawData().toByteArray()),
                    text(telemetry.getStateName()), System.currentTimeMillis()));
        }
        sessionPool.insertRecords(deviceIds, timestamps,
                measurementsList, dataTypesList, valuesList);
    }

    /** 按处理方式选择独立存储身份，RPC不依赖协议中的本地设备主键。 */
    private String processedParameterRoot(CollectInterfaceRuntimeConfig config,
            TelemetryMessage message) {
        // 第一步：复用任务校验，阻止没有任务身份的结果入库。
        String taskRoot = taskRoot(config);
        // 第二步：RPC按返回编码生成同层级身份，实时帧任务查询可继续使用原有路径。
        if (Integer.valueOf(1).equals(config.getRpcEnabled())) {
            return taskRoot + "."
                    + IoTDBPathTool.rpcDeviceNode(message.getSatCode(), message.getChannelCode())
                    + ".tms." + telemetryTypeNode(message.getTmType());
        }
        // 第三步：本地结果沿用设备主键路径，与本地异常整帧保持一致。
        return parameterRoot(config, message.getTmType());
    }

    /** 根据本地设备主键构造遥测类型根路径。 */
    private String parameterRoot(CollectInterfaceRuntimeConfig config, TelemetryType type) {
        // 第一步：校验本地任务及设备身份，不允许构造占位设备节点。
        String taskRoot = taskRoot(config);
        if (config.getDeviceSatelliteId() == null || config.getDeviceSatelliteId() <= 0) {
            throw new IllegalArgumentException("本地遥测入库缺少有效设备卫星主键");
        }
        // 第二步：保持原有设备节点和遥测类型节点，历史本地数据无需迁移。
        return taskRoot
                + "." + IoTDBPathTool.prefixedPathNode("device_",
                        String.valueOf(config.getDeviceSatelliteId()), "unknown_device")
                + ".tms." + telemetryTypeNode(type);
    }

    /** 为完整采集帧增加接口层级，参数原有路径继续复用。 */
    private String frameDevicePath(CollectInterfaceRuntimeConfig config, String parameterRoot) {
        // 第一步：必须有真实接口主键，避免多个来源写到同一个占位路径。
        if (config.getInterfaceId() == null || config.getInterfaceId() <= 0) {
            throw new IllegalArgumentException("IoTDB整帧入库缺少有效采集接口主键");
        }
        // 第二步：在任务与设备卫星身份之间插入接口节点，保留其余路径和测点结构。
        String taskPath = taskRoot(config);
        return taskPath + "."
                + IoTDBPathTool.prefixedPathNode("interface_",
                        String.valueOf(config.getInterfaceId()), "unknown_interface")
                + parameterRoot.substring(taskPath.length()) + "._frame";
    }

    /** 为两种处理方式提供相同的有效任务根路径。 */
    private String taskRoot(CollectInterfaceRuntimeConfig config) {
        // 任务编号来自采集接口，不允许使用未知任务占位路径。
        if (config == null || config.getTaskId() == null || config.getTaskId().trim().isEmpty()) {
            throw new IllegalArgumentException("IoTDB写入缺少有效任务编号");
        }
        return ROOT_PATH + "."
                + IoTDBPathTool.prefixedPathNode("task_", config.getTaskId(), "unknown_task");
    }

    /** 正常帧和异常帧复用相同字段来源及中文检查结果。 */
    private List<Object> frameValues(String deviceName,
            String channelName, byte[] raw, TelemetryFrameStatus status) {
        // 显示名称由调用方按处理方式确定，测点名称保持既有查询约定。
        return Arrays.<Object>asList(text(deviceName), text(channelName),
                new Binary(raw), text(status.getLabel()));
    }

    /** RPC显示名称缺失时使用返回编码，避免查询页面出现空白身份。 */
    private String displayName(String name, String code) {
        // 返回消息中的名称优先，编码仅用于名称缺失时的显示回退。
        return name == null || name.trim().isEmpty() ? code : name;
    }

    /** 将遥测类型编码映射到稳定的路径节点。 */
    private String telemetryTypeNode(TelemetryType type) {
        switch (type) {
            case TM_TYPE_REAL_TM: return "real";
            case TM_TYPE_DELAY_TM: return "delay";
            case TM_TYPE_MEMORY_TM: return "memory";
            case TM_TYPE_DEVICE_TM: return "device";
            default: return "unknown";
        }
    }

    /** IoTDB文本统一用UTF-8编码。 */
    private Binary text(String value) {
        return new Binary(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** 帧与所有参数共用Proto消息时间，缺省时使用当前时间。 */
    private long sourceTimeMillis(TelemetryMessage message) {
        if (!message.hasTime()) {
            return System.currentTimeMillis();
        }
        Timestamp timestamp = message.getTime();
        return timestamp.getSeconds() * 1000L + timestamp.getNanos() / 1_000_000L;
    }
}
