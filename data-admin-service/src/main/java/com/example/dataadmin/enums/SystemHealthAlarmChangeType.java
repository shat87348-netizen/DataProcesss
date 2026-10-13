package com.example.dataadmin.enums;
/** 系统告警推送变化类型，编码保持原订阅协议兼容。 */
public enum SystemHealthAlarmChangeType implements LabeledEnum {
    CREATED("新增"), UPDATED("持续触发"), RECOVERED("已恢复");
    private final String label;
    SystemHealthAlarmChangeType(String label) { this.label = label; }
    public Object getValue() { return name(); }
    public String getLabel() { return label; }
}
