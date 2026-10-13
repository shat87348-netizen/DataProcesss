package com.example.dataadmin.entity;

import java.time.LocalDateTime;

/** 系统状态快照实体，对应 sys_monitor_snapshot 表。 */
public class SysMonitorSnapshot {
    /** 快照主键 ID。 */
    private Long id;
    /** 状态采样时间。 */
    private LocalDateTime sampleTime;
    /** 服务状态：RUNNING 运行中，DOWN 停止，ERROR 异常。 */
    private String serviceStatus;
    /** 服务运行时长，单位秒。 */
    private Long uptimeSeconds;
    /** 已启用采集接口数量。 */
    private Integer onlineNodeCount;
    /** 未删除采集接口总数量。 */
    private Integer totalNodeCount;
    /** 数据库连接耗时，单位毫秒。 */
    private Long databaseLatencyMs;
    /** 遥测接收队列当前积压数量。 */
    private Long messageQueueDepth;
    /** 数据处理心跳状态：NORMAL 正常，WAITING 等待。 */
    private String heartbeatStatus;
    /** 最近一次数据处理心跳时间。 */
    private LocalDateTime lastHeartbeatTime;
    /** CPU 使用率，单位百分比。 */
    private Double cpuUsagePercent;
    /** 内存使用率，单位百分比。 */
    private Double memoryUsagePercent;
    /** 磁盘使用率，单位百分比。 */
    private Double diskUsagePercent;
    /** 综合健康评分，范围 0～100。 */
    private Integer healthScore;
    /** 记录创建时间。 */
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getSampleTime() { return sampleTime; }
    public void setSampleTime(LocalDateTime sampleTime) { this.sampleTime = sampleTime; }
    /** 返回注册中心服务状态中文名称。 */
    public String getServiceStatusName() {
        return com.example.dataadmin.enums.EnumData.labelOf(
                com.example.dataadmin.enums.SystemMonitorServiceStatus.values(), serviceStatus);
    }
    public String getServiceStatus() { return serviceStatus; }
    public void setServiceStatus(String serviceStatus) { this.serviceStatus = serviceStatus; }
    public Long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(Long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
    public Integer getOnlineNodeCount() { return onlineNodeCount; }
    public void setOnlineNodeCount(Integer onlineNodeCount) { this.onlineNodeCount = onlineNodeCount; }
    public Integer getTotalNodeCount() { return totalNodeCount; }
    public void setTotalNodeCount(Integer totalNodeCount) { this.totalNodeCount = totalNodeCount; }
    public Long getDatabaseLatencyMs() { return databaseLatencyMs; }
    public void setDatabaseLatencyMs(Long databaseLatencyMs) { this.databaseLatencyMs = databaseLatencyMs; }
    public Long getMessageQueueDepth() { return messageQueueDepth; }
    public void setMessageQueueDepth(Long messageQueueDepth) { this.messageQueueDepth = messageQueueDepth; }
    /** 返回心跳状态中文名称，等待状态不等于服务停止。 */
    public String getHeartbeatStatusName() {
        return com.example.dataadmin.enums.EnumData.labelOf(com.example.dataadmin.enums.SystemHeartbeatStatus.values(), heartbeatStatus);
    }
    public String getHeartbeatStatus() { return heartbeatStatus; }
    public void setHeartbeatStatus(String heartbeatStatus) { this.heartbeatStatus = heartbeatStatus; }
    public LocalDateTime getLastHeartbeatTime() { return lastHeartbeatTime; }
    public void setLastHeartbeatTime(LocalDateTime lastHeartbeatTime) { this.lastHeartbeatTime = lastHeartbeatTime; }
    public Double getCpuUsagePercent() { return cpuUsagePercent; }
    public void setCpuUsagePercent(Double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; }
    public Double getMemoryUsagePercent() { return memoryUsagePercent; }
    public void setMemoryUsagePercent(Double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }
    public Double getDiskUsagePercent() { return diskUsagePercent; }
    public void setDiskUsagePercent(Double diskUsagePercent) { this.diskUsagePercent = diskUsagePercent; }
    public Integer getHealthScore() { return healthScore; }
    public void setHealthScore(Integer healthScore) { this.healthScore = healthScore; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
