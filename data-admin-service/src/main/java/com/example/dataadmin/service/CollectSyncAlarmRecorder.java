package com.example.dataadmin.service;
import com.example.common.alarm.DataAlarmKey;
import com.example.common.enums.DataAlarmType;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.DmAlarmEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** 接口同步后只提交内存记录，异步入库不会影响已提交的配置。 */
@Service
public class CollectSyncAlarmRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectSyncAlarmRecorder.class);
    private final Map<String, DmAlarmEvent> pending = new LinkedHashMap<String, DmAlarmEvent>();
    private final AutomaticDataAlarmWriter writer;
    private List<DmAlarmEvent> retryBatch;
    public CollectSyncAlarmRecorder(AutomaticDataAlarmWriter writer) { this.writer = writer; }
    public synchronized void report(CollectInterfaceConfig config, String cause) {
        String key = DataAlarmKey.of(config.getTaskId(), DataAlarmType.INTERFACE_SYNC, config.getId(), null, null);
        DmAlarmEvent alarm = pending.get(key);
        if (alarm == null || alarm.getTriggerCount() == 0 || alarm.getRecoverTime() != null) {
            if (pending.size() >= 1000) {
                LOGGER.error("采集同步告警队列已满，接口ID：{}，原因：{}", config.getId(), cause);
                return;
            }
            if (alarm != null) { pending.put(key + "|" + java.util.UUID.randomUUID(), alarm); }
            alarm = new DmAlarmEvent();
            alarm.setDedupKey(key);
            alarm.setAlarmTime(LocalDateTime.now());
            alarm.setTaskId(config.getTaskId());
            alarm.setInterfaceId(config.getId());
            alarm.setSourceCode(DataAlarmType.INTERFACE_SYNC.getSource().getValue());
            alarm.setEventType(DataAlarmType.INTERFACE_SYNC.name());
            alarm.setAlarmLevel(DataAlarmType.INTERFACE_SYNC.getLevel());
            alarm.setTriggerCount(0);
            pending.put(key, alarm);
        }
        alarm.setTriggerCount(alarm.getTriggerCount() + 1);
        alarm.setLastTriggerTime(LocalDateTime.now());
        String detail = "接口：" + config.getInterfaceName() + "（ID：" + config.getId() + "）；监听地址："
                + config.getHost() + ":" + config.getPort() + "；配置已保存，运行状态未能同步；原因：" + cause;
        alarm.setAlarmMsg(detail.length() <= 500 ? detail : detail.substring(0, 500));
        alarm.setAlarmDetail(detail);
    }
    /** 明确同步成功后恢复当前接口的同步告警，不修改人工处理状态。 */
    public synchronized void recover(CollectInterfaceConfig config) {
        String key = DataAlarmKey.of(config.getTaskId(), DataAlarmType.INTERFACE_SYNC, config.getId(), null, null);
        DmAlarmEvent alarm = pending.get(key);
        if (alarm == null) {
            if (pending.size() >= 1000) { return; }
            alarm = new DmAlarmEvent(); alarm.setDedupKey(key); alarm.setTriggerCount(0); pending.put(key, alarm);
        }
        alarm.setRecoverTime(LocalDateTime.now());
    }
    @Scheduled(fixedDelay = 5000L)
    public void flush() {
        synchronized (this) {
            if (retryBatch == null) {
                retryBatch = new ArrayList<DmAlarmEvent>(pending.values());
                pending.clear();
            }
        }
        if (retryBatch.isEmpty()) { retryBatch = null; return; }
        try { writer.write(retryBatch); retryBatch = null; }
        catch (Exception exception) { LOGGER.error("采集同步告警写入失败，保留本批次重试", exception); }
    }
}
