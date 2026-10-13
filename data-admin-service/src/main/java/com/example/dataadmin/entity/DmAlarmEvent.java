package com.example.dataadmin.entity;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.EnumData;
/** 数据监测告警，复用跨服务记录字段并补充级别和状态中文展示。 */
public class DmAlarmEvent extends com.example.common.alarm.DataAlarmRecord {
    /** 数据告警仅保留一般与严重两个级别。 */
    public String getAlarmLevelName() {
        return EnumData.labelOf(BusinessEnums.DataAlarmLevel.values(), getAlarmLevel());
    }
    /** 人工处理状态与自动恢复时间独立返回。 */
    public String getAlarmStatusName() {
        return EnumData.labelOf(BusinessEnums.DataAlarmStatus.values(), getAlarmStatus());
    }
}
