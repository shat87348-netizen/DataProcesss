package com.example.dataprocess.mapper;

import com.example.dataprocess.entity.CalibrationChannelRuntimeConfig;
import com.example.dataprocess.entity.CalibrationRecordDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 校准处理的数据访问接口。 */
public interface CalibrationProcessingMapper {
    /** 按任务查询全部启用的校准配置。 */
    List<CalibrationChannelRuntimeConfig> findEnabledChannels(
            @Param("taskId") String taskId);

    /** 批量写入校准野值详情。 */
    int insertRecordDetails(@Param("details") List<CalibrationRecordDetail> details);

    /** 按校准通道ID累加校准记录的野值数量。 */
    int incrementRecordOutlierCount(
            @Param("channelId") Long channelId,
            @Param("outlierCount") Integer outlierCount);

    /** 按任务及校准通道累加参与校准的遥测参数数量。 */
    int incrementRecordCalibrationCount(
            @Param("taskId") String taskId,
            @Param("channelId") Long channelId,
            @Param("calibrationCount") Long calibrationCount);
}
