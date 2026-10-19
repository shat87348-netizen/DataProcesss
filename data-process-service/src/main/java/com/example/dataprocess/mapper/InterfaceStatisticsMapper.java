package com.example.dataprocess.mapper;

import com.example.dataprocess.entity.CollectInterfaceStatisticsRecord;
import com.example.dataprocess.entity.DataProcessingStatisticsRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 采集和处理五秒统计数据访问接口。 */
public interface InterfaceStatisticsMapper {

    /** 锁定采集接口累计计数行，保证同一接口的窗口差量串行计算。 */
    Long lockCollectionInterface(
            @Param("taskId") String taskId,
            @Param("interfaceId") Long interfaceId);

    /** 查询指定五秒窗口已经入库的采集数量。 */
    Long findStoredCollectionCount(
            @Param("taskId") String taskId,
            @Param("interfaceId") Long interfaceId,
            @Param("statisticsTime") LocalDateTime statisticsTime);

    /** 批量新增或覆盖采集统计窗口。 */
    int saveCollectionStatistics(
            @Param("records") List<CollectInterfaceStatisticsRecord> records);

    /** 只把五秒窗口新增的采集数量累加到接口长期计数。 */
    int addInterfaceCollectionCount(
            @Param("taskId") String taskId,
            @Param("interfaceId") Long interfaceId,
            @Param("increment") long increment);

    /** 批量新增或覆盖处理统计窗口。 */
    int saveProcessingStatistics(
            @Param("records") List<DataProcessingStatisticsRecord> records);
}
