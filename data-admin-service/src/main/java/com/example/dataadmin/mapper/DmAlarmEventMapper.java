package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.DmAlarmEvent;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据监测告警事件表数据访问接口。
 *
 * SQL统一维护在 DmAlarmEventMapper.xml 中。
 */
public interface DmAlarmEventMapper {
    /** 自动同步告警复用跨实例合并锁和最近记录查询。 */
    void lockKey(@Param("key") String key);
    DmAlarmEvent findRecent(@Param("key") String key, @Param("since") LocalDateTime since);
    int mergeAutomatic(DmAlarmEvent record);
    int recoverAutomatic(DmAlarmEvent record);
    /** 按页面筛选条件查询数据监测告警列表。 */
    List<DmAlarmEvent> findAll(
            @Param("taskId") String taskId,
            @Param("sourceCode") String sourceCode,
            @Param("keyword") String keyword,
            @Param("sourceKeywordValues") List<String> sourceKeywordValues,
            @Param("alarmLevel") Integer alarmLevel,
            @Param("alarmStatus") Integer alarmStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    /** 按主键查询单条业务数据。 */
    DmAlarmEvent findById(@Param("id") Long id);
    /** 后台自动新增告警并回填主键。 */
    int insert(DmAlarmEvent entity);
    /** 更新人工处理状态，保留后台异常信息和自动恢复时间。 */
    int updateStatus(@Param("id") Long id, @Param("alarmStatus") Integer alarmStatus);
}
