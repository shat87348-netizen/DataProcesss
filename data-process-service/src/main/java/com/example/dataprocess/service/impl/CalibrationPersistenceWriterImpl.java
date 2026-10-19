package com.example.dataprocess.service.impl;

import com.example.common.calibration.CalibrationCountCache;
import com.example.common.calibration.CalibrationRecordDetailCache;
import com.example.dataprocess.entity.CalibrationRecordDetail;
import com.example.dataprocess.mapper.CalibrationProcessingMapper;
import com.example.dataprocess.service.CalibrationPersistenceWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 批量写入校准野值详情和汇总数量。 */
@Service
public class CalibrationPersistenceWriterImpl implements CalibrationPersistenceWriter {

    /** 校准处理数据访问组件。 */
    private final CalibrationProcessingMapper mapper;

    /** 注入校准处理数据访问组件。 */
    public CalibrationPersistenceWriterImpl(CalibrationProcessingMapper mapper) {
        this.mapper = mapper;
    }

    /** 每个批次在同一事务内写入详情并更新汇总，避免数量与详情不一致。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void write(
            List<CalibrationRecordDetailCache> details,
            List<CalibrationCountCache> calibrationCounts) {
        // 第一步：本批次既无野值详情也无校准数量时无需执行数据库操作。
        if (details.isEmpty() && calibrationCounts.isEmpty()) {
            return;
        }
        Map<Long, Integer> counts = new LinkedHashMap<Long, Integer>();
        List<CalibrationRecordDetail> records =
                new ArrayList<CalibrationRecordDetail>(details.size());
        // 第二步：转换全部野值详情并按全局唯一的校准通道主键汇总野值数量。
        for (CalibrationRecordDetailCache detail : details) {
            records.add(record(detail));
            counts.put(detail.getChannelId(),
                    counts.getOrDefault(detail.getChannelId(), 0) + 1);
        }
        // 第三步：只有存在野值详情时才执行批量插入，正常参数只累计数量。
        if (!records.isEmpty()) {
            mapper.insertRecordDetails(records);
        }
        // 第四步：逐个通道累加野值数量，任一通道缺少汇总记录时整体回滚。
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            int updated = mapper.incrementRecordOutlierCount(
                    entry.getKey(), entry.getValue());
            if (updated <= 0) {
                throw new IllegalStateException(
                        "校准通道没有可用的校准记录：" + entry.getKey());
            }
        }
        // 第五步：同一事务内合并并累加校准数量，失败时野值详情及数量一并回滚。
        incrementCalibrationCounts(calibrationCounts);
    }

    /** 按任务及通道合并本批数量，避免对同一汇总记录重复执行更新。 */
    private void incrementCalibrationCounts(List<CalibrationCountCache> calibrationCounts) {
        // 第一步：校验内部数量增量，禁止空任务、空通道或非正数量参与累加。
        Map<String, Map<Long, Long>> taskCounts =
                new LinkedHashMap<String, Map<Long, Long>>();
        for (CalibrationCountCache count : calibrationCounts) {
            if (count.getTaskId() == null || count.getTaskId().trim().isEmpty()
                    || count.getChannelId() == null || count.getCalibrationCount() == null
                    || count.getCalibrationCount() <= 0L) {
                throw new IllegalArgumentException("校准数量缺少任务、通道或有效数量");
            }
            // 第二步：不同接口的增量按任务和通道累加，保证各任务相互隔离。
            Map<Long, Long> channelCounts = taskCounts.computeIfAbsent(
                    count.getTaskId(), taskId -> new LinkedHashMap<Long, Long>());
            channelCounts.put(count.getChannelId(),
                    channelCounts.getOrDefault(count.getChannelId(), 0L)
                            + count.getCalibrationCount());
        }
        // 第三步：通过数据库原子加法更新汇总，避免并发批次覆盖已累计数量。
        for (Map.Entry<String, Map<Long, Long>> taskEntry : taskCounts.entrySet()) {
            for (Map.Entry<Long, Long> channelEntry : taskEntry.getValue().entrySet()) {
                int updated = mapper.incrementRecordCalibrationCount(
                        taskEntry.getKey(), channelEntry.getKey(), channelEntry.getValue());
                if (updated <= 0) {
                    throw new IllegalStateException(
                            "当前试验任务下没有可用的校准记录：" + channelEntry.getKey());
                }
            }
        }
    }

    /** 将缓存中的野值详情转换为数据库写入实体。 */
    private CalibrationRecordDetail record(CalibrationRecordDetailCache source) {
        // 第一步：复制野值详情中的任务、通道、接口和遥测参数信息。
        CalibrationRecordDetail result = new CalibrationRecordDetail();
        result.setTaskId(source.getTaskId());
        result.setChannelId(source.getChannelId());
        result.setInterfaceId(source.getInterfaceId());
        result.setChannelName(source.getChannelName());
        result.setParameterName(source.getParameterName());
        result.setTmSymbol(source.getTmSymbol());
        result.setTelemetryValue(source.getTelemetryValue());
        result.setDetail(source.getDetail());
        // 第二步：沿用遥测消息的采样时间，不使用批量落库时间替代发生时间。
        result.setCreateTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(source.getCreateTime()), ZoneId.systemDefault()));
        return result;
    }
}
