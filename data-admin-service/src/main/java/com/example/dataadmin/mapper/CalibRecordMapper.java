package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.CalibRecord;
import com.example.dataadmin.vo.calibration.CalibRecordListVO;
import com.example.dataadmin.vo.calibration.CalibrationOverviewVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 校准记录表数据访问接口。
 *
 * SQL统一维护在 CalibRecordMapper.xml 中。
 */
public interface CalibRecordMapper {
    /** 按试验任务一次汇总全部有效记录的累计校准及野值数量。 */
    CalibrationOverviewVO findOverview(@Param("taskId") String taskId);
    /** 按试验任务查询校准记录列表，并返回各通道累计校准数量。 */
    List<CalibRecordListVO> findAllByTaskId(@Param("taskId") String taskId);
    /** 按任务和主键查询单条业务数据。 */
    CalibRecord findByIdAndTaskId(
            @Param("id") Long id,
            @Param("taskId") String taskId);
    /** 新增业务数据并回填主键。 */
    int insert(CalibRecord entity);
    /** 按校准通道同步汇总记录中的任务和通道名称。 */
    int updateSummaryByChannelId(
            @Param("channelId") Long channelId,
            @Param("taskId") String taskId,
            @Param("channelName") String channelName);
    /** 按校准通道逻辑删除汇总记录。 */
    int deleteByChannelId(@Param("channelId") Long channelId);
}
