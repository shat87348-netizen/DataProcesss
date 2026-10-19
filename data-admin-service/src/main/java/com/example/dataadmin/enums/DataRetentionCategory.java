package com.example.dataadmin.enums;

import com.example.common.retention.DataRetentionPolicy;
import java.util.function.Function;

/** 清理分类限定为固定业务表，SQL映射不接受任意表名。 */
public enum DataRetentionCategory implements LabeledEnum {
    COLLECTION("collection", "采集统计", DataRetentionPolicy::getCollectionStatisticsDays),
    PROCESSING("processing", "处理统计", DataRetentionPolicy::getProcessingStatisticsDays),
    SYSTEM_MONITOR("system-monitor", "系统资源快照", DataRetentionPolicy::getSystemMonitorDays),
    OPERATION_LOG("operation-log", "操作日志", DataRetentionPolicy::getOperationLogDays),
    DATA_ALARM("data-alarm", "数据告警", DataRetentionPolicy::getDataAlarmDays),
    SYSTEM_ALARM("system-alarm", "系统告警", DataRetentionPolicy::getSystemAlarmDays),
    CALIBRATION_DETAIL("calibration-detail", "校准野值详情", DataRetentionPolicy::getCalibrationDays),
    FILE_RECORD("file-record", "文件图片记录", DataRetentionPolicy::getFileRecordDays);

    /** XML使用固定英文分类码选择SQL。 */
    private final String value;
    /** 清理结果使用中文名称记录日志。 */
    private final String label;
    /** 每类读取自己的配置，空值不参与清理。 */
    private final Function<DataRetentionPolicy, Integer> days;

    DataRetentionCategory(String value, String label, Function<DataRetentionPolicy, Integer> days) {
        this.value = value;
        this.label = label;
        this.days = days;
    }

    public String getValue() { return value; }
    public String getLabel() { return label; }
    public Integer retentionDays(DataRetentionPolicy policy) { return days.apply(policy); }
}
