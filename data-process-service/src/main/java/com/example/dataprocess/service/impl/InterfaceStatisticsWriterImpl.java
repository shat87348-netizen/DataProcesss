package com.example.dataprocess.service.impl;

import com.example.dataprocess.entity.CollectInterfaceStatisticsRecord;
import com.example.dataprocess.entity.DataProcessingStatisticsRecord;
import com.example.dataprocess.mapper.InterfaceStatisticsMapper;
import com.example.dataprocess.service.InterfaceStatisticsWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 采集和处理统计事务写入实现。 */
@Service
public class InterfaceStatisticsWriterImpl implements InterfaceStatisticsWriter {

    /** 统计数据访问组件。 */
    private final InterfaceStatisticsMapper mapper;

    public InterfaceStatisticsWriterImpl(InterfaceStatisticsMapper mapper) {
        this.mapper = mapper;
    }

    /** 两张统计表在同一事务中写入，防止同一窗口只完成一半。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void write(
            List<CollectInterfaceStatisticsRecord> collectionRecords,
            List<DataProcessingStatisticsRecord> processingRecords) {
        if (collectionRecords != null && !collectionRecords.isEmpty()) {
            // 第一步：固定接口加锁顺序，避免并发批次互相等待。
            List<CollectInterfaceStatisticsRecord> orderedRecords =
                    new ArrayList<>(collectionRecords);
            orderedRecords.sort(Comparator
                    .comparing(CollectInterfaceStatisticsRecord::getInterfaceId)
                    .thenComparing(CollectInterfaceStatisticsRecord::getStatisticsTime));
            List<Long> increments = new ArrayList<>(orderedRecords.size());
            for (CollectInterfaceStatisticsRecord record : orderedRecords) {
                if (record.getCollectionCount() == null || record.getCollectionCount() < 0) {
                    throw new IllegalArgumentException("采集窗口数量不能为负数或空值");
                }
                if (mapper.lockCollectionInterface(record.getTaskId(), record.getInterfaceId()) == null) {
                    throw new IllegalStateException("采集接口不存在或试验任务不匹配：" + record.getInterfaceId());
                }
                // 第二步：只计算这个窗口相对已入库记录新增的完整帧或文件数量。
                Long storedCount = mapper.findStoredCollectionCount(
                        record.getTaskId(), record.getInterfaceId(), record.getStatisticsTime());
                long previous = storedCount == null ? 0L : storedCount;
                increments.add(Math.max(0L, record.getCollectionCount() - previous));
            }
            // 第三步：先写五秒明细，再在同一事务内更新接口长期累计量。
            mapper.saveCollectionStatistics(orderedRecords);
            for (int index = 0; index < orderedRecords.size(); index++) {
                long increment = increments.get(index);
                if (increment == 0L) {
                    continue;
                }
                CollectInterfaceStatisticsRecord record = orderedRecords.get(index);
                if (mapper.addInterfaceCollectionCount(
                        record.getTaskId(), record.getInterfaceId(), increment) != 1) {
                    throw new IllegalStateException("采集接口累计数量更新失败：" + record.getInterfaceId());
                }
            }
        }
        if (processingRecords != null && !processingRecords.isEmpty()) {
            mapper.saveProcessingStatistics(processingRecords);
        }
    }
}
