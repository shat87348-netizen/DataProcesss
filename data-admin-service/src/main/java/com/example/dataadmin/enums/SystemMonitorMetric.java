package com.example.dataadmin.enums;
/** 系统监控指标代码和中文名称。 */
public enum SystemMonitorMetric implements LabeledEnum {
    CPU("cpu_usage", "CPU使用率"), MEMORY("memory_usage", "内存使用率"),
    DISK("disk_usage", "磁盘使用率"), QUEUE("queue_depth", "消息积压数量");
    private final String code;
    private final String label;
    SystemMonitorMetric(String code, String label) { this.code = code; this.label = label; }
    public Object getValue() { return code; }
    public String getLabel() { return label; }
}
