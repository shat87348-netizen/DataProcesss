package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.CollectInterfaceStatistics;
import com.example.dataadmin.entity.DataProcessingStatistics;
import com.example.dataadmin.vo.processing.DataProcessingOverviewVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 总览统计及采集、处理曲线数据访问接口。 */
public interface DashboardMapper {
  /** 按试验任务汇总处理页面顶部统计数据。 */
  DataProcessingOverviewVO findProcessingOverview(@Param("taskId") String taskId);

  /** 统计未处理告警数量。 */
  long countUnhandledAlarms(@Param("taskId") String taskId);

  /** 查询指定起始时间后的采集五秒统计。 */
  List<CollectInterfaceStatistics> findCollectionStatisticsRange(
      @Param("taskId") String taskId, @Param("startTime") LocalDateTime startTime);

  /** 查询指定起始时间后的处理五秒统计。 */
  List<DataProcessingStatistics> findProcessingStatisticsRange(
      @Param("taskId") String taskId, @Param("startTime") LocalDateTime startTime);

  /** 查询采集接口配置，用于补充系统接口、协议和状态信息。 */
  List<CollectInterfaceConfig> findInterfaces(@Param("taskId") String taskId);

}
