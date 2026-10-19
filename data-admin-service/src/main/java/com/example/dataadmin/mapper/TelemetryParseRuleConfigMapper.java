package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.TelemetryParseRuleConfig;
import com.example.dataadmin.entity.TelemetryFilterSearchRecord;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 参数配置数据访问，任务ID始终参与查询和修改。 */
public interface TelemetryParseRuleConfigMapper {
    /** 查询当前任务所选设备卫星的参数。 */
    List<TelemetryParseRuleConfig> findAllByTaskId(@Param("taskId") String taskId, @Param("deviceSatelliteId") Long deviceSatelliteId);
    /** 查询当前层级直属的有效遥测参数。 */
    List<TelemetryParseRuleConfig> findDirectBySystem(@Param("taskId") String taskId,
            @Param("deviceSatelliteId") Long deviceSatelliteId, @Param("systemId") Long systemId);
    /** 按主键和任务查验待勾选参数。 */
    TelemetryParseRuleConfig findActiveById(@Param("taskId") String taskId, @Param("id") Long id);
    /** 一次读取本次提交的全部有效参数并校验任务范围。 */
    List<TelemetryParseRuleConfig> findActiveByIds(@Param("taskId") String taskId,
            @Param("ids") List<Long> ids);
    /** 排除本批次记录后检查最终序号和代号是否与其他参数冲突。 */
    Long findBatchConflict(@Param("taskId") String taskId,
            @Param("deviceSatelliteId") Long deviceSatelliteId,
            @Param("ids") List<Long> ids, @Param("tableIndexes") List<String> tableIndexes,
            @Param("telemetryCodes") List<String> telemetryCodes);
    /** 暂存唯一键以支持同一批次交换序号或遥测代号。 */
    int updateTemporaryKeys(@Param("taskId") String taskId,
            @Param("rule") TelemetryParseRuleConfig rule);
    /** 写回完整的参数业务字段。 */
    int updateOne(@Param("taskId") String taskId,
            @Param("rule") TelemetryParseRuleConfig rule);
    /** 按完整遥测代号查找当前任务的有效参数及其勾选状态。 */
    List<TelemetryFilterSearchRecord> searchByTelemetryCode(@Param("taskId") String taskId,
            @Param("telemetryCode") String telemetryCode,
            @Param("selectionType") int selectionType, @Param("targetId") Long targetId);
    /** 统计设备全部有效参数；指定系统时只统计该系统直属参数。 */
    long countPage(@Param("taskId") String taskId,
            @Param("deviceSatelliteId") Long deviceSatelliteId,
            @Param("systemId") Long systemId);
    /** 分页查询设备全部有效参数；指定系统时只查询该系统直属参数。 */
    List<TelemetryParseRuleConfig> findPage(@Param("taskId") String taskId,
            @Param("deviceSatelliteId") Long deviceSatelliteId,
            @Param("systemId") Long systemId, @Param("limit") int limit,
            @Param("offset") long offset);
    /** 分批导入配置。 */
    int batchInsert(@Param("list") List<TelemetryParseRuleConfig> list);
    /** 逻辑删除当前任务同类型设备卫星的参数。 */
    int deleteByType(@Param("taskId") String taskId, @Param("type") String type);
}
