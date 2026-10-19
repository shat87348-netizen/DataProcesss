package com.example.dataadmin.service.impl;

import com.example.common.response.PageResult;
import com.example.dataadmin.config.HttpCollectionProperties;
import com.example.dataadmin.dto.collection.InterfaceQueryRequest;
import com.example.dataadmin.dto.collection.InterfaceSaveRequest;
import com.example.dataadmin.dto.collection.ProtocolQueryRequest;
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
import com.example.dataadmin.service.support.HttpCollectionAddressSupport;
import com.example.dataadmin.vo.collection.CollectionOverviewVO;
import com.example.dataadmin.vo.collection.ProtocolConfigVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
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

    /** 离线构造沿用默认HTTP协议，运行环境通过系统配置统一覆盖。 */
    private HttpCollectionProperties httpCollectionProperties = new HttpCollectionProperties();

    /** 注入系统访问协议，保留既有构造入口供已有调用和测试复用。 */
    @Autowired
    public void setHttpCollectionProperties(HttpCollectionProperties properties) {
        this.httpCollectionProperties = properties;
    }

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
        // 第二步：计算HTTP完整接收地址，累计量直接使用接口表字段。
        for (CollectInterfaceConfig config : records) {
            fillReceiveUrl(config);
        }
        fillCollectionRatesForPage(records);
        // 第三步：返回原有分页结构，附带持久累计量和实时速率。
        return new PageResult<CollectInterfaceConfig>(
                request.getPageNum(),
                request.getPageSize(),
                total,
                records);
    }

    /** 分页完成后按任务批量补充实时速率，累计量由接口表直接返回。 */
    private void fillCollectionRatesForPage(List<CollectInterfaceConfig> records) {
        // 第一步：跳过空页，并为缺少累计值或实时缓存的接口设置零值。
        if (records.isEmpty()) {
            return;
        }
        Map<String, List<CollectInterfaceConfig>> taskInterfaces = new LinkedHashMap<>();
        for (CollectInterfaceConfig config : records) {
            if (config.getDataPacketCount() == null) {
                config.setDataPacketCount(0L);
            }
            config.setInterfaceRateKbps(0D);
            taskInterfaces.computeIfAbsent(config.getTaskId(), ignored -> new ArrayList<>())
                    .add(config);
        }
        // 第二步：按任务批量读取当前页接口速率，支持未指定任务的跨任务分页。
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

        // 第二步：按当前页字段读取任务Hash，连接失败不影响接口列表和长期累计量。
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
        // 详情地址只用于前端展示，始终按当前系统访问协议动态计算。
        fillReceiveUrl(config);
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
        // 唯一路径由数据库再次保证，并发创建冲突时转换为业务提示。
        try {
            interfaceMapper.insert(config);
        } catch (DuplicateKeyException exception) {
            throw translateHttpPathConflict(exception);
        }
        // 新增结果以启用开关为准，在事务提交后同步完整运行列表。
        interfaceRuntimeSyncService.syncAfterCommit();
        return config.getId();
    }

    @Override
    @Transactional
    public void updateInterfaceConfig(
            Long id,
            InterfaceSaveRequest request) {
        CollectInterfaceConfig current = getInterfaceConfig(id);
        validateInterfaceRequest(request);
        // 第一步：接口累计量按原任务长期保存，编辑时不允许更换任务编号。
        if (!Objects.equals(current.getTaskId(), request.getTaskId())) {
            throw new IllegalArgumentException("采集接口所属试验任务不能修改");
        }
        CollectInterfaceConfig config = buildInterfaceConfig(id, request);
        // 第二步：修改后的最终启用状态决定是否需要校验监听地址。
        validateEnabledEndpointAvailable(config);
        // 第三步：数据库再次核对任务归属，并发修改失败时由事务回滚。
        try {
            if (interfaceMapper.update(config) == 0) {
                throw new IllegalArgumentException("采集接口修改失败");
            }
        } catch (DuplicateKeyException exception) {
            throw translateHttpPathConflict(exception);
        }
        // 第四步：修改后由数据处理服务比较运行配置，按启用开关决定启停或重启。
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
        // 从禁用切换为启用时复核HTTP绑定或套接字监听地址，避免绕过保存校验。
        if (Integer.valueOf(1).equals(enabled)) {
            if (isHttp(current.getTransferType())) {
                validateHttpAddress(current.getHost(), current.getPort(), current.getReceivePath());
                validateHttpProtocol(current.getTaskId(), current.getTransferProtocol(),
                        current.getProtocolConfigId());
            }
            current.setEnabled(enabled);
            validateEnabledEndpointAvailable(current);
        }
        if (interfaceMapper.updateEnabled(id, enabled) == 0) {
            throw new IllegalArgumentException("采集接口启用状态修改失败");
        }
        // 启用开关提交后立即同步，启用时启动任务，禁用时停止任务。
        interfaceRuntimeSyncService.syncAfterCommit();
    }

    @Override
    public PageResult<ProtocolConfigVO> pageProtocolConfigs(ProtocolQueryRequest request) {
        // 第一步：先统计相同筛选条件下的总数，空结果不再读取分页记录。
        long total = protocolMapper.count(request);
        List<CollectProtocolConfig> configs = total == 0
                ? Collections.<CollectProtocolConfig>emptyList()
                : protocolMapper.findPage(request);
        // 第二步：沿用详情转换逻辑，返回中文枚举名称并清理历史废弃字段。
        List<ProtocolConfigVO> result =
                new ArrayList<ProtocolConfigVO>(configs.size());
        for (CollectProtocolConfig config : configs) {
            result.add(toProtocolVO(config));
        }
        // 第三步：按统一分页结构返回当前页、每页条数和总数。
        return new PageResult<ProtocolConfigVO>(
                request.getPageNum(), request.getPageSize(), total, result);
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
        // 先锁定协议行，再核对HTTP绑定，避免类型或任务在绑定过程中发生并发变化。
        getProtocolEntityForUpdate(id);
        CollectProtocolConfig config = buildProtocolConfig(id, request);
        // 已绑定HTTP接口的配置必须继续满足JSON类型、所属任务及启用约束。
        validateHttpProtocolBindings(config);
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
        getProtocolEntityForUpdate(id);
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
        // 第一步：只查询绑定接口的主键与名称，不在数据库中拼接提示文本。
        List<CollectInterfaceConfig> bindings = interfaceMapper.findActiveBindingsByProtocolConfigId(
                protocolConfigId);
        if (!bindings.isEmpty()) {
            // 第二步：在业务层为历史未命名接口补上可定位的编号。
            List<String> interfaceNames = new ArrayList<String>(bindings.size());
            for (CollectInterfaceConfig binding : bindings) {
                String name = binding.getInterfaceName();
                interfaceNames.add(name == null || name.trim().isEmpty()
                        ? "未命名采集接口（编号：" + binding.getId() + "）" : name.trim());
            }
            throw new IllegalArgumentException("协议配置已被采集接口【"
                    + String.join("、", interfaceNames) + "】绑定，不能删除");
        }
    }

    @Override
    @Transactional
    public void updateProtocolEnabled(Long id, Integer enabled) {
        // 启用状态与HTTP绑定在同一协议行锁下检查和更新。
        CollectProtocolConfig config = getProtocolEntityForUpdate(id);
        validateEnabled(enabled);
        // 运行中的HTTP接口仍使用该配置时，禁止单独禁用协议配置。
        config.setEnabled(enabled);
        validateHttpProtocolBindings(config);
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
        // 根据已有传输协议枚举识别文件交换，创建和修改共用同一保存规则。
        boolean fepProtocol = BusinessEnums.TransferProtocol.FEP.getValue()
                .equals(request.getTransferProtocol());
        // HTTP使用统一JSON接收分支，处理开关和组播地址不参与配置。
        boolean httpInterface = isHttp(request.getTransferType());
        config.setId(id);
        config.setTaskId(request.getTaskId());
        config.setInterfaceName(request.getInterfaceName());
        config.setInterfaceType(request.getInterfaceType());
        config.setSendFrom(request.getSendFrom());
        config.setMessageContent(request.getMessageContent());
        config.setTransferType(request.getTransferType());
        // 传输协议按固定枚举编码保存。
        config.setTransferProtocol(request.getTransferProtocol());
        // 文件交换使用系统配置，清空旧协议关联；其他协议保留所选配置。
        config.setProtocolConfigId(fepProtocol ? null : request.getProtocolConfigId());
        // 清理监听地址两端空白，确保重复校验与实际绑定地址一致。
        config.setHost(httpInterface ? HttpCollectionAddressSupport.normalizeHost(request.getHost())
                : request.getHost() == null ? null : request.getHost().trim());
        config.setPort(request.getPort());
        // 空组播地址统一保存为null，避免重复校验受到空字符串影响。
        config.setMulticastIp(httpInterface ? null : trimToNull(request.getMulticastIp()));
        // HTTP保存规范化接收路径，切换到其他传输方式时清除旧路径。
        config.setReceivePath(httpInterface
                ? HttpCollectionAddressSupport.normalizeReceivePath(request.getReceivePath()) : null);
        // 在线状态由最终启用开关派生，不接受前端单独指定状态。
        config.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        // HTTP和文件交换固定关闭RPC处理，其他协议未传开关时使用本地处理。
        config.setRpcEnabled(httpInterface || fepProtocol || request.getRpcEnabled() == null
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
        // HTTP固定JSON并选择有效JSON配置，旧组播值由保存逻辑清空。
        if (isHttp(request.getTransferType())) {
            validateHttpAddress(request.getHost(), request.getPort(), request.getReceivePath());
            validateHttpProtocol(request.getTaskId(), request.getTransferProtocol(),
                    request.getProtocolConfigId());
            return;
        }
        // 只有UDP接口允许配置组播地址，有值时必须属于IP组播范围。
        validateMulticastIp(request.getTransferType(), request.getMulticastIp());
        // 文件交换只使用系统配置，无需选择处理方式或查询关联协议配置。
        if (BusinessEnums.TransferProtocol.FEP.getValue().equals(request.getTransferProtocol())) {
            return;
        }
        // 其他传输协议的两种处理方式都必须选择存在的协议配置。
        if (request.getProtocolConfigId() == null) {
            throw new IllegalArgumentException("协议配置不能为空");
        }
        // 处理方式决定配置类型，前端筛选之外也校验直接提交的请求。
        CollectProtocolConfig protocol = getProtocolEntity(request.getProtocolConfigId());
        boolean rpcProcessing = Integer.valueOf(1).equals(request.getRpcEnabled());
        BusinessEnums.ProtocolType requiredType = rpcProcessing
                ? BusinessEnums.ProtocolType.RPC : BusinessEnums.ProtocolType.PDXP;
        if (!requiredType.getValue().equals(protocol.getProtocolType())) {
            throw new IllegalArgumentException(rpcProcessing
                    ? "转发方式只能选择RPC类型的协议配置"
                    : "处理方式只能选择PDXP类型的协议配置");
        }
    }

    /**
     * 构造协议配置实体，将动态参数序列化为 JSON。
     */
    private CollectProtocolConfig buildProtocolConfig(
            Long id,
            ProtocolSaveRequest request) {
        validateEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        // RPC只清理历史设备关联和废弃数据类型，不要求导入本地参数。
        Map<String, Object> storedParams = normalizeRpcConfigParams(
                request.getProtocolType(), request.getConfigParams());
        // PDXP字段改为设备卫星主键，创建和修改都必须验证任务范围。
        Map<String, Object> params = request.getConfigParams();
        Object value = params == null ? null : params.get("deviceSatelliteId");
        if (BusinessEnums.ProtocolType.PDXP.getValue().equals(request.getProtocolType())) {
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
            // PDXP本地配置只保留包头和设备主键，RPC使用独立字段配置。
            storedParams = new LinkedHashMap<>();
            if (params.containsKey("pdxphead")) {
                storedParams.put("pdxphead", params.get("pdxphead"));
            }
            storedParams.put("deviceSatelliteId", deviceId);
        }
        CollectProtocolConfig config = new CollectProtocolConfig();
        config.setId(id);
        config.setTaskId(request.getTaskId());
        config.setConfigName(request.getConfigName());
        config.setProtocolType(request.getProtocolType());
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

    /** 仅供写事务调用，锁定记录后复用协议不存在的统一提示。 */
    private CollectProtocolConfig getProtocolEntityForUpdate(Long id) {
        // 锁定与更新使用同一事务连接，事务提交或回滚时自动释放行锁。
        CollectProtocolConfig config = protocolMapper.findByIdForUpdate(id);
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
        result.setConfigDesc(config.getConfigDesc());
        // 查询历史配置时同样移除废弃属性，不要求先修改已有数据库记录。
        result.setConfigParams(normalizeRpcConfigParams(
                config.getProtocolType(), readConfigParams(config.getConfigParams())));
        result.setParserClass(config.getParserClass());
        result.setEnabled(config.getEnabled());
        result.setCreateTime(config.getCreateTime());
        result.setUpdateTime(config.getUpdateTime());
        return result;
    }

    /** 清理RPC历史设备关联与废弃属性，复制容器避免修改请求或查询对象。 */
    private Map<String, Object> normalizeRpcConfigParams(
            Integer protocolType, Map<String, Object> configParams) {
        // 第一步：只规范RPC配置，其他协议沿用原有动态参数结构。
        if (!BusinessEnums.ProtocolType.RPC.getValue().equals(protocolType)
                || configParams == null) {
            return configParams;
        }
        // 第二步：即使没有字段列表，也移除历史本地设备主键。
        Map<String, Object> cleanedParams = new LinkedHashMap<>(configParams);
        cleanedParams.remove("deviceSatelliteId");
        if (!(configParams.get("fields") instanceof List)) {
            return cleanedParams;
        }
        // 第三步：逐项复制并移除数据类型，自定义字段的其他属性完整保留。
        List<?> fields = (List<?>) configParams.get("fields");
        List<Object> cleanedFields = new ArrayList<>(fields.size());
        for (Object field : fields) {
            if (field instanceof Map) {
                Map<?, ?> source = (Map<?, ?>) field;
                Map<Object, Object> cleanedField = new LinkedHashMap<>(source);
                cleanedField.remove("dataType");
                cleanedFields.add(cleanedField);
            } else {
                cleanedFields.add(field);
            }
        }
        // 第四步：返回新的动态参数对象，供保存、列表和详情共用。
        cleanedParams.put("fields", cleanedFields);
        return cleanedParams;
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
        // HTTP共用服务端口，路径在所有未删除配置中保留，不受启用状态影响。
        if (isHttp(config.getTransferType())) {
            if (interfaceMapper.countHttpReceivePathConflicts(
                    config.getReceivePath(), config.getId()) > 0) {
                throw new IllegalArgumentException("HTTP接收路径已被其他采集接口占用");
            }
            return;
        }
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

    /** 使用已有枚举判断HTTP传输方式，避免重复定义协议编码。 */
    private boolean isHttp(Integer transferType) {
        return BusinessEnums.TransferType.HTTP.getValue().equals(transferType);
    }

    /** HTTP地址只验证格式，真实可访问性由部署和联调确认。 */
    private void validateHttpAddress(String host, Integer port, String receivePath) {
        // 第一步：复用地址工具校验IP或域名，以及统一前缀下的接收路径。
        HttpCollectionAddressSupport.normalizeHost(host);
        HttpCollectionAddressSupport.normalizeReceivePath(receivePath);
        // 第二步：业务层复核端口，启用历史配置时同样不能跳过范围校验。
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException("HTTP接收端口必须在1到65535之间");
        }
    }

    /** 保存与启用HTTP接口时复核JSON配置的类型、所属任务和启用状态。 */
    private void validateHttpProtocol(String taskId, Integer transferProtocol, Long protocolConfigId) {
        // 第一步：HTTP固定JSON，协议配置必须真实存在且未删除。
        if (!BusinessEnums.TransferProtocol.JSON.getValue().equals(transferProtocol)) {
            throw new IllegalArgumentException("HTTP采集接口的传输协议只能选择JSON");
        }
        if (protocolConfigId == null) {
            throw new IllegalArgumentException("HTTP采集接口必须选择JSON类型的协议配置");
        }
        // 持有协议行锁直到接口事务结束，防止绑定校验后配置被并发改为无效状态。
        CollectProtocolConfig protocol = getProtocolEntityForUpdate(protocolConfigId);
        // 第二步：只允许当前任务的已启用JSON配置，避免跨任务或禁用配置被引用。
        if (!BusinessEnums.ProtocolType.JSON.getValue().equals(protocol.getProtocolType())) {
            throw new IllegalArgumentException("HTTP采集接口只能选择JSON类型的协议配置");
        }
        if (taskId == null || !Objects.equals(taskId, protocol.getTaskId())) {
            throw new IllegalArgumentException("HTTP协议配置必须属于当前试验任务");
        }
        if (!Integer.valueOf(1).equals(protocol.getEnabled())) {
            throw new IllegalArgumentException("HTTP采集接口不能选择已禁用的协议配置");
        }
    }

    /** 修改协议配置时反向核对HTTP绑定，避免已保存接口的配置关系失效。 */
    private void validateHttpProtocolBindings(CollectProtocolConfig protocol) {
        // 第一步：只检查未删除HTTP绑定，其他采集协议沿用已有更新规则。
        List<CollectInterfaceConfig> bindings =
                interfaceMapper.findHttpBindingsByProtocolConfigId(protocol.getId());
        for (CollectInterfaceConfig binding : bindings) {
            // 第二步：禁用接口也保留绑定，配置类型和任务不能被改变为不匹配值。
            if (!BusinessEnums.ProtocolType.JSON.getValue().equals(protocol.getProtocolType())
                    || !Objects.equals(binding.getTaskId(), protocol.getTaskId())) {
                throw new IllegalArgumentException("协议配置已被HTTP采集接口【"
                        + binding.getInterfaceName() + "】绑定，不能修改为非JSON类型或其他任务");
            }
            // 第三步：启用HTTP接口仍依赖该配置，需先禁用接口再禁用配置。
            if (Integer.valueOf(1).equals(binding.getEnabled())
                    && !Integer.valueOf(1).equals(protocol.getEnabled())) {
                throw new IllegalArgumentException("请先禁用绑定的HTTP采集接口【"
                        + binding.getInterfaceName() + "】，再禁用协议配置");
            }
        }
    }

    /** 详情和分页统一补充计算字段，其他传输方式不返回完整HTTP地址。 */
    private void fillReceiveUrl(CollectInterfaceConfig config) {
        // 只根据当前配置生成展示地址，历史缺失路径的记录返回空。
        config.setReceiveUrl(isHttp(config.getTransferType())
                ? HttpCollectionAddressSupport.receiveUrl(httpCollectionProperties.getScheme(),
                        config.getHost(), config.getPort(), config.getReceivePath()) : null);
    }

    /** 将并发写入触发的路径唯一冲突转换为中文业务提示，保留其他数据库错误。 */
    private RuntimeException translateHttpPathConflict(DuplicateKeyException exception) {
        // 仅处理本次新增的唯一约束，其他主键或约束错误沿用既有异常处理。
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().contains("uk_collect_interface_http_receive_path")) {
                return new IllegalArgumentException("HTTP接收路径已被其他采集接口占用");
            }
        }
        return exception;
    }
}
