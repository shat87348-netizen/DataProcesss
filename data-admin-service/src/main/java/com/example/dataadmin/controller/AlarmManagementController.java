package com.example.dataadmin.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.dataadmin.dto.IdStatusRequest;
import com.example.dataadmin.entity.DmAlarmEvent;
import com.example.dataadmin.entity.SysAlarmEvent;
import com.example.dataadmin.entity.SysMonitorSnapshot;
import com.example.dataadmin.enums.ControllerEnumData;
import com.example.dataadmin.service.AlarmManagementService;
import com.example.dataadmin.vo.EnumOptionVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据监测告警和系统告警管理接口。
 */
@RestController
@RequestMapping("/alarms")
public class AlarmManagementController {

    private final AlarmManagementService alarmService;

    public AlarmManagementController(AlarmManagementService alarmService) {
        this.alarmService = alarmService;
    }

    /** 查询告警管理页面涉及的全部枚举选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, List<EnumOptionVO>>> findData() {
        return ApiResponse.success(ControllerEnumData.alarm());
    }

    /** 查询系统健康监测当前状态，供“系统告警”页顶部指标卡展示。 */
    @GetMapping("/system-monitor/current")
    public ApiResponse<SysMonitorSnapshot> getSystemMonitorCurrent() {
        return ApiResponse.success(alarmService.getSystemMonitorCurrent());
    }

    /** 查询一周内系统健康状态快照，用于趋势曲线。 */
    @GetMapping("/system-monitor/history")
    public ApiResponse<List<SysMonitorSnapshot>> getSystemMonitorHistory(
            @RequestParam(defaultValue = "60") int minutes) {
        return ApiResponse.success(
                alarmService.getSystemMonitorHistory(minutes));
    }

    /** 按来源编码查询数据监测告警，中文来源名称由返回结果提供。 */
    @GetMapping("/data")
    public ApiResponse<PageResult<DmAlarmEvent>> listDataAlarms(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer alarmLevel,
            @RequestParam(required = false) Integer alarmStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(
                alarmService.pageDataAlarms(
                        taskId,
                        sourceCode,
                        keyword,
                        alarmLevel,
                        alarmStatus,
                        startTime,
                        endTime,
                        pageNum,
                        pageSize));
    }

    /** 查询数据监测告警详情。 */
    @GetMapping("/data/{id}")
    public ApiResponse<DmAlarmEvent> getDataAlarm(@PathVariable Long id) {
        return ApiResponse.success(alarmService.getDataAlarm(id));
    }

    /** 标记数据监测告警人工处理状态。 */
    // 人工处理状态与系统自动恢复独立，修改处理状态使用编辑权限。
    @PostMapping("/data/status")
    public ApiResponse<Void> updateDataAlarmStatus(
            @Valid @RequestBody IdStatusRequest request) {
        validateBinaryStatus(request.getStatus());
        requireSuccess(
                alarmService.updateDataAlarmStatus(
                        request.getId(),
                        request.getStatus()),
                "数据监测告警状态修改失败");
        return ApiResponse.success();
    }

    /** 查询系统告警。 */
    @GetMapping("/system")
    public ApiResponse<List<SysAlarmEvent>> listSystemAlarms(
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(alarmService.listSystemAlarms(status));
    }

    /** 校验二值状态字段。 */
    private void validateBinaryStatus(Integer status) {
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("状态只能为0或1");
        }
    }

    /** 将实现层执行结果转换为统一业务异常。 */
    private void requireSuccess(boolean success, String message) {
        if (!success) {
            throw new IllegalArgumentException(message);
        }
    }
}
