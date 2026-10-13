package com.example.dataadmin.entity;

import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.EnumData;
import com.example.dataadmin.enums.SystemMonitorMetric;

import java.time.LocalDateTime;

/**
 * 系统告警事件表实体。
 *
 * 对应数据库表 sys_alarm_event，用于承载业务数据和MyBatis查询结果。
 */
public class SysAlarmEvent {
    /** 主键ID。 */
    private Long id;
    /** 告警时间。 */
    private LocalDateTime alarmTime;
    /** 系统对象。 */
    private String objName;
    /** 告警类型英文编码，复用系统监测指标枚举。 */
    private String alarmType;
    /** 告警内容。 */
    private String alarmContent;
    /** 告警级别：2一般 3严重。 */
    private Integer alarmLevel;
    /** 告警状态：0未恢复 1已恢复。 */
    private Integer alarmStatus;
    /** 是否删除：0否 1是。 */
    private Integer isDeleted;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getAlarmTime() {
        return alarmTime;
    }

    public void setAlarmTime(LocalDateTime alarmTime) {
        this.alarmTime = alarmTime;
    }

    public String getObjName() {
        return objName;
    }

    public void setObjName(String objName) {
        this.objName = objName;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    /** 告警类型中文名称只用于展示，来源于已有监测指标枚举。 */
    public String getAlarmTypeName() {
        return EnumData.labelOf(SystemMonitorMetric.values(), alarmType);
    }

    public String getAlarmContent() {
        return alarmContent;
    }

    public void setAlarmContent(String alarmContent) {
        this.alarmContent = alarmContent;
    }

    public Integer getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(Integer alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

    /** 返回系统告警级别中文名称。 */
    public String getAlarmLevelName() {
        return EnumData.labelOf(BusinessEnums.SystemAlarmLevel.values(), alarmLevel);
    }

    public Integer getAlarmStatus() {
        return alarmStatus;
    }

    public void setAlarmStatus(Integer alarmStatus) {
        this.alarmStatus = alarmStatus;
    }

    /** 返回系统告警恢复状态中文名称。 */
    public String getAlarmStatusName() {
        return EnumData.labelOf(BusinessEnums.SystemAlarmStatus.values(), alarmStatus);
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /** 指标编码。 */
    private String metricCode;
    /** 返回指标中文名称。 */
    public String getMetricName() {
        return EnumData.labelOf(com.example.dataadmin.enums.SystemMonitorMetric.values(), metricCode);
    }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    /** 当前指标值。 */
    private Double currentValue;
    public Double getCurrentValue() { return currentValue; }
    public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }

    /** 告警阈值。 */
    private Double thresholdValue;
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }

    /** 首次触发时间。 */
    private LocalDateTime firstAlarmTime;
    public LocalDateTime getFirstAlarmTime() { return firstAlarmTime; }
    public void setFirstAlarmTime(LocalDateTime firstAlarmTime) { this.firstAlarmTime = firstAlarmTime; }

    /** 最近触发时间。 */
    private LocalDateTime lastAlarmTime;
    public LocalDateTime getLastAlarmTime() { return lastAlarmTime; }
    public void setLastAlarmTime(LocalDateTime lastAlarmTime) { this.lastAlarmTime = lastAlarmTime; }

    /** 自动恢复时间。 */
    private LocalDateTime recoverTime;
    public LocalDateTime getRecoverTime() { return recoverTime; }
    public void setRecoverTime(LocalDateTime recoverTime) { this.recoverTime = recoverTime; }

    /** 触发次数。 */
    private Integer triggerCount;
    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }
}
