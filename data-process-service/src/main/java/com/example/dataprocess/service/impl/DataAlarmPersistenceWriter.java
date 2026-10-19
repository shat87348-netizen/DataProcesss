package com.example.dataprocess.service.impl;

import com.example.common.alarm.DataAlarmRecord;
import com.example.dataprocess.mapper.DataAlarmRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
/** 告警批次使用独立事务，失败后整批保留重试。 */
@Service
public class DataAlarmPersistenceWriter {
    private final DataAlarmRecordMapper mapper;
    public DataAlarmPersistenceWriter(DataAlarmRecordMapper mapper) { this.mapper = mapper; }
    @Transactional(rollbackFor = Exception.class)
    public void write(List<DataAlarmRecord> records) {
        for (DataAlarmRecord record : records) {
            // 同一合并键的查询和写入需要处于同一事务锁内。
            mapper.lockKey(record.getDedupKey());
            if (record.getTriggerCount() == 0) {
                mapper.recover(record);
                continue;
            }
            // 仅合并一分钟内仍活动且未人工处理的同对象同参数告警。
            DataAlarmRecord existing = mapper.findRecent(record.getDedupKey(),
                    record.getAlarmTime().minusSeconds(60));
            if (existing == null) {
                mapper.insert(record);
            } else {
                record.setId(existing.getId());
                mapper.merge(record);
            }
        }
    }
}
