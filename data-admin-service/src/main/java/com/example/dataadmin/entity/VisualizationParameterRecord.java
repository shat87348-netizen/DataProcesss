package com.example.dataadmin.entity;

import com.example.dataadmin.dto.visualization.VisualizationConfigFile;

/** 配置导入导出内部查询结果，主键仅用于数据库关联，不直接序列化到文件。 */
public class VisualizationParameterRecord extends VisualizationConfigFile.Parameter {
    /** 勾选所属组件主键。 */
    private Long widgetId;
    /** 当前有效参数解析配置主键。 */
    private Long parseRuleId;
    /** 当前有效设备卫星主键。 */
    private Long deviceSatelliteId;

    /** 读取组件主键。 */
    public Long getWidgetId() { return widgetId; }
    /** 设置组件主键。 */
    public void setWidgetId(Long widgetId) { this.widgetId = widgetId; }
    /** 读取解析配置主键。 */
    public Long getParseRuleId() { return parseRuleId; }
    /** 设置解析配置主键。 */
    public void setParseRuleId(Long parseRuleId) { this.parseRuleId = parseRuleId; }
    /** 读取设备卫星主键。 */
    public Long getDeviceSatelliteId() { return deviceSatelliteId; }
    /** 设置设备卫星主键。 */
    public void setDeviceSatelliteId(Long deviceSatelliteId) { this.deviceSatelliteId = deviceSatelliteId; }
}
