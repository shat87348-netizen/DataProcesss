package com.example.dataadmin.service;

import com.example.common.response.PageResult;
import com.example.dataadmin.entity.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警管理页面业务接口。
 *
 * 负责组织页面所需的查询和写入操作，数据库访问由对应Mapper完成。
 */
public interface AlarmManagementService {

    /** 查询系统健康监测的最新状态。 */
    SysMonitorSnapshot getSystemMonitorCurrent();

    /** 查询系统健康监测历史快照。 */
    List<SysMonitorSnapshot> getSystemMonitorHistory(int minutes);

    /** 查询统一系统告警列表，状态为空时返回全部。 */
    List<SysAlarmEvent> listSystemAlarms(Integer status);
    /** 按页面筛选条件分页查询数据监测告警。 */
    PageResult<DmAlarmEvent> pageDataAlarms(
            String taskId,
            String sourceCode,
            String keyword,
            Integer alarmLevel,
            Integer alarmStatus,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize);
    /** 查询数据监测告警详情。 */
    DmAlarmEvent getDataAlarm(Long id);
    /** 更新数据监测告警人工处理状态，不改变自动恢复时间。 */
    boolean updateDataAlarmStatus(Long id, Integer alarmStatus);
}
