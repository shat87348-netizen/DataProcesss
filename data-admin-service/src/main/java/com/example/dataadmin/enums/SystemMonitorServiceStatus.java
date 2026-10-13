package com.example.dataadmin.enums;
/** 注册中心查询得到的数据处理服务状态。 */
public enum SystemMonitorServiceStatus implements LabeledEnum {
    RUNNING("运行中"), DOWN("停止"), ERROR("查询异常");
    private final String label;
    SystemMonitorServiceStatus(String label) { this.label = label; }
    public Object getValue() { return name(); }
    public String getLabel() { return label; }
}
