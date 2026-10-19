package com.example.common.retention;

/** 各类业务数据的保留天数，未配置时不执行过期清理。 */
public class DataRetentionPolicy {
    /** 遥测参数、正常帧、异常帧和遥测曲线的保留天数。 */
    private Integer telemetryDays;
    /** 采集统计的保留天数。 */
    private Integer collectionStatisticsDays;
    /** 处理统计的保留天数。 */
    private Integer processingStatisticsDays;
    /** 系统资源快照的保留天数。 */
    private Integer systemMonitorDays;
    /** 操作日志的保留天数。 */
    private Integer operationLogDays;
    /** 数据告警的保留天数。 */
    private Integer dataAlarmDays;
    /** 系统告警的保留天数。 */
    private Integer systemAlarmDays;
    /** 野值详情的保留天数，校准汇总及累计计数不参与过期删除。 */
    private Integer calibrationDays;
    /** 文件、图片数据库记录的保留天数。 */
    private Integer fileRecordDays;

    /** 配置必须为空或正整数，防止零天、负数误删数据。 */
    private Integer validDays(Integer days) {
        if (days != null && days <= 0) {
            throw new IllegalArgumentException("数据保留天数必须为正整数，不删除时请留空");
        }
        return days;
    }

    public Integer getTelemetryDays() { return telemetryDays; }
    public void setTelemetryDays(Integer days) { telemetryDays = validDays(days); }
    public Integer getCollectionStatisticsDays() { return collectionStatisticsDays; }
    public void setCollectionStatisticsDays(Integer days) { collectionStatisticsDays = validDays(days); }
    public Integer getProcessingStatisticsDays() { return processingStatisticsDays; }
    public void setProcessingStatisticsDays(Integer days) { processingStatisticsDays = validDays(days); }
    public Integer getSystemMonitorDays() { return systemMonitorDays; }
    public void setSystemMonitorDays(Integer days) { systemMonitorDays = validDays(days); }
    public Integer getOperationLogDays() { return operationLogDays; }
    public void setOperationLogDays(Integer days) { operationLogDays = validDays(days); }
    public Integer getDataAlarmDays() { return dataAlarmDays; }
    public void setDataAlarmDays(Integer days) { dataAlarmDays = validDays(days); }
    public Integer getSystemAlarmDays() { return systemAlarmDays; }
    public void setSystemAlarmDays(Integer days) { systemAlarmDays = validDays(days); }
    public Integer getCalibrationDays() { return calibrationDays; }
    public void setCalibrationDays(Integer days) { calibrationDays = validDays(days); }
    public Integer getFileRecordDays() { return fileRecordDays; }
    public void setFileRecordDays(Integer days) { fileRecordDays = validDays(days); }
}
