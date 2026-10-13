package com.example.dataadmin.service;

import com.example.dataadmin.dto.visualization.VisualizationConfigFile;
import com.example.dataadmin.dto.visualization.VisualizationConfigFile.Item;
import com.example.dataadmin.dto.visualization.VisualizationConfigFile.Parameter;
import com.example.dataadmin.dto.visualization.VisualizationConfigFile.Widget;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.CollectProtocolConfig;
import com.example.dataadmin.entity.MonitorDashboardWidget;
import com.example.dataadmin.entity.MonitorWidgetConfigItem;
import com.example.dataadmin.entity.VisualizationItemRecord;
import com.example.dataadmin.entity.VisualizationParameterRecord;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.mapper.DeviceSatelliteMapper;
import com.example.dataadmin.mapper.MonitorDashboardWidgetMapper;
import com.example.dataadmin.mapper.VisualizationConfigMapper;
import com.example.dataadmin.vo.visualization.VisualizationConfigPreviewVO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** 可视化页面配置的完整导出、只读检查和事务替换。 */
@Service
@Transactional(readOnly = true)
public class VisualizationConfigService {
    /** 限制配置文件体积，上传前后都执行检查。 */
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    /** 防止异常文件一次创建过多组件。 */
    private static final int MAX_WIDGETS = 500;
    /** 限制整页参数勾选和数据项的合计数量。 */
    private static final int MAX_ENTRIES = 50000;
    /** 分批匹配，控制数据库条件数量。 */
    private static final int MATCH_BATCH_SIZE = 500;
    /** 限制检查问题数量，避免错误响应过大。 */
    private static final int MAX_ERRORS = 100;

    /** 复用组件业务操作及原有清理逻辑。 */
    private final DataVisualizationService visualizationService;
    /** 读取当前用户页面，导入时插入新组件并获得新主键。 */
    private final MonitorDashboardWidgetMapper widgetMapper;
    /** 文件专用批量业务标识查询。 */
    private final VisualizationConfigMapper configMapper;
    /** 复用任务行锁，与参数全量导入、批量编辑串行。 */
    private final DeviceSatelliteMapper deviceMapper;
    /** 使用独立副本严格读取配置文件，不改变应用其他接口的配置。 */
    private final ObjectMapper fileMapper;

    /** 注入已有访问组件并设置配置文件专用校验规则。 */
    public VisualizationConfigService(DataVisualizationService visualizationService,
            MonitorDashboardWidgetMapper widgetMapper, VisualizationConfigMapper configMapper,
            DeviceSatelliteMapper deviceMapper, ObjectMapper objectMapper) {
        // 第一步：复用已有服务和映射组件，不建立重复的数据访问流程。
        this.visualizationService = visualizationService;
        this.widgetMapper = widgetMapper;
        this.configMapper = configMapper;
        this.deviceMapper = deviceMapper;
        // 第二步：拒绝未知字段、重复字段、尾随内容和类型隐式转换。
        this.fileMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    /** 同一数据库快照中读取布局和筛选，防止导出混合前后两版配置。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] exportConfig(String taskId, Long userId) {
        // 第一步：确认用户及任务范围，读取全部组件并建立新文件。
        String scope = requireScope(taskId, userId);
        requireTaskExists(scope);
        VisualizationConfigFile file = new VisualizationConfigFile();
        file.setSchemaVersion(1);
        file.setExportedAt(OffsetDateTime.now().toString());
        file.setWidgets(new ArrayList<>());
        Map<Long, Widget> widgets = new LinkedHashMap<>();
        for (MonitorDashboardWidget existing : widgetMapper.findAllByTaskAndUser(scope, userId)) {
            Widget widget = new Widget();
            BeanUtils.copyProperties(existing, widget);
            widgets.put(existing.getId(), widget);
            file.getWidgets().add(widget);
        }
        // 第二步：直接从场景勾选表读取参数，包含没有数据项记录的筛选树勾选。
        Map<List<Long>, Parameter> selected = new LinkedHashMap<>();
        for (VisualizationParameterRecord record : configMapper.findSelectedParameters(scope, userId)) {
            if (record.getParseRuleId() == null || record.getDeviceSatelliteId() == null) {
                throw new IllegalArgumentException("页面存在已失效的勾选参数，请重新保存筛选后导出");
            }
            Parameter parameter = new Parameter();
            BeanUtils.copyProperties(record, parameter);
            widgets.get(record.getWidgetId()).getSelectedParameters().add(parameter);
            selected.put(Arrays.asList(record.getWidgetId(), record.getParseRuleId()), parameter);
        }
        // 第三步：仅附带已勾选参数的数据项；非遥测项保留原勾选状态。
        for (VisualizationItemRecord record : configMapper.findItems(scope, userId)) {
            Parameter parameter = selected.get(Arrays.asList(record.getWidgetId(), record.getParseRuleId()));
            if (record.getParseRuleId() != null && parameter == null) {
                continue;
            }
            if ((record.getInterfaceId() != null && record.getInterfaceName() == null)
                    || (record.getProtocolConfigId() != null && record.getProtocolConfigName() == null)) {
                throw new IllegalArgumentException("页面数据项存在失效或跨任务的接口、协议关联，请修正后导出");
            }
            Item item = new Item();
            BeanUtils.copyProperties(record, item);
            if (parameter == null) {
                widgets.get(record.getWidgetId()).getItems().add(item);
            } else {
                item.setSelected(true);
                parameter.getDataItems().add(item);
            }
        }
        // 第四步：导出文件必须满足同一套结构约束，保证可以原样重新导入。
        VisualizationConfigPreviewVO validation = new VisualizationConfigPreviewVO();
        validateStructure(file, validation);
        requireValid(validation);
        try {
            byte[] bytes = fileMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(file);
            if (bytes.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("页面配置超过十兆字节，请减少组件或筛选项后导出");
            }
            // 检查源任务内的关联名称唯一，保证导出的文件可以在源任务原样恢复。
            requireValid(prepare(scope, userId, "可视化配置.json", bytes, false).preview);
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("生成可视化配置文件失败", exception);
        }
    }

    /** 文件问题作为中文检查列表返回，本方法不加写锁、不写入数据。 */
    public VisualizationConfigPreviewVO preview(String taskId, Long userId, String filename, byte[] bytes) {
        // 在当前用户目标任务中进行完整匹配，检查结果不作为后续写入授权凭据。
        String scope = requireScope(taskId, userId);
        requireTaskExists(scope);
        return prepare(scope, userId, filename, bytes, false).preview;
    }

    /** 真正导入时重新检查，并在同一事务内清理旧页面和创建新页面。 */
    @Transactional(rollbackFor = Exception.class)
    public VisualizationConfigPreviewVO importConfig(String taskId, Long userId, String filename, byte[] bytes) {
        // 第一步：锁定目标任务，与参数配置替换及本页配置写入串行执行。
        String scope = requireScope(taskId, userId);
        if (deviceMapper.lockForReplacement(scope) == null) {
            throw new IllegalArgumentException("目标试验任务不存在");
        }
        // 第二步：重新校验同一文件和最新参数关联，全部成功后才允许清理原页面。
        Prepared prepared = prepare(scope, userId, filename, bytes, true);
        requireValid(prepared.preview);
        for (MonitorDashboardWidget existing : widgetMapper.findAllByTaskAndUser(scope, userId)) {
            if (!visualizationService.deleteWidget(existing.getId())) {
                throw new IllegalArgumentException("清理原可视化组件失败，导入已回滚");
            }
        }
        // 第三步：重新生成组件主键，保留原组件标识、标题、显示状态和网格布局。
        for (Widget source : prepared.file.getWidgets()) {
            MonitorDashboardWidget widget = new MonitorDashboardWidget();
            BeanUtils.copyProperties(source, widget);
            widget.setTaskId(scope);
            widget.setUserId(userId);
            if (widgetMapper.insert(widget) != 1 || widget.getId() == null) {
                throw new IllegalArgumentException("创建导入组件失败，导入已回滚");
            }
            // 第四步：非遥测数据项恢复各自勾选状态及重新匹配的关联主键。
            List<MonitorWidgetConfigItem> items = new ArrayList<>();
            for (Item sourceItem : source.getItems()) {
                items.add(toItem(widget.getId(), null, sourceItem, prepared));
            }
            // 第五步：遥测参数写入新组件自己的场景，同一参数在不同组件互不影响。
            int selectionType = selectionType(source.getWidgetType());
            List<VisualizationParameterRecord> parameters = new ArrayList<>();
            for (Parameter parameter : source.getSelectedParameters()) {
                VisualizationParameterRecord target = prepared.parameters.get(parameterKey(parameter));
                parameters.add(target);
                for (Item sourceItem : parameter.getDataItems()) {
                    items.add(toItem(widget.getId(), target.getParseRuleId(), sourceItem, prepared));
                }
            }
            // 每批恢复实际保存的勾选和数据项，避免大页面逐条访问数据库。
            for (int start = 0; start < parameters.size(); start += MATCH_BATCH_SIZE) {
                List<VisualizationParameterRecord> batch = parameters.subList(start,
                        Math.min(start + MATCH_BATCH_SIZE, parameters.size()));
                if (configMapper.insertSelections(scope, widget.getId(), selectionType, batch) != batch.size()) {
                    throw new IllegalArgumentException("恢复组件参数勾选失败，导入已回滚");
                }
            }
            for (int start = 0; start < items.size(); start += MATCH_BATCH_SIZE) {
                List<MonitorWidgetConfigItem> batch = items.subList(start, Math.min(start + MATCH_BATCH_SIZE, items.size()));
                if (configMapper.insertItems(batch) != batch.size()) {
                    throw new IllegalArgumentException("恢复组件数据项失败，导入已回滚");
                }
            }
        }
        // 第六步：成功返回恢复数量，任何异常都会回滚此前的删除和新增。
        return prepared.preview;
    }

    /** 先检查文件，再分批匹配参数和数据项关联，过程不执行数据库写入。 */
    private Prepared prepare(String taskId, Long userId, String filename, byte[] bytes, boolean lock) {
        Prepared prepared = new Prepared();
        prepared.preview.setExistingWidgetCount(widgetMapper.findAllByTaskAndUser(taskId, userId).size());
        // 第一步：拒绝空文件、错误扩展名及超过体积限制的文件。
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            error(prepared.preview, "仅支持导入本系统导出的JSON配置文件");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            error(prepared.preview, "配置文件不能为空，且不能超过十兆字节");
        }
        if (!prepared.preview.isValid()) {
            return prepared;
        }
        // 第二步：严格解析文件；原始解析错误不输出，统一返回中文提示。
        try {
            prepared.file = fileMapper.readValue(bytes, VisualizationConfigFile.class);
        } catch (IOException exception) {
            error(prepared.preview, "配置文件格式不正确，存在未知字段、重复字段或字段类型错误");
            return prepared;
        }
        validateStructure(prepared.file, prepared.preview);
        if (!prepared.preview.isValid()) {
            return prepared;
        }
        // 第三步：跨组件相同参数只查询一次，每个组件仍独立恢复勾选。
        Map<List<String>, Parameter> unique = new LinkedHashMap<>();
        for (Widget widget : prepared.file.getWidgets()) {
            for (Parameter parameter : widget.getSelectedParameters()) {
                unique.putIfAbsent(parameterKey(parameter), parameter);
            }
        }
        List<Parameter> parameters = new ArrayList<>(unique.values());
        Map<List<String>, List<VisualizationParameterRecord>> matches = new LinkedHashMap<>();
        for (int start = 0; start < parameters.size(); start += MATCH_BATCH_SIZE) {
            List<Parameter> batch = parameters.subList(start, Math.min(start + MATCH_BATCH_SIZE, parameters.size()));
            for (VisualizationParameterRecord record : configMapper.findParameterMatches(taskId, batch)) {
                matches.computeIfAbsent(parameterKey(record), key -> new ArrayList<>()).add(record);
            }
        }
        for (Map.Entry<List<String>, Parameter> entry : unique.entrySet()) {
            List<VisualizationParameterRecord> candidates = matches.get(entry.getKey());
            if (candidates == null || candidates.size() != 1) {
                Parameter parameter = entry.getValue();
                error(prepared.preview, "参数匹配" + (candidates == null ? "失败" : "不唯一") + "："
                        + parameter.getDeviceTypeName() + "／" + parameter.getDeviceCode()
                        + "／" + parameter.getTelemetryCode());
            } else {
                prepared.parameters.put(entry.getKey(), candidates.get(0));
            }
        }
        // 第四步：仅当文件有相应引用时读取目标任务接口和协议，名称重名时不猜测。
        List<Item> items = allItems(prepared.file);
        Map<String, List<Long>> interfaces = new LinkedHashMap<>();
        Map<String, List<Long>> protocols = new LinkedHashMap<>();
        Set<String> interfaceNames = new HashSet<>();
        Set<String> protocolNames = new HashSet<>();
        for (Item item : items) {
            if (item.getInterfaceName() != null) { interfaceNames.add(item.getInterfaceName()); }
            if (item.getProtocolConfigName() != null) { protocolNames.add(item.getProtocolConfigName()); }
        }
        List<String> interfaceList = new ArrayList<>(interfaceNames);
        List<String> protocolList = new ArrayList<>(protocolNames);
        for (int start = 0; start < interfaceList.size(); start += MATCH_BATCH_SIZE) {
            List<String> batch = interfaceList.subList(start, Math.min(start + MATCH_BATCH_SIZE, interfaceList.size()));
            for (CollectInterfaceConfig entity : configMapper.findInterfaceMatches(taskId, batch, lock)) {
                interfaces.computeIfAbsent(entity.getInterfaceName(), key -> new ArrayList<>()).add(entity.getId());
            }
        }
        for (int start = 0; start < protocolList.size(); start += MATCH_BATCH_SIZE) {
            List<String> batch = protocolList.subList(start, Math.min(start + MATCH_BATCH_SIZE, protocolList.size()));
            for (CollectProtocolConfig entity : configMapper.findProtocolMatches(taskId, batch, lock)) {
                protocols.computeIfAbsent(entity.getConfigName(), key -> new ArrayList<>()).add(entity.getId());
            }
        }
        for (Item item : items) {
            prepared.interfaces.put(item, matchAssociation(item.getInterfaceName(), interfaces, "采集接口", prepared.preview));
            prepared.protocols.put(item, matchAssociation(item.getProtocolConfigName(), protocols, "协议配置", prepared.preview));
        }
        return prepared;
    }

    /** 检查组件布局、枚举和每个组件自己的参数列表，不修改文件中的业务标识。 */
    private void validateStructure(VisualizationConfigFile file, VisualizationConfigPreviewVO preview) {
        // 第一步：格式版本及组件列表必须显式提供，空页面必须明确传空列表。
        if (file == null || !Integer.valueOf(1).equals(file.getSchemaVersion())) {
            error(preview, "配置文件版本不支持，当前仅支持第一版");
            return;
        }
        if (file.getWidgets() == null || file.getWidgets().size() > MAX_WIDGETS) {
            error(preview, "组件列表不能为空值，且最多包含五百个组件");
            return;
        }
        if (file.getExportedAt() != null) {
            try {
                OffsetDateTime.parse(file.getExportedAt());
            } catch (java.time.format.DateTimeParseException exception) {
                error(preview, "导出时间格式不正确，需要包含时区偏移");
            }
        }
        preview.setWidgetCount(file.getWidgets().size());
        Set<String> widgetKeys = new HashSet<>();
        int parameterCount = 0;
        int itemCount = 0;
        for (Widget widget : file.getWidgets()) {
            if (widget == null) {
                error(preview, "组件列表不能包含空组件");
                continue;
            }
            // 第二步：组件标识不能重复，标题、类型、显示状态和网格位置必须合法。
            String prefix = "组件「" + widget.getWidgetKey() + "」：";
            text(widget.getWidgetKey(), 64, true, prefix + "组件标识", preview);
            if (!widgetKeys.add(widget.getWidgetKey())) {
                error(preview, prefix + "组件标识重复");
            }
            text(widget.getWidgetTitle(), 100, false, prefix + "组件标题", preview);
            if (widget.getWidgetTypeName() == null) {
                error(preview, prefix + "组件类型无效");
            }
            if (widget.getEnabledName() == null) {
                error(preview, prefix + "显示状态无效");
            }
            if (widget.getGridX() == null || widget.getGridY() == null || widget.getGridWidth() == null
                    || widget.getGridHeight() == null || widget.getGridX() < 0 || widget.getGridY() < 0
                    || widget.getGridWidth() <= 0 || widget.getGridHeight() <= 0
                    || (long) widget.getGridX() + widget.getGridWidth() > Integer.MAX_VALUE
                    || (long) widget.getGridY() + widget.getGridHeight() > Integer.MAX_VALUE) {
                error(preview, prefix + "网格位置必须非负，宽高必须大于零且不能溢出");
            }
            if (widget.getSelectedParameters() == null || widget.getItems() == null) {
                error(preview, prefix + "参数列表和数据项列表不能为空值");
                continue;
            }
            if (!widget.getSelectedParameters().isEmpty()
                    && !Integer.valueOf(1).equals(widget.getWidgetType())
                    && !Integer.valueOf(2).equals(widget.getWidgetType())) {
                error(preview, prefix + "只有实时曲线和实时数据组件支持遥测参数勾选");
            }
            // 第三步：每个组件内参数唯一，设备和参数通过完整业务标识定位。
            parameterCount += widget.getSelectedParameters().size();
            itemCount += widget.getItems().size();
            Set<List<String>> keys = new HashSet<>();
            for (Parameter parameter : widget.getSelectedParameters()) {
                if (parameter == null) {
                    error(preview, prefix + "参数列表不能包含空参数");
                    continue;
                }
                if (parameter.getDeviceTypeName() == null) {
                    error(preview, prefix + "设备卫星类型无效");
                }
                text(parameter.getDeviceCode(), 100, true, prefix + "设备卫星编码", preview);
                text(parameter.getTelemetryCode(), 100, true, prefix + "遥测代号", preview);
                if (!keys.add(parameterKey(parameter))) {
                    error(preview, prefix + "同一设备下的遥测参数重复：" + parameter.getTelemetryCode());
                }
                if (parameter.getDataItems() == null) {
                    error(preview, prefix + "参数数据项列表不能为空值");
                } else {
                    itemCount += parameter.getDataItems().size();
                    for (Item item : parameter.getDataItems()) {
                        validateItem(item, true, prefix, preview);
                    }
                }
            }
            // 第四步：非遥测数据项可以未勾选，已有接口和协议名称不能为空白文本。
            for (Item item : widget.getItems()) {
                validateItem(item, false, prefix, preview);
            }
            if ((long) parameterCount + itemCount > MAX_ENTRIES) {
                error(preview, "配置文件参数勾选和数据项合计不能超过五万条");
                break;
            }
        }
        preview.setParameterCount(parameterCount);
        preview.setItemCount(itemCount);
    }

    /** 检查数据项字段，已勾选参数下不接受未勾选状态。 */
    private void validateItem(Item item, boolean parameterItem, String prefix, VisualizationConfigPreviewVO preview) {
        if (item == null) {
            error(preview, prefix + "数据项列表不能包含空数据项");
            return;
        }
        // 数据项与现有数据库字段长度保持一致，可选关联允许空值但不允许空白名称。
        text(item.getItemCode(), 100, true, prefix + "数据项编码", preview);
        text(item.getItemName(), 100, true, prefix + "数据项名称", preview);
        text(item.getInterfaceName(), 100, false, prefix + "采集接口名称", preview);
        text(item.getProtocolConfigName(), 100, false, prefix + "协议配置名称", preview);
        if ((item.getInterfaceName() != null && item.getInterfaceName().trim().isEmpty())
                || (item.getProtocolConfigName() != null && item.getProtocolConfigName().trim().isEmpty())) {
            error(preview, prefix + "可选的接口、协议名称只能为空值或有效名称");
        }
        if (item.getSelected() == null || (parameterItem && !Boolean.TRUE.equals(item.getSelected()))) {
            error(preview, prefix + "数据项勾选状态无效，已勾选参数的数据项必须为已勾选");
        }
    }

    /** 同名关联必须恰好有一条有效目标记录，缺失和重名都阻止导入。 */
    private Long matchAssociation(String name, Map<String, List<Long>> candidates, String label,
            VisualizationConfigPreviewVO preview) {
        if (name == null) {
            return null;
        }
        // 不按源数据库主键回退匹配，以免跨任务关联到无关对象。
        List<Long> ids = candidates.get(name);
        if (ids == null || ids.size() != 1) {
            error(preview, label + "匹配" + (ids == null ? "失败" : "不唯一") + "：" + name);
            return null;
        }
        return ids.get(0);
    }

    /** 转换已持久化数据项，实际写入按批进行。 */
    private MonitorWidgetConfigItem toItem(Long widgetId, Long parseRuleId, Item source, Prepared prepared) {
        // 仅从通过校验的匹配结果取目标主键，源文件不允许传入主键。
        MonitorWidgetConfigItem item = new MonitorWidgetConfigItem();
        item.setWidgetId(widgetId);
        item.setParseRuleId(parseRuleId);
        item.setItemCode(source.getItemCode());
        item.setItemName(source.getItemName());
        item.setIsSelected(Boolean.TRUE.equals(source.getSelected()) ? 1 : 0);
        item.setInterfaceId(prepared.interfaces.get(source));
        item.setProtocolConfigId(prepared.protocols.get(source));
        return item;
    }

    /** 汇总已有数据项，参数筛选仍保留在各自组件内。 */
    private List<Item> allItems(VisualizationConfigFile file) {
        List<Item> items = new ArrayList<>();
        // 同时覆盖非遥测数据项和已勾选参数的数据项配置。
        for (Widget widget : file.getWidgets()) {
            items.addAll(widget.getItems());
            for (Parameter parameter : widget.getSelectedParameters()) {
                items.addAll(parameter.getDataItems());
            }
        }
        return items;
    }

    /** 三段业务键使用列表比较，避免分隔符出现在编码中造成碰撞。 */
    private List<String> parameterKey(Parameter parameter) {
        return Arrays.asList(parameter.getDeviceType(), parameter.getDeviceCode(), parameter.getTelemetryCode());
    }

    /** 组件类型与现有勾选场景枚举对应，其他组件不执行参数恢复。 */
    private int selectionType(Integer widgetType) {
        return Integer.valueOf(1).equals(widgetType)
                ? BusinessEnums.SelectionType.VISUALIZATION_CURVE.getCode()
                : BusinessEnums.SelectionType.VISUALIZATION_TABLE.getCode();
    }

    /** 必填文本必须非空白，所有文本按数据库字段字符长度检查。 */
    private void text(String value, int maxLength, boolean required, String label,
            VisualizationConfigPreviewVO preview) {
        // 按字符数而非字节数检查，避免中文字段提前触发长度限制。
        if ((required && (value == null || value.trim().isEmpty()))
                || (value != null && value.codePointCount(0, value.length()) > maxLength)) {
            error(preview, label + "不能为空白且不能超过" + maxLength + "个字符");
        }
    }

    /** 所有公开操作强制限制到有效登录用户及非空任务编号。 */
    private String requireScope(String taskId, Long userId) {
        if (taskId == null || taskId.trim().isEmpty() || taskId.length() > 100) {
            throw new IllegalArgumentException("试验任务编号不能为空，且不能超过一百个字符");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("当前登录用户无效");
        }
        return taskId.trim();
    }

    /** 导出和检查只读确认任务存在，真正导入使用同一任务的行锁。 */
    private void requireTaskExists(String taskId) {
        if (configMapper.findTask(taskId) == null) {
            throw new IllegalArgumentException("目标试验任务不存在");
        }
    }

    /** 将最多一百条中文问题返回给页面。 */
    private void error(VisualizationConfigPreviewVO preview, String message) {
        if (preview.getErrors().size() < MAX_ERRORS) {
            preview.getErrors().add(message);
        }
    }

    /** 写入前只允许无问题配置通过，失败保留原页面。 */
    private void requireValid(VisualizationConfigPreviewVO preview) {
        if (!preview.isValid()) {
            throw new IllegalArgumentException("配置检查未通过：" + String.join("；", preview.getErrors()));
        }
    }

    /** 本次请求内的检查结果及目标主键，检查后不对外暴露、不跨请求缓存。 */
    private static class Prepared {
        /** 已解析文件。 */
        private VisualizationConfigFile file;
        /** 数量统计及中文问题。 */
        private final VisualizationConfigPreviewVO preview = new VisualizationConfigPreviewVO();
        /** 唯一业务标识对应的目标有效参数。 */
        private final Map<List<String>, VisualizationParameterRecord> parameters = new LinkedHashMap<>();
        /** 每个数据项实际匹配的目标接口。 */
        private final Map<Item, Long> interfaces = new IdentityHashMap<>();
        /** 每个数据项实际匹配的目标协议。 */
        private final Map<Item, Long> protocols = new IdentityHashMap<>();
    }
}
