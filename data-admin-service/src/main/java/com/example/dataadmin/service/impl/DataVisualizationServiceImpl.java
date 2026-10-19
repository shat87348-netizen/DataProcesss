package com.example.dataadmin.service.impl;

import com.example.dataadmin.entity.MonitorDashboardWidget;
import com.example.dataadmin.entity.MonitorWidgetConfigItem;
import com.example.dataadmin.mapper.FepFileListMapper;
import com.example.dataadmin.mapper.MonitorDashboardWidgetMapper;
import com.example.dataadmin.mapper.MonitorWidgetConfigItemMapper;
import com.example.dataadmin.mapper.ProcessedTelemetryFilterSelectionMapper;
import com.example.dataadmin.mapper.DeviceSatelliteMapper;
import com.example.dataadmin.service.DataVisualizationService;
import com.example.dataadmin.service.TelemetryFilterService;
import com.example.dataadmin.dto.processing.TelemetryFilterSelectionRequest;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.common.response.PageResult;
import com.example.dataadmin.vo.visualization.FepFileListItemVO;
import com.example.dataadmin.storage.MinioFileUrlSigner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据可视化页面业务实现。
 *
 * 查询默认使用只读事务，新增、修改和删除操作单独开启写事务。
 */
@Service
@Transactional(readOnly = true)
public class DataVisualizationServiceImpl implements DataVisualizationService {

    /** MonitorDashboardWidget数据访问组件。 */
    private final MonitorDashboardWidgetMapper widgetMapper;
    /** MonitorWidgetConfigItem数据访问组件。 */
    private final MonitorWidgetConfigItemMapper itemMapper;
    /** 遥测参数勾选由统一场景表保存。 */
    private final TelemetryFilterService selectionService;
    private final ProcessedTelemetryFilterSelectionMapper selectionMapper;
    /** 文件分页查询只读取已上传至对象存储的接收记录。 */
    private final FepFileListMapper fileListMapper;
    /** 页面写入与整页配置替换共同锁定所属任务，避免并发写入残留组件。 */
    private final DeviceSatelliteMapper deviceMapper;
    /** 查询授权通过后才为文件生成临时读取地址。 */
    private final MinioFileUrlSigner fileUrlSigner;

    public DataVisualizationServiceImpl(
            MonitorDashboardWidgetMapper widgetMapper,
            MonitorWidgetConfigItemMapper itemMapper,
            TelemetryFilterService selectionService,
            ProcessedTelemetryFilterSelectionMapper selectionMapper,
            FepFileListMapper fileListMapper,
            DeviceSatelliteMapper deviceMapper,
            MinioFileUrlSigner fileUrlSigner) {
        this.widgetMapper = widgetMapper;
        this.itemMapper = itemMapper;
        this.selectionService = selectionService;
        this.selectionMapper = selectionMapper;
        this.fileListMapper = fileListMapper;
        this.deviceMapper = deviceMapper;
        this.fileUrlSigner = fileUrlSigner;
    }

    /** 查询当前用户在指定试验任务下的全部页面组件。 */
    @Override
    public List<MonitorDashboardWidget> listWidgets(String taskId, Long userId) {
        return widgetMapper.findAllByTaskAndUser(taskId, userId);
    }

    /** 查询页面组件详情。 */
    @Override
    public MonitorDashboardWidget getWidget(Long id) {
        return widgetMapper.findById(id);
    }

    /** 新增页面组件。 */
    @Override
    @Transactional
    public Long createWidget(MonitorDashboardWidget widget) {
        // 组件类型必须来自统一枚举，任务编号不能为空。
        requireWidgetConfig(widget);
        // 锁定同一任务后再新增，与整页替换保持串行。
        lockTask(widget.getTaskId());
        widgetMapper.insert(widget);
        // 新组件的遥测参数由按需筛选树勾选，不复制任务下全部解析配置。
        return widget.getId();
    }

    /** 更新页面组件的位置、大小和基本信息。 */
    @Override
    @Transactional
    public boolean updateWidget(MonitorDashboardWidget widget) {
        // 加锁后重新读取组件，防止等待期间旧组件已经被整页导入删除。
        lockWidget(widget.getId());
        // 修改布局时保持原任务和组件类型，避免已保存勾选失去归属。
        MonitorDashboardWidget existing = widgetMapper.findById(widget.getId());
        if (existing == null) { return false; }
        if (widget.getTaskId() != null && !widget.getTaskId().equals(existing.getTaskId())) {
            throw new IllegalArgumentException("组件所属试验任务不能修改");
        }
        if (widget.getWidgetType() != null
                && !widget.getWidgetType().equals(existing.getWidgetType())) {
            throw new IllegalArgumentException("组件类型不能修改");
        }
        return widgetMapper.update(widget) > 0;
    }

    /** 删除页面组件及其数据项配置。 */
    @Override
    @Transactional
    public boolean deleteWidget(Long id) {
        // 组件与参数勾选的清理使用同一任务锁。
        lockWidget(id);
        // 先清理组件下的数据项，避免残留无法归属的配置记录。
        MonitorDashboardWidget widget = widgetMapper.findById(id);
        if (widget == null) { return false; }
        selectionMapper.deleteByTarget(widget.getTaskId(), id);
        itemMapper.deleteByWidgetId(id);
        return widgetMapper.delete(id) > 0;
    }

    /** 查询页面组件的数据项配置。 */
    @Override
    public List<MonitorWidgetConfigItem> listWidgetItems(Long widgetId) {
        return itemMapper.findAllByWidgetId(widgetId);
    }

    /** 查询组件数据项配置详情。 */
    @Override
    public MonitorWidgetConfigItem getWidgetItem(Long id) {
        return itemMapper.findById(id);
    }

    /** 新增组件数据项配置。 */
    @Override
    @Transactional
    public Long createWidgetItem(MonitorWidgetConfigItem item) {
        // 在任务锁内确认组件仍存在，防止写入旧组件主键。
        lockWidget(item.getWidgetId());
        // 未明确勾选的遥测参数默认保持未选中，避免重新运行迁移脚本时误勾选。
        if (item.getParseRuleId() != null && item.getIsSelected() == null) {
            item.setIsSelected(0);
        }
        itemMapper.insert(item);
        // 新增遥测配置项时同步用户明确提交的勾选状态。
        if (item.getParseRuleId() != null && item.getIsSelected() != null) {
            saveParameterSelection(item, item.getIsSelected() == 1);
        }
        return item.getId();
    }

    /** 更新组件数据项配置。 */
    @Override
    @Transactional
    public boolean updateWidgetItem(MonitorWidgetConfigItem item) {
        MonitorWidgetConfigItem before = itemMapper.findById(item.getId());
        if (before == null) { return false; }
        // 等待整页替换结束后重新读取数据项，避免恢复已删除的数据项勾选。
        lockWidget(before.getWidgetId());
        before = itemMapper.findById(item.getId());
        if (before == null) { return false; }
        // 参数关联变化时清理原参数勾选，再保存新参数的勾选状态。
        boolean changedRule = item.getParseRuleId() != null
                && !item.getParseRuleId().equals(before.getParseRuleId());
        if (changedRule && before.getParseRuleId() != null
                && Integer.valueOf(1).equals(before.getIsSelected())) {
            saveParameterSelection(before, false);
        }
        boolean updated = itemMapper.update(item) > 0;
        if (updated && (item.getIsSelected() != null || changedRule)) {
            MonitorWidgetConfigItem effective = itemMapper.findById(item.getId());
            if (effective.getParseRuleId() != null) {
                boolean checked = item.getIsSelected() == null
                        ? Integer.valueOf(1).equals(before.getIsSelected())
                        : item.getIsSelected() == 1;
                saveParameterSelection(effective, checked);
            }
        }
        return updated;
    }

    /** 删除组件数据项配置。 */
    @Override
    @Transactional
    public boolean deleteWidgetItem(Long id) {
        MonitorWidgetConfigItem item = itemMapper.findById(id);
        if (item == null) { return false; }
        // 数据项删除与整页替换按同一任务串行执行。
        lockWidget(item.getWidgetId());
        item = itemMapper.findById(id);
        if (item == null) { return false; }
        // 遥测参数项删除时取消对应组件的勾选。
        if (item.getParseRuleId() != null) {
            saveParameterSelection(item, false);
        }
        return itemMapper.delete(id) > 0;
    }

    /** 更新组件数据项的勾选状态。 */
    @Override
    @Transactional
    public boolean updateItemSelected(Long id, Integer isSelected) {
        MonitorWidgetConfigItem item = itemMapper.findById(id);
        if (item == null) { return false; }
        // 勾选只能写入加锁后仍存在的组件数据项。
        lockWidget(item.getWidgetId());
        item = itemMapper.findById(id);
        if (item == null) { return false; }
        // 只有非遥测参数项继续使用组件项自身的勾选列。
        if (item.getParseRuleId() == null) {
            return itemMapper.updateSelected(id, isSelected) > 0;
        }
        saveParameterSelection(item, isSelected == 1);
        return true;
    }

    /** 将可视化组件参数项的勾选写入统一场景表。 */
    private void saveParameterSelection(MonitorWidgetConfigItem item, boolean checked) {
        MonitorDashboardWidget widget = widgetMapper.findById(item.getWidgetId());
        if (widget == null || widget.getWidgetType() == null
                || (widget.getWidgetType() != 1 && widget.getWidgetType() != 2)) {
            throw new IllegalArgumentException("可视化组件不支持遥测参数勾选");
        }
        TelemetryFilterSelectionRequest request = new TelemetryFilterSelectionRequest();
        request.setTaskId(widget.getTaskId());
        request.setParseRuleId(item.getParseRuleId());
        request.setChecked(checked);
        request.setSelectionType(widget.getWidgetType() == 1
                ? BusinessEnums.SelectionType.VISUALIZATION_CURVE.getCode()
                : BusinessEnums.SelectionType.VISUALIZATION_TABLE.getCode());
        request.setTargetId(widget.getId());
        selectionService.update(request);
    }

    /** 新增组件时校验任务和已有枚举类型。 */
    private void requireWidgetConfig(MonitorDashboardWidget widget) {
        if (widget.getTaskId() == null || widget.getTaskId().trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
        for (BusinessEnums.WidgetType type : BusinessEnums.WidgetType.values()) {
            if (type.getValue().equals(widget.getWidgetType())) { return; }
        }
        throw new IllegalArgumentException("组件类型无效");
    }

    /** 页面写入复用任务行锁，与参数配置维护及配置整页替换保持一致。 */
    private void lockTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty() || deviceMapper.lockForReplacement(taskId) == null) {
            throw new IllegalArgumentException("组件所属试验任务不存在");
        }
    }

    /** 等待任务锁后重新确认组件存在，避免并发导入后出现悬空关联。 */
    private void lockWidget(Long widgetId) {
        MonitorDashboardWidget widget = widgetMapper.findById(widgetId);
        if (widget == null) {
            throw new IllegalArgumentException("可视化组件不存在");
        }
        // 任务锁查询会清理本地查询缓存，加锁后的确认读取最新组件记录。
        lockTask(widget.getTaskId());
        if (widgetMapper.findById(widgetId) == null) {
            throw new IllegalArgumentException("可视化组件已被替换，请刷新页面后重试");
        }
    }

    /** 按图片或文件组件类型查询，避免前端绕过组件类型筛选其他文件。 */
    @Override
    public PageResult<FepFileListItemVO> pageFiles(String taskId, Long widgetId,
            Long userId, Integer pageNum, Integer pageSize) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
        MonitorDashboardWidget widget = widgetMapper.findById(widgetId);
        if (widget == null || !taskId.trim().equals(widget.getTaskId())
                || !userId.equals(widget.getUserId())
                || widget.getWidgetType() == null
                || (widget.getWidgetType() != 4 && widget.getWidgetType() != 5)) {
            throw new IllegalArgumentException("当前任务下不存在图片或文件组件");
        }
        if (pageNum == null || pageNum < 1 || pageSize == null
                || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        long offset = (long) (pageNum - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页范围过大");
        }
        boolean image = widget.getWidgetType() == 4;
        long total = fileListMapper.count(taskId.trim(), image);
        List<FepFileListItemVO> records = total == 0
                ? java.util.Collections.emptyList()
                : fileListMapper.findPage(taskId.trim(), image, (int) offset, pageSize);
        // 使用保存的对象名称重新签名，兼容历史记录中保存的本机地址且不修改数据库。
        for (FepFileListItemVO record : records) {
            record.setFileUrl(fileUrlSigner.createUrl(record.getObjectName()));
        }
        return new PageResult<>(pageNum, pageSize, total, records);
    }

}
