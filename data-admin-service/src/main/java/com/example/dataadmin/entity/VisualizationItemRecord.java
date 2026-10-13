package com.example.dataadmin.entity;

import com.example.dataadmin.dto.visualization.VisualizationConfigFile;

/** 配置文件内部数据项查询结果，保留主键以检查失效关联。 */
public class VisualizationItemRecord extends VisualizationConfigFile.Item {
    /** 所属组件主键。 */
    private Long widgetId;
    /** 可选的遥测解析配置主键。 */
    private Long parseRuleId;
    /** 可选的采集接口主键。 */
    private Long interfaceId;
    /** 可选的协议配置主键。 */
    private Long protocolConfigId;

    /** 读取组件主键。 */
    public Long getWidgetId() { return widgetId; }
    /** 设置组件主键。 */
    public void setWidgetId(Long widgetId) { this.widgetId = widgetId; }
    /** 读取参数主键。 */
    public Long getParseRuleId() { return parseRuleId; }
    /** 设置参数主键。 */
    public void setParseRuleId(Long parseRuleId) { this.parseRuleId = parseRuleId; }
    /** 读取接口主键。 */
    public Long getInterfaceId() { return interfaceId; }
    /** 设置接口主键。 */
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    /** 读取协议主键。 */
    public Long getProtocolConfigId() { return protocolConfigId; }
    /** 设置协议主键。 */
    public void setProtocolConfigId(Long protocolConfigId) { this.protocolConfigId = protocolConfigId; }
}
