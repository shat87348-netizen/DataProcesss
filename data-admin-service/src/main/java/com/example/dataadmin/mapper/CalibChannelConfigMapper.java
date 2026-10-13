package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.CalibChannelConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 校准通道配置表数据访问接口。
 *
 * SQL统一维护在 CalibChannelConfigMapper.xml 中。
 */
public interface CalibChannelConfigMapper {
    /** 按试验任务ID查询业务数据列表。 */
    List<CalibChannelConfig> findAllByTaskId(@Param("taskId") String taskId);
    /** 按主键查询单条业务数据。 */
    CalibChannelConfig findById(@Param("id") Long id);
    /** 查询同一任务中使用相同校准公式的有效配置数量。 */
    int countByTaskIdAndCalibrationFormula(
            @Param("taskId") String taskId,
            @Param("calibrationFormula") String calibrationFormula,
            @Param("excludeId") Long excludeId);
    /** 新增业务数据并回填主键。 */
    int insert(CalibChannelConfig entity);
    /** 按主键更新业务数据。 */
    int update(CalibChannelConfig entity);
    /** 按主键删除业务数据。 */
    int delete(@Param("id") Long id);
    /** 仅更新指定任务下未删除通道的启用状态。 */
    int updateEnabled(
            @Param("taskId") String taskId,
            @Param("id") Long id,
            @Param("enabled") Integer enabled);
}
