package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.SysMonitorSnapshot;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 系统状态快照数据访问接口。 */
public interface SysMonitorSnapshotMapper {
    /** 执行轻量查询，用于测量数据库连接耗时。 */
    int ping();
    int insert(SysMonitorSnapshot snapshot);
    List<SysMonitorSnapshot> findHistory(
            @Param("startTime") LocalDateTime startTime);
}
