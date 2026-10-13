package com.example.dataadmin.service;
import com.example.dataadmin.entity.DmAlarmEvent;
import com.example.dataadmin.mapper.DmAlarmEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
/** 管理服务运行同步异常使用同一数据告警表和合并规则。 */
@Service
public class AutomaticDataAlarmWriter {
    private final DmAlarmEventMapper mapper;
    public AutomaticDataAlarmWriter(DmAlarmEventMapper mapper) { this.mapper = mapper; }
    @Transactional(rollbackFor = Exception.class)
    public void write(List<DmAlarmEvent> records) {
        for (DmAlarmEvent record : records) {
            mapper.lockKey(record.getDedupKey());
            if (record.getTriggerCount() == 0) { mapper.recoverAutomatic(record); continue; }
            DmAlarmEvent existing = mapper.findRecent(record.getDedupKey(), record.getAlarmTime().minusSeconds(60));
            if (existing == null) { mapper.insert(record); }
            else { record.setId(existing.getId()); mapper.mergeAutomatic(record); }
        }
    }
}
