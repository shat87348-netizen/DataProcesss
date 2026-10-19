package com.example.dataprocess.alarm;

import com.example.common.alarm.DataAlarmRecord;
import com.example.common.enums.DataAlarmType;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.mapper.DataAlarmRecordMapper;
import com.example.dataprocess.service.impl.DataAlarmPersistenceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** 采集线程只提交内存告警，由定时任务合并批量写库。 */
@Component
public class DataAlarmRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataAlarmRecorder.class);
    /** 限制异常风暴的内存占用，数据库故障时保留待重试批次。 */
    private static final int MAX_PENDING = 10000;
    private final Map<String, DataAlarmRecord> pending = new LinkedHashMap<String, DataAlarmRecord>();
    /** 标记已知活动参数和已检查初始恢复的参数，正常帧不反复写恢复记录。 */
    private final Set<String> observed = new HashSet<String>();
    private final Set<String> active = new HashSet<String>();
    private final DataAlarmPersistenceWriter writer;
    private final DataAlarmRecordMapper mapper;
    private List<DataAlarmRecord> retryBatch;
    private long lastOverflowWarning;
    public DataAlarmRecorder(DataAlarmPersistenceWriter writer, DataAlarmRecordMapper mapper) {
        this.writer = writer;
        this.mapper = mapper;
    }
    /** 接口级异常统一包含接口身份、网络地址和协议配置。 */
    public void record(CollectInterfaceRuntimeConfig config, DataAlarmType type, String cause) {
        record(config, type, null, null, null, null, cause);
    }
    /** 参数级异常保留名称、代号、校准通道和规则。 */
    public synchronized void record(CollectInterfaceRuntimeConfig config, DataAlarmType type,
            String symbol, String parameterName, Long channelId, String rule, String cause) {
        if (config == null) { return; }
        LocalDateTime now = LocalDateTime.now();
        String key = key(config, type, symbol, channelId);
        DataAlarmRecord record = pending.get(key);
        if (record != null && record.getTriggerCount() > 0 && record.getRecoverTime() == null
                && record.getAlarmTime().isAfter(now.minusSeconds(60))) {
            // 内存先合并高频异常，只保留最新详情和完整次数。
            record.setTriggerCount(record.getTriggerCount() + 1);
            record.setLastTriggerTime(now);
        } else {
            if (!makeRoom(key)) { return; }
            // 已恢复或超过窗口的旧记录必须保留，不能覆盖未落库次数。
            if (record != null) {
                pending.remove(key);
                pending.put(key + "|" + java.util.UUID.randomUUID(), record);
            }
            record = new DataAlarmRecord();
            record.setTaskId(config.getTaskId());
            record.setInterfaceId(config.getInterfaceId());
            record.setSourceCode(type.getSource().getValue());
            record.setEventType(type.name());
            record.setAlarmLevel(type.getLevel());
            record.setTmSymbol(symbol);
            record.setParameterName(parameterName);
            record.setCalibChannelId(channelId);
            record.setTriggerRule(limit(rule, 200));
            record.setAlarmTime(now);
            record.setLastTriggerTime(now);
            record.setTriggerCount(1);
            record.setDedupKey(key);
            pending.put(key, record);
        }
        String detail = "事件：" + type.getLabel() + "；试验任务：" + value(config.getTaskId())
                + "；采集接口：" + value(config.getInterfaceName()) + "（ID：" + config.getInterfaceId() + "）"
                + "；监听地址：" + value(config.getHost()) + ":" + config.getPort()
                + "；传输方式：" + transport(config.getTransferType())
                + "；传输协议：" + protocol(config.getTransferProtocol())
                + "；协议配置ID：" + config.getProtocolConfigId()
                + (symbol == null ? "" : "；遥测参数：" + value(parameterName) + "（代号：" + symbol + "）")
                + (channelId == null ? "" : "；校准通道ID：" + channelId)
                + (rule == null ? "" : "；判定规则：" + rule)
                + "；原因及影响：" + value(cause);
        // 列表摘要优先保留接口、参数和原因，完整网络与规则信息留在详情中。
        record.setAlarmMsg(limit(type.getLabel() + "；接口：" + value(config.getInterfaceName())
                + "（ID：" + config.getInterfaceId() + "）"
                + (symbol == null ? "" : "；参数：" + value(parameterName) + "（" + symbol + "）")
                + "；" + value(cause), 500));
        record.setAlarmDetail(limit(detail, 10000));
        active.add(key);
        if (observed.size() >= MAX_PENDING * 10) { observed.clear(); active.clear(); }
        observed.add(key);
    }
    /** 参数恢复只维护恢复时间，不代替人工处理告警。 */
    public synchronized void recover(CollectInterfaceRuntimeConfig config, DataAlarmType type,
            String symbol, Long channelId) {
        String key = key(config, type, symbol, channelId);
        if (!active.remove(key) && observed.contains(key)) { return; }
        if (!makeRoom(key)) { return; }
        if (observed.size() >= MAX_PENDING * 10) { observed.clear(); active.clear(); }
        observed.add(key);
        DataAlarmRecord record = pending.get(key);
        if (record == null) {
            record = new DataAlarmRecord();
            record.setDedupKey(key);
            record.setTriggerCount(0);
            pending.put(key, record);
        }
        record.setRecoverTime(LocalDateTime.now());
    }
    /** 后台文件重试只在后台线程查询接口名称，不增加采集线程查询。 */
    public void recordForInterface(Long interfaceId, DataAlarmType type, String detail) {
        try {
            record(mapper.findInterface(interfaceId), type, detail);
        } catch (Exception exception) {
            LOGGER.error("告警接口上下文查询失败，接口ID：{}，原始异常：{}", interfaceId, detail);
        }
    }
    /** 每五秒最多处理五百个不同对象，数据库失败的批次下轮原样重试。 */
    @Scheduled(fixedDelay = 5000L)
    public void flush() {
        synchronized (this) {
            if (retryBatch == null) {
                retryBatch = new ArrayList<DataAlarmRecord>();
                java.util.Iterator<DataAlarmRecord> iterator = pending.values().iterator();
                while (iterator.hasNext() && retryBatch.size() < 500) {
                    retryBatch.add(iterator.next());
                    iterator.remove();
                }
            }
            if (retryBatch.isEmpty()) {
                retryBatch = null;
                return;
            }
        }
        try {
            // 持久化使用独立组件的事务代理，不占用接收线程所需的内存锁。
            writer.write(retryBatch);
            retryBatch = null;
        } catch (Exception exception) {
            // 告警入库失败只写服务日志，不能再次生成告警造成递归风暴。
            LOGGER.error("数据告警批量入库失败，本批次保留并等待重试，记录数：{}",
                    retryBatch.size(), exception);
        }
    }
    /** 满容量时降级为服务日志并限频，保证采集不会等待数据库。 */
    private boolean makeRoom(String key) {
        if (pending.containsKey(key) || pending.size() < MAX_PENDING) { return true; }
        long now = System.currentTimeMillis();
        if (now - lastOverflowWarning > 60000L) {
            lastOverflowWarning = now;
            LOGGER.error("数据告警待写队列已满，新告警暂未入库，请检查数据库连接");
        }
        return false;
    }
    /** 不同任务、类型、接口、参数和校准通道使用不同合并键。 */
    private String key(CollectInterfaceRuntimeConfig config, DataAlarmType type, String symbol, Long channelId) {
        return com.example.common.alarm.DataAlarmKey.of(config.getTaskId(), type, config.getInterfaceId(), symbol, channelId);
    }
    private String value(String value) { return value == null ? "未提供" : value; }
    private String limit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
    /** 使用中文协议说明，未知编码保留原值供排查。 */
    private String transport(Integer code) {
        return Integer.valueOf(1).equals(code) ? "UDP" : Integer.valueOf(2).equals(code) ? "TCP" : "未识别（" + code + "）";
    }
    private String protocol(Integer code) {
        if (Integer.valueOf(1).equals(code)) { return "JSON"; }
        if (Integer.valueOf(2).equals(code)) { return "PDXP"; }
        if (Integer.valueOf(3).equals(code)) { return "FEP"; }
        return "未识别（" + code + "）";
    }
}
