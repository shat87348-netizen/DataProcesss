package com.example.dataadmin.service;

import com.example.common.response.PageResult;
import com.example.dataadmin.entity.CalibChannelConfig;
import com.example.dataadmin.entity.CalibRecord;
import com.example.dataadmin.vo.calibration.CalibRecordDetailVO;
import com.example.dataadmin.vo.calibration.CalibrationOverviewVO;

import java.util.List;

/**
 * 数据校准页面业务接口。
 *
 * 负责组织页面所需的查询和写入操作，数据库访问由对应Mapper完成。
 */
public interface DataCalibrationService {
    /** 查询指定试验任务的野值数量总览。 */
    CalibrationOverviewVO getOverview(String taskId);
    /** 查询指定试验任务的校准通道列表。 */
    List<CalibChannelConfig> listChannelConfigs(String taskId);
    /** 查询校准通道详情。 */
    CalibChannelConfig getChannelConfig(Long id);
    /** 新增校准通道配置。 */
    Long createChannelConfig(CalibChannelConfig config);
    /** 更新校准通道配置。 */
    boolean updateChannelConfig(CalibChannelConfig config);
    /** 切换指定任务下有效校准通道的启用状态。 */
    boolean updateChannelEnabled(String taskId, Long id, Integer enabled);
    /** 删除校准通道配置。 */
    boolean deleteChannelConfig(Long id);
    /** 查询指定试验任务的校准记录列表。 */
    PageResult<CalibRecord> pageCalibRecords(
            String taskId,
            Integer pageNum,
            Integer pageSize);
    /** 查询校准记录汇总和分页野值详情。 */
    CalibRecordDetailVO getCalibRecordDetail(
            Long id,
            String taskId,
            Integer pageNum,
            Integer pageSize);
}
