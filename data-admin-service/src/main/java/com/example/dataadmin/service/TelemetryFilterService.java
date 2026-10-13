package com.example.dataadmin.service;

import com.example.dataadmin.dto.processing.TelemetryFilterSelectionRequest;
import com.example.dataadmin.entity.TelemetryParseRuleConfig;
import com.example.dataadmin.entity.TelemetryFilterSearchRecord;
import com.example.dataadmin.entity.TelemetrySystemConfig;
import com.example.dataadmin.enums.TelemetryFilterNodeType;
import com.example.dataadmin.mapper.DeviceSatelliteMapper;
import com.example.dataadmin.mapper.ProcessedTelemetryFilterSelectionMapper;
import com.example.dataadmin.mapper.TelemetryParseRuleConfigMapper;
import com.example.dataadmin.mapper.TelemetrySystemConfigMapper;
import com.example.dataadmin.vo.processing.DeviceSatelliteOptionVO;
import com.example.dataadmin.vo.processing.TelemetryFilterNodeVO;
import com.example.dataadmin.vo.processing.TelemetrySystemNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** 按需展开遥测筛选树并持久保存参数勾选状态。 */
@Service
@Transactional(readOnly = true)
public class TelemetryFilterService {
    /** 复用操作日志服务，离线构造时可不注入。 */
    @org.springframework.beans.factory.annotation.Autowired
    private OperationLogService operationLogs;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataadmin.mapper.MonitorDashboardWidgetMapper widgetMapper;

    private final DeviceSatelliteMapper deviceMapper;
    private final TelemetrySystemConfigMapper systemMapper;
    private final TelemetryParseRuleConfigMapper ruleMapper;
    private final ProcessedTelemetryFilterSelectionMapper selectionMapper;
    private final TelemetrySelectionScopeService scopeService;

    public TelemetryFilterService(DeviceSatelliteMapper deviceMapper,
            TelemetrySystemConfigMapper systemMapper,
            TelemetryParseRuleConfigMapper ruleMapper,
            ProcessedTelemetryFilterSelectionMapper selectionMapper,
            TelemetrySelectionScopeService scopeService) {
        this.deviceMapper = deviceMapper;
        this.systemMapper = systemMapper;
        this.ruleMapper = ruleMapper;
        this.selectionMapper = selectionMapper;
        this.scopeService = scopeService;
    }

    /** 按当前展开位置返回直属节点，设备级参数排在系统节点前面。 */
    public List<TelemetryFilterNodeVO> list(String taskId, Long deviceId, Long systemId,
            String telemetryCode, Integer selectionType, Long targetId) {
        requireTask(taskId);
        long scopeId = scopeService.require(taskId, selectionType, targetId);
        if (telemetryCode != null && !telemetryCode.trim().isEmpty()) {
            if (deviceId != null || systemId != null) {
                throw new IllegalArgumentException("按遥测代号搜索时不能同时指定设备卫星或所属系统");
            }
            return search(taskId, telemetryCode, selectionType, scopeId);
        }
        List<TelemetryFilterNodeVO> nodes = new ArrayList<>();
        if (deviceId == null) {
            if (systemId != null) {
                throw new IllegalArgumentException("展开所属系统时必须指定设备卫星主键");
            }
            for (DeviceSatelliteOptionVO device : deviceMapper.findOptions(taskId, null)) {
                nodes.add(TelemetryFilterNodeVO.of(device.getId(), device.getName(),
                        TelemetryFilterNodeType.DEVICE, false, true));
            }
            return nodes;
        }
        if (deviceMapper.findActive(taskId, deviceId) == null) {
            throw new IllegalArgumentException("当前任务下不存在有效的设备卫星");
        }
        if (systemId != null && systemMapper.findActive(taskId, deviceId, systemId) == null) {
            throw new IllegalArgumentException("当前设备下不存在有效的所属系统");
        }
        // 一次取得当前设备已选代号，再只查询当前展开层级直属参数。
        Set<String> selected = new HashSet<>(selectionMapper.findSelectedCodes(
                taskId, deviceId, selectionType, scopeId));
        for (TelemetryParseRuleConfig rule : ruleMapper.findDirectBySystem(taskId, deviceId, systemId)) {
            String name = rule.getTelemetryName() == null ? "" : rule.getTelemetryName();
            nodes.add(TelemetryFilterNodeVO.of(rule.getId(),
                    name + "/" + rule.getTelemetryCode(), TelemetryFilterNodeType.PARAMETER,
                    selected.contains(rule.getTelemetryCode()), false));
        }
        long parentId = systemId == null ? 0L : systemId;
        for (TelemetrySystemConfig system : systemMapper.findChildren(taskId, deviceId, parentId)) {
            nodes.add(TelemetryFilterNodeVO.of(system.getId(), system.getSystemName(),
                    TelemetryFilterNodeType.SYSTEM, false, true));
        }
        return nodes;
    }

    /** 遥测代号搜索直接返回当前任务全部匹配的可勾选参数。 */
    private List<TelemetryFilterNodeVO> search(String taskId, String telemetryCode,
            int selectionType, long targetId) {
        // 去除输入两端空白后按完整遥测代号查询。
        String code = telemetryCode.trim();
        List<TelemetryFilterNodeVO> nodes = new ArrayList<>();
        for (TelemetryFilterSearchRecord record : ruleMapper.searchByTelemetryCode(
                taskId, code, selectionType, targetId)) {
            String name = record.getTelemetryName() == null ? "" : record.getTelemetryName();
            TelemetryFilterNodeVO node = TelemetryFilterNodeVO.of(record.getId(),
                    name + "/" + record.getTelemetryCode(), TelemetryFilterNodeType.PARAMETER,
                    Boolean.TRUE.equals(record.getChecked()), false);
            node.setDeviceSatelliteId(record.getDeviceSatelliteId());
            node.setDeviceSatelliteName(record.getDeviceSatelliteName());
            // 直接使用导入时保存的完整系统路径，不逐条查询系统表。
            node.setSystemName(record.getSystemName());
            nodes.add(node);
        }
        return nodes;
    }

    /** 参数管理页面查询设备下全部系统层级，供前端组织树形显示。 */
    public List<TelemetrySystemNodeVO> systems(String taskId, Long deviceId) {
        requireTask(taskId);
        if (deviceId == null || deviceMapper.findActive(taskId, deviceId) == null) {
            throw new IllegalArgumentException("当前任务下不存在有效的设备卫星");
        }
        List<TelemetrySystemConfig> systems = systemMapper.findByDevice(taskId, deviceId);
        Map<Long, TelemetrySystemNodeVO> nodesById = new HashMap<>();
        for (TelemetrySystemConfig system : systems) {
            TelemetrySystemNodeVO node = new TelemetrySystemNodeVO();
            node.setId(system.getId());
            node.setSystemName(system.getSystemName());
            node.setParentId(system.getParentId());
            node.setSortOrder(system.getSortOrder());
            nodesById.put(node.getId(), node);
        }
        // 第二遍按父主键挂接，根节点按导入顺序返回。
        List<TelemetrySystemNodeVO> roots = new ArrayList<>();
        for (TelemetrySystemConfig system : systems) {
            TelemetrySystemNodeVO node = nodesById.get(system.getId());
            TelemetrySystemNodeVO parent = nodesById.get(system.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 即时保存单个参数勾选，主键和代号由有效解析配置确定。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(TelemetryFilterSelectionRequest request) {
        requireTask(request.getTaskId());
        // 参数勾选与整页可视化导入、参数表替换共同串行，锁内重新检查组件归属。
        if (deviceMapper.lockForReplacement(request.getTaskId()) == null) {
            throw new IllegalArgumentException("目标试验任务不存在");
        }
        long scopeId = scopeService.require(request.getTaskId(),
                request.getSelectionType(), request.getTargetId());
        TelemetryParseRuleConfig rule = ruleMapper.findActiveById(
                request.getTaskId(), request.getParseRuleId());
        if (rule == null) {
            throw new IllegalArgumentException("当前任务下不存在有效的遥测参数");
        }
        boolean before = selectionMapper.findSelectedCodes(request.getTaskId(), rule.getDeviceSatelliteId(),
                request.getSelectionType(), scopeId).contains(rule.getTelemetryCode());
        boolean after = Boolean.TRUE.equals(request.getChecked());
        // 请求返回成功但状态没有变化时，不写重复操作日志。
        org.springframework.web.context.request.RequestAttributes attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(com.example.dataadmin.config.OperationLogAdvice.SELECTION_RECORDED,
                    true, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
        if (before == after) { return; }
        selectionMapper.save(request.getTaskId(), rule.getDeviceSatelliteId(),
                rule.getTelemetryCode(), request.getSelectionType(), scopeId,
                after ? 0 : 1);
        if (operationLogs != null) {
            com.example.dataadmin.entity.DeviceSatellite device = deviceMapper.findActive(request.getTaskId(), rule.getDeviceSatelliteId());
            String scope = com.example.dataadmin.enums.BusinessEnums.SelectionType.fromCode(request.getSelectionType()).getLabel();
            com.example.dataadmin.entity.MonitorDashboardWidget widget = scopeId == 0 ? null : widgetMapper.findById(scopeId);
            String detail = "筛选框：" + scope + "；目标ID：" + scopeId
                    + (widget == null ? "" : "；组件名称：" + widget.getWidgetTitle() + "；组件ID：" + widget.getId())
                    + "；设备卫星：" + (device == null ? "未取得名称" : device.getName()) + "；设备卫星ID：" + rule.getDeviceSatelliteId()
                    + "；所属系统路径：" + rule.getSystemName() + "；参数名称：" + rule.getTelemetryName()
                    + "；遥测代号：" + rule.getTelemetryCode() + "；参数ID：" + rule.getId()
                    + "；状态：" + (before ? "已勾选" : "未勾选") + " → " + (after ? "已勾选" : "未勾选");
            operationLogs.record(request.getTaskId(), com.example.dataadmin.enums.SystemLogSource.TELEMETRY_FILTER,
                    com.example.dataadmin.enums.SystemLogLevel.INFO,
                    scope + "遥测参数" + (after ? "已勾选" : "已取消勾选"), detail);
        }
    }

    private void requireTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
    }
}
