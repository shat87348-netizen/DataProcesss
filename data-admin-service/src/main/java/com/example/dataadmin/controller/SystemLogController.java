package com.example.dataadmin.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.dataadmin.entity.SysRuntimeLog;
import com.example.dataadmin.enums.ControllerEnumData;
import com.example.dataadmin.service.SystemLogService;
import com.example.dataadmin.vo.EnumOptionVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统运行日志管理查询接口。
 *
 * 页面只允许查询日志；日志新增由各后台模块通过SystemLogService内部完成。
 */
@RestController
@RequestMapping("/system/logs")
public class SystemLogController {

    /** 系统日志业务服务。 */
    private final SystemLogService logService;

    public SystemLogController(SystemLogService logService) {
        this.logService = logService;
    }

    /** 查询系统日志页面涉及的全部枚举选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, List<EnumOptionVO>>> findData() {
        return ApiResponse.success(ControllerEnumData.systemLog());
    }

    /** 按页面条件组合筛选并分页查询系统运行日志。 */
    @GetMapping
    public ApiResponse<PageResult<SysRuntimeLog>> listLogs(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String logSource,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(logService.pageLogs(
                taskId,
                logLevel,
                logSource,
                operatorId,
                keyword,
                startTime,
                endTime,
                pageNum,
                pageSize));
    }

    /** 查询单条系统运行日志完整详情。 */
    @GetMapping("/{id}")
    public ApiResponse<SysRuntimeLog> getLog(@PathVariable Long id) {
        return ApiResponse.success(logService.getLog(id));
    }
}
