package com.example.dataadmin.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.dataadmin.dto.processing.ProcessingRuleConfigRequest;
import com.example.dataadmin.dto.processing.TelemetryParseRuleBatchUpdateRequest;
import com.example.dataadmin.entity.TelemetryParseRuleConfig;
import com.example.dataadmin.entity.ProcessingRuleConfig;
import com.example.dataadmin.service.DataProcessingService;
import com.example.dataadmin.vo.processing.DataProcessingOverviewVO;
import com.example.dataadmin.vo.processing.DataProcessingRealtimeVO;
import com.example.dataadmin.vo.processing.CollectInterfaceOptionVO;
import com.example.dataadmin.vo.processing.RealtimeTelemetryFrameVO;
import com.example.dataadmin.vo.processing.ProcessedTelemetryVO;
import com.example.dataadmin.vo.processing.ProcessedTelemetryCurveVO;
import com.example.dataadmin.entity.InvalidTelemetryFrame;
import com.example.dataadmin.enums.ControllerEnumData;
import com.example.dataadmin.vo.EnumOptionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 数据处理任务和遥测解析规则接口。
 */
@RestController
@RequestMapping("/processing")
public class DataProcessingController {

    private final DataProcessingService processingService;

    public DataProcessingController(DataProcessingService processingService) {
        this.processingService = processingService;
    }

    /** 查询数据处理页面涉及的全部枚举选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, List<EnumOptionVO>>> findData() {
        return ApiResponse.success(ControllerEnumData.processing());
    }

    /** 查询数据处理页面筛选框使用的已启用采集接口。 */
    @GetMapping("/collect-interfaces")
    public ApiResponse<List<CollectInterfaceOptionVO>> listCollectInterfaces() {
        return ApiResponse.success(
                processingService.listEnabledCollectInterfaces());
    }

    /** 按试验任务查询今日处理量和历史累计去重数量。 */
    @GetMapping("/overview")
    public ApiResponse<DataProcessingOverviewVO> getOverview(
            @RequestParam String taskId) {
        // 处理总览必须限定试验任务，避免跨任务累计处理指标。
        return ApiResponse.success(processingService.getOverview(taskId));
    }

    /** 按试验任务查询所有接口处理速率之和及与系统告警同口径的机器资源指标。 */
    @GetMapping("/realtime")
    public ApiResponse<DataProcessingRealtimeVO> getRealtime(
            @RequestParam String taskId) {
        return ApiResponse.success(processingService.getRealtime(taskId));
    }

    /** 按任务分页查询IoTDB中最新的遥测帧。 */
    @GetMapping("/realtime/telemetry")
    public ApiResponse<PageResult<RealtimeTelemetryFrameVO>> pageRealtimeTelemetry(
            @RequestParam String taskId,
            @RequestParam(required = false) Long deviceSatelliteId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        // 查询参数统一交给业务层校验并转换为IoTDB分页范围。
        return ApiResponse.success(
                processingService.pageRealtimeTelemetryFrames(
                        taskId, deviceSatelliteId, pageNum, pageSize));
    }

    /** 查询当前任务全部已勾选参数的最新处理结果。 */
    @GetMapping("/processed")
    public ApiResponse<List<ProcessedTelemetryVO>> listProcessedTelemetry(
            @RequestParam String taskId,
            @RequestParam(defaultValue = "1") Integer selectionType,
            @RequestParam(defaultValue = "0") Long targetId) {
        // 按表格场景取得已勾选参数；可视化表格传自己的组件主键。
        return ApiResponse.success(processingService.listProcessedTelemetry(
                taskId, selectionType, targetId));
    }

    /** 查询当前任务全部已勾选参数的最近曲线点。 */
    @GetMapping("/processed/curve")
    public ApiResponse<List<ProcessedTelemetryCurveVO>> listProcessedTelemetryCurves(
            @RequestParam String taskId,
            @RequestParam(defaultValue = "2") Integer selectionType,
            @RequestParam(defaultValue = "0") Long targetId) {
        // 每条曲线按自己的组件勾选查询，并保留实际采样时间轴。
        return ApiResponse.success(processingService.listProcessedTelemetryCurves(
                taskId, selectionType, targetId));
    }

    /** 按任务和页面条件分页查询IoTDB中帧检查异常的遥测原始帧。 */
    @GetMapping("/realtime/invalid")
    public ApiResponse<PageResult<InvalidTelemetryFrame>> pageInvalidTelemetry(
            @RequestParam String taskId,
            @RequestParam(required = false) Long deviceSatelliteId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        // 查询参数统一交给业务层校验并转换为IoTDB分页范围。
        return ApiResponse.success(
                processingService.pageInvalidTelemetryFrames(
                        taskId,
                        deviceSatelliteId,
                        pageNum,
                        pageSize));
    }

    /** 查询任务级参数值范围检查开关。 */
    @GetMapping("/rule-config")
    public ApiResponse<ProcessingRuleConfig> getRuleConfig(
            @RequestParam String taskId) {
        return ApiResponse.success(
                processingService.getProcessingRuleConfig(taskId));
    }

    /** 保存任务级参数值范围检查开关。 */
    @PostMapping("/rule-config/update")
    public ApiResponse<ProcessingRuleConfig> updateRuleConfig(
            @Valid @RequestBody ProcessingRuleConfigRequest request) {
        ProcessingRuleConfig config = new ProcessingRuleConfig();
        BeanUtils.copyProperties(request, config);
        return ApiResponse.success(
                processingService.saveProcessingRuleConfig(config));
    }

    /** 查询参数筛选用设备卫星，仅返回编码字段。 */
    @GetMapping("/device-satellites")
    public ApiResponse<List<com.example.dataadmin.vo.processing.DeviceSatelliteOptionVO>> listDeviceSatellites(
            @RequestParam String taskId,
            @RequestParam(required = false) String type) {
        return ApiResponse.success(processingService.listDeviceSatellites(taskId, type));
    }

    /** 查询遥测解析规则。 */
    @GetMapping("/rules")
    public ApiResponse<PageResult<TelemetryParseRuleConfig>> listRules(
            @RequestParam String taskId,
            @RequestParam Long deviceSatelliteId,
            @RequestParam(required = false) Long systemId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(processingService.pageParseRules(
                taskId, deviceSatelliteId, systemId, pageNum, pageSize));
    }

    /** 在当前任务下原子修改多条遥测参数解析配置。 */
    @PostMapping("/rules/batch-update")
    public ApiResponse<Integer> batchUpdateRules(
            @Valid @RequestBody TelemetryParseRuleBatchUpdateRequest request) {
        return ApiResponse.success(processingService.batchUpdateParseRules(request));
    }

    /** 从Excel页签或制表符TXT导入设备卫星及参数，按当前任务和类型替换。 */
    @PostMapping(value = "/rules/import", consumes = "multipart/form-data")
    public ApiResponse<Integer> importRules(
            @RequestParam String taskId,
            @RequestParam String type,
            @RequestParam("file") MultipartFile file) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的Excel或TXT文件");
        }
        try {
            return ApiResponse.success(
                    processingService.importParseRules(
                            taskId,
                            type,
                            file.getOriginalFilename(),
                            file.getBytes()));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("读取导入文件失败", ex);
        }
    }

}
