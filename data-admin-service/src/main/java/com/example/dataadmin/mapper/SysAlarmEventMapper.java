package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.SysAlarmEvent;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 系统告警数据访问接口。 */
public interface SysAlarmEventMapper {
    int insert(SysAlarmEvent event);
    SysAlarmEvent findOpen(
            @Param("metricCode") String metricCode);
    List<SysAlarmEvent> findAll(
            @Param("alarmStatus") Integer alarmStatus);
    int updateActive(
            @Param("id") Long id,
            @Param("currentValue") Double currentValue,
            @Param("lastAlarmTime") LocalDateTime lastAlarmTime,
            @Param("alarmLevel") Integer alarmLevel,
            @Param("alarmContent") String alarmContent);
    /** 按主键恢复一条活动告警，避免误更新同指标的历史事件。 */
    int recoverById(
            @Param("id") Long id,
            @Param("recoverTime") LocalDateTime recoverTime);
}
