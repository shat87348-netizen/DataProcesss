package com.example.dataadmin.service;

import com.example.common.response.PageResult;
import com.example.dataadmin.entity.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统日志页面业务接口。
 *
 * 负责组织页面所需的查询和写入操作，数据库访问由对应Mapper完成。
 */
public interface SystemLogService {
    /** 按页面条件组合筛选并分页查询系统运行日志。 */
    PageResult<SysRuntimeLog> pageLogs(
            String taskId,
            String logLevel,
            String logSource,
            String operatorId,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize);
    /** 查询指定试验任务的系统运行日志。 */
    List<SysRuntimeLog> listLogs(String taskId);
    /** 查询系统运行日志详情。 */
    SysRuntimeLog getLog(Long id);
    /** 新增系统运行日志。 */
    Long createLog(SysRuntimeLog log);
    /** 更新系统运行日志。 */
    boolean updateLog(SysRuntimeLog log);
    /** 删除系统运行日志。 */
    boolean deleteLog(Long id);
    /** 清空指定试验任务的系统运行日志。 */
    boolean clearLogs(String taskId);
}
