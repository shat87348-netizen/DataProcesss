package com.example.common.alarm;
/** 跨管理和处理服务复用的数据监测告警记录。 */
public class DataAlarmRecord {
    /** 主键。 */
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** 试验任务编号。 */
    private String taskId;
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    /** 首次告警时间。 */
    private java.time.LocalDateTime alarmTime;
    public java.time.LocalDateTime getAlarmTime() { return alarmTime; }
    public void setAlarmTime(java.time.LocalDateTime alarmTime) { this.alarmTime = alarmTime; }

    /** 来源英文编码，对应现有数据库来源字段，不额外增加数据库列。 */
    private String sourceCode;
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    /** 来源中文名称仅用于查询展示，始终由枚举编码派生。 */
    public String getSourceName() {
        return com.example.common.enums.DataAlarmSource.labelOf(sourceCode);
    }

    /** 列表告警摘要。 */
    private String alarmMsg;
    public String getAlarmMsg() { return alarmMsg; }
    public void setAlarmMsg(String alarmMsg) { this.alarmMsg = alarmMsg; }

    /** 级别编码。 */
    private Integer alarmLevel;
    public Integer getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(Integer alarmLevel) { this.alarmLevel = alarmLevel; }

    /** 人工处理状态。 */
    private Integer alarmStatus;
    public Integer getAlarmStatus() { return alarmStatus; }
    public void setAlarmStatus(Integer alarmStatus) { this.alarmStatus = alarmStatus; }

    /** 任务名称快照。 */
    private String taskName;
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    /** 通道编码。 */
    private String channelCode;
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }

    /** 触发规则。 */
    private String triggerRule;
    public String getTriggerRule() { return triggerRule; }
    public void setTriggerRule(String triggerRule) { this.triggerRule = triggerRule; }

    /** 逻辑删除标志。 */
    private Integer isDeleted;
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }

    /** 创建时间。 */
    private java.time.LocalDateTime createTime;
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }

    /** 更新时间。 */
    private java.time.LocalDateTime updateTime;
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }

    /** 自动事件类型编码。 */
    private String eventType;
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    /** 采集接口主键。 */
    private Long interfaceId;
    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }

    /** 处理任务主键。 */
    private Long processTaskId;
    public Long getProcessTaskId() { return processTaskId; }
    public void setProcessTaskId(Long processTaskId) { this.processTaskId = processTaskId; }

    /** 校准通道主键。 */
    private Long calibChannelId;
    public Long getCalibChannelId() { return calibChannelId; }
    public void setCalibChannelId(Long calibChannelId) { this.calibChannelId = calibChannelId; }

    /** 遥测参数代号。 */
    private String tmSymbol;
    public String getTmSymbol() { return tmSymbol; }
    public void setTmSymbol(String tmSymbol) { this.tmSymbol = tmSymbol; }

    /** 遥测参数名称。 */
    private String parameterName;
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }

    /** 完整中文详情。 */
    private String alarmDetail;
    public String getAlarmDetail() { return alarmDetail; }
    public void setAlarmDetail(String alarmDetail) { this.alarmDetail = alarmDetail; }

    /** 累计触发次数。 */
    private Integer triggerCount;
    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }

    /** 最近触发时间。 */
    private java.time.LocalDateTime lastTriggerTime;
    public java.time.LocalDateTime getLastTriggerTime() { return lastTriggerTime; }
    public void setLastTriggerTime(java.time.LocalDateTime lastTriggerTime) { this.lastTriggerTime = lastTriggerTime; }

    /** 自动恢复时间，独立于人工处理状态。 */
    private java.time.LocalDateTime recoverTime;
    public java.time.LocalDateTime getRecoverTime() { return recoverTime; }
    public void setRecoverTime(java.time.LocalDateTime recoverTime) { this.recoverTime = recoverTime; }
    /** 合并键只用于后台去重，不作为页面字段返回。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String dedupKey;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    /** 返回自动事件的中文类型名称。 */
    public String getEventTypeName() {
        return com.example.common.enums.DataAlarmType.labelOf(eventType);
    }
}
