package com.example.dataadmin.entity;

import com.example.dataadmin.enums.SystemLogLevel;
import com.example.dataadmin.enums.SystemLogSource;

import java.time.LocalDateTime;

/**
 * 系统日志表实体。
 *
 * 对应数据库表 sys_runtime_log，用于承载业务数据和MyBatis查询结果。
 */
public class SysRuntimeLog {
    /** 自增主键。 */
    private Long id;
    /** 试验任务ID。 */
    private String taskId;
    /** 时间。 */
    private LocalDateTime logTime;
    /** 日志级别：INFO信息 WARN警告 ERROR错误。 */
    private String logLevel;
    /** 来源英文编码。 */
    private String logSource;
    /** 操作人ID。 */
    private String operatorId;
    /** 内容。 */
    private String logContent;
    /** 详情。 */
    private String logDetail;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getLogTime() {
        return logTime;
    }

    public void setLogTime(LocalDateTime logTime) {
        this.logTime = logTime;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /** 返回日志级别中文名称，供所有包含系统日志的接口直接展示。 */
    public String getLogLevelName() {
        return SystemLogLevel.descriptionOf(logLevel);
    }

    public String getLogSource() {
        return logSource;
    }

    public void setLogSource(String logSource) {
        this.logSource = logSource;
    }

    /** 返回来源枚举的中文名称，供列表、详情和操作快照展示。 */
    public String getLogSourceName() {
        return SystemLogSource.labelOf(logSource);
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getLogContent() {
        return logContent;
    }

    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    public String getLogDetail() {
        return logDetail;
    }

    public void setLogDetail(String logDetail) {
        this.logDetail = logDetail;
    }

}
