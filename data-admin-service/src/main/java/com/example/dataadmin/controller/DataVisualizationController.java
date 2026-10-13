package com.example.dataadmin.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.dataadmin.vo.visualization.FepFileListItemVO;
import com.example.dataadmin.dto.IdRequest;
import com.example.dataadmin.dto.IdStatusRequest;
import com.example.dataadmin.dto.visualization.WidgetItemSaveRequest;
import com.example.dataadmin.dto.visualization.WidgetSaveRequest;
import com.example.dataadmin.entity.MonitorDashboardWidget;
import com.example.dataadmin.entity.MonitorWidgetConfigItem;
import com.example.dataadmin.enums.ControllerEnumData;
import com.example.dataadmin.service.DataVisualizationService;
import com.example.dataadmin.service.VisualizationConfigService;
import com.example.dataadmin.vo.visualization.VisualizationConfigPreviewVO;
import com.example.dataadmin.vo.EnumOptionVO;
import com.example.security.context.LoginUserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 数据可视化页面配置接口。
 */
@RestController
@RequestMapping("/visualization")
public class DataVisualizationController {

    private final DataVisualizationService visualizationService;
    /** 整页配置导入导出业务组件。 */
    private final VisualizationConfigService configService;

    public DataVisualizationController(
            DataVisualizationService visualizationService, VisualizationConfigService configService) {
        this.visualizationService = visualizationService;
        this.configService = configService;
    }

    /** 下载当前用户指定任务的整页可视化配置文件。 */
    @GetMapping(value = "/config/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportConfig(@RequestParam String taskId) {
        // 第一步：用户范围从登录上下文取得，客户端不能指定其他用户。
        byte[] bytes = configService.exportConfig(taskId, LoginUserContext.getRequired().getUserId());
        // 第二步：以附件形式返回标准配置文件，下载内容不包裹统一响应对象。
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("可视化配置.json", StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    /** 只读检查配置文件及其在目标任务中的参数、接口、协议匹配情况。 */
    @PostMapping(value = "/config/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VisualizationConfigPreviewVO> previewConfig(@RequestParam String taskId,
            @RequestParam("file") MultipartFile file) {
        // 文件检查只返回中文问题和数量，不修改原组件或勾选记录。
        return ApiResponse.success(configService.preview(taskId, LoginUserContext.getRequired().getUserId(),
                file.getOriginalFilename(), readConfigFile(file)));
    }

    /** 上传同一文件，重新检查并事务替换当前用户目标任务的整页配置。 */
    @PostMapping(value = "/config/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VisualizationConfigPreviewVO> importConfig(@RequestParam String taskId,
            @RequestParam("file") MultipartFile file) {
        // 不接受客户端提供用户或主键映射，实际关联由业务服务重新匹配。
        return ApiResponse.success(configService.importConfig(taskId, LoginUserContext.getRequired().getUserId(),
                file.getOriginalFilename(), readConfigFile(file)));
    }

    /** 在读取全部字节前检查体积，避免超大上传占用业务内存。 */
    private byte[] readConfigFile(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > VisualizationConfigService.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("配置文件不能为空，且不能超过十兆字节");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取可视化配置文件失败", exception);
        }
    }

    /** 查询数据可视化页面涉及的全部枚举选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, List<EnumOptionVO>>> findData() {
        return ApiResponse.success(ControllerEnumData.visualization());
    }

    /** 查询当前用户的可视化组件。 */
    @GetMapping("/widgets")
    public ApiResponse<List<MonitorDashboardWidget>> listWidgets(
            @RequestParam String taskId) {
        Long userId = LoginUserContext.getRequired().getUserId();
        return ApiResponse.success(
                visualizationService.listWidgets(taskId, userId));
    }

    /** 查询可视化组件详情。 */
    @GetMapping("/widgets/{id}")
    public ApiResponse<MonitorDashboardWidget> getWidget(
            @PathVariable Long id) {
        return ApiResponse.success(getOwnedWidget(id));
    }

    /** 新增当前用户的可视化组件。 */
    @PostMapping("/widgets/create")
    public ApiResponse<Long> createWidget(
            @RequestBody WidgetSaveRequest request) {
        MonitorDashboardWidget widget = toWidget(request);
        widget.setUserId(LoginUserContext.getRequired().getUserId());
        return ApiResponse.success(visualizationService.createWidget(widget));
    }

    /** 修改可视化组件布局和基本信息。 */
    @PostMapping("/widgets/update")
    public ApiResponse<Void> updateWidget(
            @RequestBody WidgetSaveRequest request) {
        requireId(request.getId());
        getOwnedWidget(request.getId());
        MonitorDashboardWidget widget = toWidget(request);
        widget.setUserId(LoginUserContext.getRequired().getUserId());
        requireSuccess(
                visualizationService.updateWidget(widget),
                "可视化组件修改失败");
        return ApiResponse.success();
    }

    /** 删除可视化组件及其数据项。 */
    @PostMapping("/widgets/delete")
    public ApiResponse<Void> deleteWidget(
            @Valid @RequestBody IdRequest request) {
        getOwnedWidget(request.getId());
        requireSuccess(
                visualizationService.deleteWidget(request.getId()),
                "可视化组件删除失败");
        return ApiResponse.success();
    }

    /** 查询组件下的数据项配置。 */
    @GetMapping("/widgets/{widgetId}/items")
    public ApiResponse<List<MonitorWidgetConfigItem>> listWidgetItems(
            @PathVariable Long widgetId) {
        getOwnedWidget(widgetId);
        return ApiResponse.success(
                visualizationService.listWidgetItems(widgetId));
    }

    /** 按图片或文件组件类型分页查询已上传文件。 */
    @GetMapping("/widgets/{widgetId}/files")
    public ApiResponse<PageResult<FepFileListItemVO>> pageFiles(
            @PathVariable Long widgetId,
            @RequestParam String taskId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        // 服务层同时校验任务、当前用户和组件类型。
        return ApiResponse.success(visualizationService.pageFiles(taskId, widgetId,
                LoginUserContext.getRequired().getUserId(), pageNum, pageSize));
    }

    /** 查询组件数据项详情。 */
    @GetMapping("/items/{id}")
    public ApiResponse<MonitorWidgetConfigItem> getWidgetItem(
            @PathVariable Long id) {
        return ApiResponse.success(getOwnedWidgetItem(id));
    }

    /** 新增组件数据项。 */
    @PostMapping("/items/create")
    public ApiResponse<Long> createWidgetItem(
            @RequestBody WidgetItemSaveRequest request) {
        requireId(request.getWidgetId());
        getOwnedWidget(request.getWidgetId());
        MonitorWidgetConfigItem item = toWidgetItem(request);
        return ApiResponse.success(
                visualizationService.createWidgetItem(item));
    }

    /** 修改组件数据项。 */
    @PostMapping("/items/update")
    public ApiResponse<Void> updateWidgetItem(
            @RequestBody WidgetItemSaveRequest request) {
        requireId(request.getId());
        MonitorWidgetConfigItem existingItem =
                getOwnedWidgetItem(request.getId());
        MonitorWidgetConfigItem item = toWidgetItem(request);
        item.setWidgetId(existingItem.getWidgetId());
        requireSuccess(
                visualizationService.updateWidgetItem(item),
                "组件数据项修改失败");
        return ApiResponse.success();
    }

    /** 删除组件数据项。 */
    @PostMapping("/items/delete")
    public ApiResponse<Void> deleteWidgetItem(
            @Valid @RequestBody IdRequest request) {
        getOwnedWidgetItem(request.getId());
        requireSuccess(
                visualizationService.deleteWidgetItem(request.getId()),
                "组件数据项删除失败");
        return ApiResponse.success();
    }

    /** 修改组件数据项的勾选状态。 */
    @PostMapping("/items/selected")
    public ApiResponse<Void> updateItemSelected(
            @Valid @RequestBody IdStatusRequest request) {
        getOwnedWidgetItem(request.getId());
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new IllegalArgumentException("勾选状态只能为0或1");
        }
        requireSuccess(
                visualizationService.updateItemSelected(
                        request.getId(),
                        request.getStatus()),
                "组件数据项勾选状态修改失败");
        return ApiResponse.success();
    }

    /** 将组件请求 DTO 转换为数据库实体。 */
    private MonitorDashboardWidget toWidget(WidgetSaveRequest request) {
        MonitorDashboardWidget widget = new MonitorDashboardWidget();
        BeanUtils.copyProperties(request, widget);
        return widget;
    }

    /** 将组件数据项请求 DTO 转换为数据库实体。 */
    private MonitorWidgetConfigItem toWidgetItem(
            WidgetItemSaveRequest request) {
        MonitorWidgetConfigItem item = new MonitorWidgetConfigItem();
        BeanUtils.copyProperties(request, item);
        return item;
    }

    /** 校验写操作请求中的业务主键。 */
    private void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("业务主键不能为空");
        }
    }

    /**
     * 查询组件并校验其是否属于当前登录用户。
     */
    private MonitorDashboardWidget getOwnedWidget(Long id) {
        MonitorDashboardWidget widget = visualizationService.getWidget(id);
        Long currentUserId = LoginUserContext.getRequired().getUserId();
        if (widget == null || !currentUserId.equals(widget.getUserId())) {
            throw new IllegalArgumentException("可视化组件不存在");
        }
        return widget;
    }

    /**
     * 查询组件数据项并校验所属组件的用户归属。
     */
    private MonitorWidgetConfigItem getOwnedWidgetItem(Long id) {
        MonitorWidgetConfigItem item = visualizationService.getWidgetItem(id);
        if (item == null) {
            throw new IllegalArgumentException("组件数据项不存在");
        }
        getOwnedWidget(item.getWidgetId());
        return item;
    }

    /** 将实现层执行结果转换为统一业务异常。 */
    private void requireSuccess(boolean success, String message) {
        if (!success) {
            throw new IllegalArgumentException(message);
        }
    }
}
