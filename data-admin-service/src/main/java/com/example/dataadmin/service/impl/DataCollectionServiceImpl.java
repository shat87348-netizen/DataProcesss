package com.example.dataadmin.service.impl;

import com.example.common.response.PageResult;
import com.example.dataadmin.dto.collection.InterfaceQueryRequest;
import com.example.dataadmin.dto.collection.InterfaceSaveRequest;
import com.example.dataadmin.dto.collection.ProtocolSaveRequest;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.CollectProtocolConfig;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.EnumData;
import com.example.dataadmin.mapper.CollectInterfaceConfigMapper;
import com.example.dataadmin.mapper.CollectProtocolConfigMapper;
import com.example.dataadmin.mapper.DeviceSatelliteMapper;
import com.example.dataadmin.mapper.SysRuntimeLogMapper;
import com.example.dataadmin.service.CollectInterfaceRuntimeSyncService;
import com.example.dataadmin.service.DataCollectionService;
import com.example.dataadmin.vo.collection.CollectionOverviewVO;
import com.example.dataadmin.vo.collection.ProtocolConfigVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * 数据采集业务实现。
 *
 * 负责采集接口、协议配置和采集事件的业务编排，数据库操作均通过
 *
 */
@Service
@Transactional(readOnly = true)
public class DataCollectionServiceImpl implements DataCollectionService {

    /** UDP传输方式编码。 */
    private static final int TRANSFER_TYPE_UDP = 1;

    /** 复用采集总览和采集统计组件的实时指标键格式。 */
    private static final String COLLECTION_CURRENT_PREFIX = "dashboard:collection:current:";

    /** 采集接口数据访问组件。 */
    private final CollectInterfaceConfigMapper interfaceMapper;

    /** 协议配置数据访问组件。 */
    private final CollectProtocolConfigMapper protocolMapper;
    /** 校验PDXP协议引用的设备卫星。 */
    private final DeviceSatelliteMapper deviceSatelliteMapper;


    /** 用于协议动态参数与 JSON 字符串之间的转换。 */
    private final ObjectMapper objectMapper;

    /** 采集接口运行任务同步组件。 */
    private final CollectInterfaceRuntimeSyncService interfaceRuntimeSyncService;

    /** 批量读取当前页接口的Redis实时采集指标。 */
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public DataCollectionServiceImpl(
            CollectInterfaceConfigMapper interfaceMapper,
            CollectProtocolConfigMapper protocolMapper,
            DeviceSatelliteMapper deviceSatelliteMapper,
            SysRuntimeLogMapper runtimeLogMapper,
            ObjectMapper objectMapper,
            CollectInterfaceRuntimeSyncService interfaceRuntimeSyncService,
            StringRedisTemplate redisTemplate) {
        this.interfaceMapper = interfaceMapper;
        this.protocolMapper = protocolMapper;
        this.deviceSatelliteMapper = deviceSatelliteMapper;
        this.objectMapper = objectMapper;
        this.interfaceRuntimeSyncService = interfaceRuntimeSyncService;
        this.redisTemplate = redisTemplate;
    }

    /** 保留既有离线构造入口，运行环境使用包含设备校验组件的构造器。 */
    DataCollectionServiceImpl(CollectInterfaceConfigMapper interfaceMapper,
            CollectProtocolConfigMapper protocolMapper,
            SysRuntimeLogMapper runtimeLogMapper,
            ObjectMapper objectMapper,
            CollectInterfaceRuntimeSyncService interfaceRuntimeSyncService) {
        this(interfaceMapper, protocolMapper, null, runtimeLogMapper,
                objectMapper, interfaceRuntimeSyncService, null);
    }

    @Override
    public CollectionOverviewVO getOverview(String taskId) {
        return interfaceMapper.findOverview(taskId);
    }

    @Override
    public PageResult<CollectInterfaceConfig> pageInterfaceConfigs(
            InterfaceQueryRequest request) {
        // 第一步：先执行原有分页查询，确定当前页接口和分页总数。
        long total = interfaceMapper.count(request);
        List<CollectInterfaceConfig> records = total == 0
                ? Collections.<CollectInterfaceConfig>emptyList()
                : interfaceMapper.findPage(request);
        // 第二步：仅查询当前页接口的采集量和实时速率，再回填分页结果。
        fillCollectionMetrics(request.getTaskId(), records);
        // 第三步：返回原有分页结构，附带当前页接口的采集统计。
        return new PageResult<CollectInterfaceConfig>(
                request.getPageNum(),
                request.getPageSize(),
                total,
                records);
    }

    /** 分页完成后批量补充采集量和速率，空页不再访问统计表或Redis。 */
    private void fillCollectionMetrics(String taskId, List<CollectInterfaceConfig> records) {
        // 第一步：跳过空页，并为没有统计或缓存的接口设置零值。
        if (records.isEmpty()) {
            return;
        }
        Map<Long, CollectInterfaceConfig> interfaces = new LinkedHashMap<>();
        Map<String, List<CollectInterfaceConfig>> taskInterfaces = new LinkedHashMap<>();
        for (CollectInterfaceConfig config : records) {
            config.setDataPacketCount(0L);
            config.setInterfaceRateKbps(0D);
            interfaces.put(config.getId(), config);
            taskInterfaces.computeIfAbsent(config.getTaskId(), ignored -> new ArrayList<>())
                    .add(config);
        }

        // 第二步：一次汇总当前页接口，核对任务编号后回填，避免接口换任务时串用历史量。
        List<CollectInterfaceConfig> counts = interfaceMapper.findCollectionCounts(
                taskId, new ArrayList<>(interfaces.keySet()));
        for (CollectInterfaceConfig count : counts) {
            CollectInterfaceConfig config = interfaces.get(count.getId());
            if (config != null && Objects.equals(config.getTaskId(), count.getTaskId())) {
                config.setDataPacketCount(count.getDataPacketCount());
            }
        }

        // 第三步：按任务批量读取当前页接口字段，支持未指定任务的跨任务分页。
        for (Map.Entry<String, List<CollectInterfaceConfig>> entry : taskInterfaces.entrySet()) {
            fillCollectionRates(entry.getKey(), entry.getValue());
        }
    }

    /** 同一任务的接口速率一次读取，缓存过期或Redis不可用时保留零值。 */
    private void fillCollectionRates(String taskId, List<CollectInterfaceConfig> records) {
        // 第一步：无任务的配置和既有离线构造入口不读取实时缓存。
        if (taskId == null || taskId.trim().isEmpty() || redisTemplate == null) {
            return;
        }
        List<Object> fields = new ArrayList<>();
        for (CollectInterfaceConfig config : records) {
            fields.add("interface:" + config.getId());
        }

        // 第二步：按当前页字段读取任务Hash，连接失败不影响接口列表和历史采集量。
        List<Object> values;
        try {
            values = redisTemplate.opsForHash().multiGet(COLLECTION_CURRENT_PREFIX + taskId, fields);
        } catch (RuntimeException ignored) {
            return;
        }
        if (values == null) {
            return;
        }

        // 第三步：逐项解析已有实时指标格式，异常字段独立跳过，不影响其他接口。
        for (int index = 0; index < records.size() && index < values.size(); index++) {
            Object value = values.get(index);
            if (value == null) {
                continue;
            }
            try {
                JsonNode metric = objectMapper.readTree(String.valueOf(value));
                JsonNode rate = metric == null ? null : metric.get("interfaceRateKbps");
                if (rate != null && rate.isNumber()) {
                    double rateKbps = rate.asDouble();
                    if (Double.isFinite(rateKbps) && rateKbps >= 0) {
                        records.get(index).setInterfaceRateKbps(rateKbps);
                    }
                }
            } catch (JsonProcessingException ignored) {
                // 无效实时指标保持默认零值，其他接口继续解析。
            }
        }
    }

    @Override
    public CollectInterfaceConfig getInterfaceConfig(Long id) {
        CollectInterfaceConfig config = interfaceMapper.findById(id);
        if (config == null) {
            throw new IllegalArgumentException("采集接口不存在");
        }
        return config;
    }

    @Override
    @Transactional
    public Long createInterfaceConfig(InterfaceSaveRequest request) {
        validateInterfaceRequest(request);
        CollectInterfaceConfig config = buildInterfaceConfig(null, request);
        // 启用状态下先校验监听地址，避免数据处理服务启动任务时端口冲突。
        validateEnabledEndpointAvailable(config);
        config.setDataPacketCount(0L);
        config.setIsDeleted(0);
        interfaceMapper.insert(config);
        // 新增结果以启用开关为准，在事务提交后同步完整运行列表。
        interfaceRuntimeSyncService.syncAfterCommit();
        return config.getId();
    }

    @Override
    @Transactional
    public void updateInterfaceConfig(
            Long id,
            InterfaceSaveRequest request) {
        getInterfaceConfig(id);
        validateInterfaceRequest(request);
        CollectInterfaceConfig config = buildInterfaceConfig(id, request);
        // 修改后的最终启用状态决定是否需要校验监听地址。
        validateEnabledEndpointAvailable(config);
        if (interfaceMapper.update(config) == 0) {
            throw new IllegalArgumentException("采集接口修改失败");
        }
        // 修改后由数据处理服务比较运行配置，按启用开关决定启停或重启。
        interfaceRuntimeSyncService.syncAfterCommit();
    }

    @Override
    @Transactional
    public void deleteInterfaceConfig(Long id) {
        CollectInterfaceConfig current = getInterfaceConfig(id);
        // 运行中的采集接口必须先禁用并释放监听端口，随后才允许删除。
        if (Integer.valueOf(1).equals(current.getEnabled())) {
            throw new IllegalArgumentException("请先禁用采集接口，再执行删除");
        }
        if (interfaceMapper.delete(id) == 0) {
            throw new IllegalArgumentException("采集接口删除失败");
        }
        // 删除后同步完整列表，使数据处理服务停止已经移除的采集任务。
        interfaceRuntimeSyncService.syncAfterCommit();
    }

    @Override
    @Transactional
    public void updateInterfaceEnabled(Long id, Integer enabled) {
        CollectInterfaceConfig current = getInterfaceConfig(id);
        validateEnabled(enabled);
        // 从禁用切换为启用时校验监听地址，防止绕过新增和修改校验。
        if (Integer.valueOf(1).equals(enabled)) {
            validateEndpointAvailable(
                    current.getId(),
                    current.getTransferType(),
                    current.getHost(),
                    current.getPort(),
                    current.getMulticastIp());
        }
        if (interfaceMapper.updateEnabled(id, enabled) == 0) {
            throw new IllegalArgumentException("采集接口启用状态修改失败");
        }
        // 启用开关提交后立即同步，启用时启动任务，禁用时停止任务。
        interfaceRuntimeSyncService.syncAfterCommit();
    }

    @Override
    public List<ProtocolConfigVO> listProtocolConfigs(String taskId) {
        List<CollectProtocolConfig> configs =
                protocolMapper.findAllByTaskId(taskId);
        List<ProtocolConfigVO> result =
                new ArrayList<ProtocolConfigVO>(configs.size());
        for (CollectProtocolConfig config : configs) {
            result.add(toProtocolVO(config));
        }
        return result;
    }

    @Override
    public ProtocolConfigVO getProtocolConfig(Long id) {
        return toProtocolVO(getProtocolEntity(id));
    }

    @Override
    @Transactional
    public Long createProtocolConfig(ProtocolSaveRequest request) {
        CollectProtocolConfig config = buildProtocolConfig(null, request);
        config.setIsDeleted(0);
        protocolMapper.insert(config);
        return config.getId();
    }

    @Override
    @Transactional
    public void updateProtocolConfig(
            Long id,
            ProtocolSaveRequest request) {
        getProtocolEntity(id);
        CollectProtocolConfig config = buildProtocolConfig(id, request);
        if (protocolMapper.update(config) == 0) {
            throw new IllegalArgumentException("协议配置修改失败");
        }
        // 修改正在使用的PDXP设备主键后，提交成功再刷新处理服务的字段快照。
        interfaceRuntimeSyncService.syncAfterCommit();
    }

    @Override
    @Transactional
    public void deleteProtocolConfig(Long id) {
        // 第一步：确认协议存在，再检查所有未删除采集接口的绑定关系。
        getProtocolEntity(id);
        requireProtocolUnbound(id);
        // 第二步：无绑定时执行逻辑删除，数据库同时再次检查绑定条件。
        if (protocolMapper.delete(id) == 0) {
            // 删除条件发生变化时重新取得接口名称，返回明确的绑定提示。
            requireProtocolUnbound(id);
            throw new IllegalArgumentException("协议配置删除失败");
        }
    }

    /** 未删除的采集接口仍占用协议，提示中列出所有绑定接口名称。 */
    private void requireProtocolUnbound(Long protocolConfigId) {
        List<String> interfaceNames = interfaceMapper.findActiveNamesByProtocolConfigId(
                protocolConfigId);
        if (!interfaceNames.isEmpty()) {
            throw new IllegalArgumentException("协议配置已被采集接口【"
                    + String.join("、", interfaceNames) + "】绑定，不能删除");
        }
    }

    @Override
    @Transactional
    public void updateProtocolEnabled(Long id, Integer enabled) {
        getProtocolEntity(id);
        validateEnabled(enabled);
        if (protocolMapper.updateEnabled(id, enabled) == 0) {
            throw new IllegalArgumentException("协议配置启用状态修改失败");
        }
    }

    /**
     * 构造采集接口实体，并填充默认状态。
     */
    private CollectInterfaceConfig buildInterfaceConfig(
            Long id,
            InterfaceSaveRequest request) {
        CollectInterfaceConfig config = new CollectInterfaceConfig();
        config.setId(id);
        config.setTaskId(request.getTaskId());
        config.setInterfaceName(request.getInterfaceName());
        config.setInterfaceType(request.getInterfaceType());
        config.setSendFrom(request.getSendFrom());
        config.setMessageContent(request.getMessageContent());
        config.setTransferType(request.getTransferType());
        // 传输协议按固定枚举编码保存。
        config.setTransferProtocol(request.getTransferProtocol());
        // 本地处理和RPC处理统一关联协议配置，处理开关只决定后续处理分支。
        config.setProtocolConfigId(request.getProtocolConfigId());
        // 清理监听地址两端空白，确保重复校验与实际绑定地址一致。
        config.setHost(request.getHost() == null
                ? null : request.getHost().trim());
        config.setPort(request.getPort());
        // 空组播地址统一保存为null，避免重复校验受到空字符串影响。
        config.setMulticastIp(trimToNull(request.getMulticastIp()));
        // 在线状态由最终启用开关派生，不接受前端单独指定状态。
        config.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        // RPC处理默认关闭，避免接口创建后在RPC定义不完整时误调用。
        config.setRpcEnabled(request.getRpcEnabled() == null
                ? 0 : request.getRpcEnabled());
        config.setRemark(request.getRemark());
        return config;
    }

    /**
     * 校验接口状态和关联协议配置。
     */
    private void validateInterfaceRequest(InterfaceSaveRequest request) {
        validateEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        // 传输协议必须匹配采集接口使用的固定枚举。
        if (EnumData.labelOf(BusinessEnums.TransferProtocol.values(),
                request.getTransferProtocol()) == null) {
            throw new IllegalArgumentException("传输协议只能为1（JSON）、2（PDXP）或3（FEP）");
        }
        // RPC开关与通用启用开关复用相同的枚举取值。
        validateEnabled(request.getRpcEnabled() == null
                ? 0 : request.getRpcEnabled());
        // 只有UDP接口允许配置组播地址，有值时必须属于IP组播范围。
        validateMulticastIp(request.getTransferType(), request.getMulticastIp());
        // 两种处理方式都必须选择存在的协议配置。
        if (request.getProtocolConfigId() == null) {
            throw new IllegalArgumentException("协议配置不能为空");
        }
        getProtocolEntity(request.getProtocolConfigId());
    }

    /**
     * 构造协议配置实体，将动态参数序列化为 JSON。
     */
    private CollectProtocolConfig buildProtocolConfig(
            Long id,
            ProtocolSaveRequest request) {
        validateEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        Map<String, Object> storedParams = request.getConfigParams();
        // PDXP字段改为设备卫星主键，创建和修改都必须验证任务范围。
        Map<String, Object> params = request.getConfigParams();
        Object value = params == null ? null : params.get("deviceSatelliteId");
        if (BusinessEnums.ProtocolType.PDXP.getValue().equals(request.getProtocolType())
                || value != null) {
            Long deviceId;
            try {
                deviceId = value == null ? null : Long.valueOf(value.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("设备卫星主键格式不正确", exception);
            }
            if (deviceId == null || deviceSatelliteMapper.findActive(
                    request.getTaskId(), deviceId) == null) {
                throw new IllegalArgumentException("协议必须选择当前任务下有效的设备卫星");
            }
            if (BusinessEnums.ProtocolType.PDXP.getValue().equals(request.getProtocolType())) {
                // PDXP本地配置只保留包头和设备主键，RPC配置继续保留fields。
                storedParams = new LinkedHashMap<>();
                if (params.containsKey("pdxphead")) {
                    storedParams.put("pdxphead", params.get("pdxphead"));
                }
                storedParams.put("deviceSatelliteId", deviceId);
            }
        }
        CollectProtocolConfig config = new CollectProtocolConfig();
        config.setId(id);
        config.setTaskId(request.getTaskId());
        config.setConfigName(request.getConfigName());
        config.setProtocolType(request.getProtocolType());
        config.setDataSourceType(request.getDataSourceType());
        config.setConfigDesc(request.getConfigDesc());
        config.setConfigParams(writeConfigParams(storedParams));
        config.setParserClass(request.getParserClass());
        config.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        return config;
    }

    /**
     * 查询协议配置实体，不存在时抛出统一业务异常。
     */
    private CollectProtocolConfig getProtocolEntity(Long id) {
        CollectProtocolConfig config = protocolMapper.findById(id);
        if (config == null) {
            throw new IllegalArgumentException("协议配置不存在");
        }
        return config;
    }

    /**
     * 将协议配置实体转换为对外返回对象。
     */
    private ProtocolConfigVO toProtocolVO(CollectProtocolConfig config) {
        ProtocolConfigVO result = new ProtocolConfigVO();
        result.setId(config.getId());
        result.setTaskId(config.getTaskId());
        result.setConfigName(config.getConfigName());
        result.setProtocolType(config.getProtocolType());
        result.setDataSourceType(config.getDataSourceType());
        result.setConfigDesc(config.getConfigDesc());
        result.setConfigParams(readConfigParams(config.getConfigParams()));
        result.setParserClass(config.getParserClass());
        result.setEnabled(config.getEnabled());
        result.setCreateTime(config.getCreateTime());
        result.setUpdateTime(config.getUpdateTime());
        return result;
    }

    /**
     * 将动态参数序列化为 JSON 字符串。
     */
    private String writeConfigParams(Map<String, Object> configParams) {
        try {
            return objectMapper.writeValueAsString(
                    configParams == null
                            ? Collections.<String, Object>emptyMap()
                            : configParams);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("协议动态参数格式不正确");
        }
    }

    /**
     * 将数据库中的 JSON 字符串还原为动态参数对象。
     */
    private Map<String, Object> readConfigParams(String configParams) {
        if (configParams == null || configParams.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(
                    configParams,
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("数据库中的协议动态参数格式不正确");
        }
    }

    /**
     * 校验启用状态。
     */
    private void validateEnabled(Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new IllegalArgumentException("启用状态只能为0或1");
        }
    }

    /** 仅对最终处于启用状态的接口校验监听地址。 */
    private void validateEnabledEndpointAvailable(
            CollectInterfaceConfig config) {
        // 禁用接口不会创建采集任务，允许暂时保存相同监听配置。
        if (!Integer.valueOf(1).equals(config.getEnabled())) {
            return;
        }
        validateEndpointAvailable(
                config.getId(),
                config.getTransferType(),
                config.getHost(),
                config.getPort(),
                config.getMulticastIp());
    }

    /** 校验相同监听配置没有被其他启用接口占用。 */
    private void validateEndpointAvailable(
            Long excludeId,
            Integer transferType,
            String host,
            Integer port,
            String multicastIp) {
        // 查询覆盖全部任务，因为所有接口最终由同一个数据处理服务绑定端口。
        long conflictCount = interfaceMapper.countEnabledEndpointConflicts(
                transferType,
                host,
                port,
                multicastIp,
                excludeId);
        if (conflictCount > 0) {
            throw new IllegalArgumentException(
                    "相同传输方式、监听地址、端口和组播地址的采集接口已经启用");
        }
    }

    /** 校验可选组播地址，并限制组播仅用于UDP采集接口。 */
    private void validateMulticastIp(
            Integer transferType,
            String multicastIp) {
        String normalizedMulticastIp = trimToNull(multicastIp);
        if (normalizedMulticastIp == null) {
            return;
        }
        if (!Integer.valueOf(TRANSFER_TYPE_UDP).equals(transferType)) {
            throw new IllegalArgumentException("只有UDP采集接口可以配置组播地址");
        }
        try {
            // 地址解析后必须处于IP协议规定的组播地址范围。
            InetAddress address = InetAddress.getByName(normalizedMulticastIp);
            if (!address.isMulticastAddress()) {
                throw new IllegalArgumentException("组播地址不合法");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("组播地址不合法");
        }
    }

    /** 清理可选字符串两端空白，空内容统一转为null。 */
    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }
}
