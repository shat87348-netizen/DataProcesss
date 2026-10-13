package com.example.dataadmin.vo.processing;

/** 数据处理页面实时监控指标。 */
public class DataProcessingRealtimeVO {

    /** 当前处理速率，返回值直接包含条/秒单位。 */
    private String processRate = "0 条/秒";

    /** 管理服务所在机器整体CPU使用率，与系统告警同口径，返回值直接包含百分号。 */
    private String cpuUsage = "0.00%";

    /** 管理服务所在机器物理内存使用率，与系统告警同口径，返回值直接包含百分号。 */
    private String memoryUsage = "0.00%";

    public String getProcessRate() {
        return processRate;
    }

    public void setProcessRate(String processRate) {
        this.processRate = processRate;
    }

    public String getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(String cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public String getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(String memoryUsage) {
        this.memoryUsage = memoryUsage;
    }
}
