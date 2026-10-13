package com.example.dataadmin.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.dataadmin.dto.IdRequest;
import com.example.dataadmin.dto.calibration.CalibrationChannelEnabledRequest;
import com.example.dataadmin.dto.calibration.ChannelSaveRequest;
import com.example.dataadmin.entity.CalibChannelConfig;
import com.example.dataadmin.entity.CalibRecord;
import com.example.dataadmin.enums.ControllerEnumData;
import com.example.dataadmin.service.DataCalibrationService;
import com.example.dataadmin.vo.EnumOptionVO;
import com.example.dataadmin.vo.calibration.CalibRecordDetailVO;
import com.example.dataadmin.vo.calibration.CalibrationOverviewVO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 数据校准通道配置和校准记录接口。
 */
@RestController
@RequestMapping("/calibration")
public class DataCalibrationController {

    private final DataCalibrationService calibrationService;

    public DataCalibrationController(
            DataCalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    /** 查询数据校准页面涉及的全部枚举选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, List<EnumOptionVO>>> findData() {
        return ApiResponse.success(ControllerEnumData.calibration());
    }

    /** 查询当前试验任务累计检出的野值数量。 */
    @GetMapping("/overview")
    public ApiResponse<CalibrationOverviewVO> getOverview(
            @RequestParam String taskId) {
        // 校准总览必须限定试验任务，避免跨任务累计野值数量。
        return ApiResponse.success(calibrationService.getOverview(taskId));
    }

    /** 查询校准通道配置。 */
    @GetMapping("/channels")
    public ApiResponse<List<CalibChannelConfig>> listChannels(
            @RequestParam String taskId) {
        return ApiResponse.success(
                calibrationService.listChannelConfigs(taskId));
    }

    /** 查询校准通道详情。 */
    @GetMapping("/channels/{id}")
    public ApiResponse<CalibChannelConfig> getChannel(@PathVariable Long id) {
        return ApiResponse.success(calibrationService.getChannelConfig(id));
    }

    /** 新增校准通道配置。 */
    @PostMapping("/channels/create")
    public ApiResponse<Long> createChannel(
            @RequestBody ChannelSaveRequest request) {
        CalibChannelConfig config = toChannelConfig(request);
        return ApiResponse.success(
                calibrationService.createChannelConfig(config));
    }

    /** 修改校准通道配置。 */
    @PostMapping("/channels/update")
    public ApiResponse<Void> updateChannel(
            @RequestBody ChannelSaveRequest request) {
        requireId(request.getId());
        CalibChannelConfig config = toChannelConfig(request);
        requireSuccess(
                calibrationService.updateChannelConfig(config),
                "校准通道修改失败");
        return ApiResponse.success();
    }

    /** 切换当前任务下校准通道的启用状态。 */
    @PostMapping("/channels/enabled")
    public ApiResponse<Void> updateChannelEnabled(
            @Valid @RequestBody CalibrationChannelEnabledRequest request) {
        // 仅修改指定任务下的通道开关，提交后刷新处理模块运行配置。
        requireSuccess(calibrationService.updateChannelEnabled(
                request.getTaskId(), request.getId(), request.getEnabled()),
                "校准通道启用状态修改失败");
        return ApiResponse.success();
    }

    /** 删除校准通道配置。 */
    @PostMapping("/channels/delete")
    public ApiResponse<Void> deleteChannel(
            @Valid @RequestBody IdRequest request) {
        requireSuccess(
                calibrationService.deleteChannelConfig(request.getId()),
                "校准通道删除失败");
        return ApiResponse.success();
    }

    /** 查询校准记录。 */
    @GetMapping("/records")
    public ApiResponse<PageResult<CalibRecord>> listRecords(
            @RequestParam String taskId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(
                calibrationService.pageCalibRecords(taskId, pageNum, pageSize));
    }

    /** 查询校准记录详情。 */
    @GetMapping("/records/{id}")
    public ApiResponse<CalibRecordDetailVO> getRecord(
            @PathVariable Long id,
            @RequestParam String taskId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(calibrationService.getCalibRecordDetail(
                id, taskId, pageNum, pageSize));
    }

    /** 将校准通道请求 DTO 转换为数据库实体。 */
    private CalibChannelConfig toChannelConfig(ChannelSaveRequest request) {
        CalibChannelConfig config = new CalibChannelConfig();
        BeanUtils.copyProperties(request, config);
        return config;
    }

    /** 校验写操作请求中的业务主键。 */
    private void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("业务主键不能为空");
        }
    }

    /** 将实现层执行结果转换为统一业务异常。 */
    private void requireSuccess(boolean success, String message) {
        if (!success) {
            throw new IllegalArgumentException(message);
        }
    }
}
