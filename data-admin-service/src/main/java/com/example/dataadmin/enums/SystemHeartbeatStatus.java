package com.example.dataadmin.enums;
/** 处理服务心跳展示状态，未接入上报时显示等待心跳。 */
public enum SystemHeartbeatStatus implements LabeledEnum {
    NORMAL("正常"), WAITING("等待心跳");
    private final String label;
    SystemHeartbeatStatus(String label) { this.label = label; }
    public Object getValue() { return name(); }
    public String getLabel() { return label; }
}
