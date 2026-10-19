package com.example.dataadmin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 当前任务及场景下已勾选的遥测参数。 */
public class ProcessedTelemetryFilterSelection {
    /** 当前试验任务编号。 */
    private String taskId;
    /** 设备卫星主键。 */
    private Long deviceSatelliteId;
    /** 内部查询使用的设备卫星类型，复用所属设备资料，不作为接口出参。 */
    @JsonIgnore
    private String deviceSatelliteType;
    /** 遥测代号。 */
    private String telemetryCode;
    /** 勾选场景。 */
    private Integer selectionType;
    /** 固定页面为零，可视化页面为组件主键。 */
    private Long targetId;
    /** 当前有效解析配置中的遥测名称。 */
    private String telemetryName;

    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public Long getDeviceSatelliteId() { return deviceSatelliteId; }
    public void setDeviceSatelliteId(Long value) { this.deviceSatelliteId = value; }
    public String getDeviceSatelliteType() { return deviceSatelliteType; }
    public void setDeviceSatelliteType(String value) { this.deviceSatelliteType = value; }
    public String getTelemetryCode() { return telemetryCode; }
    public void setTelemetryCode(String value) { this.telemetryCode = value; }
    public Integer getSelectionType() { return selectionType; }
    public void setSelectionType(Integer value) { this.selectionType = value; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { this.targetId = value; }
    public String getTelemetryName() { return telemetryName; }
    public void setTelemetryName(String value) { this.telemetryName = value; }
}
