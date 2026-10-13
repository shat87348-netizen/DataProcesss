package com.example.dataprocess.service.impl;

import com.example.common.calibration.CalibrationRedisKeys;
import com.example.common.calibration.CalibrationRecordDetailCache;
import com.example.dataprocess.service.CalibrationPersistenceWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 按配置周期将缓存中的校准野值详情批量写入数据库。 */
@Component
public class CalibrationPersistenceScheduler {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.service.DataAlarmRecorder alarmRecorder;



    /** 校准落库日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CalibrationPersistenceScheduler.class);
    /** 字符串缓存访问组件。 */
    private final StringRedisTemplate redisTemplate;
    /** 野值详情序列化组件。 */
    private final ObjectMapper objectMapper;
    /** 事务写入组件。 */
    private final CalibrationPersistenceWriter writer;
    /** 单次最多写入的野值详情数量。 */
    private final int batchSize;

    /** 注入定时落库所需组件。 */
    public CalibrationPersistenceScheduler(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CalibrationPersistenceWriter writer,
            @Value("${calibration.persistence-batch-size:500}") int batchSize) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.writer = writer;
        this.batchSize = batchSize;
    }

    /** 默认每五秒取出一个批次，具体周期由配置文件覆盖。 */
    @Scheduled(fixedDelayString = "${calibration.persistence-interval-millis:5000}")
    public void flush() {
        List<String> jsonValues = popBatch();
        if (jsonValues.isEmpty()) {
            return;
        }
        try {
            List<CalibrationRecordDetailCache> details =
                    new ArrayList<CalibrationRecordDetailCache>(
                    jsonValues.size());
            for (String json : jsonValues) {
                details.add(objectMapper.readValue(
                        json, CalibrationRecordDetailCache.class));
            }
            writer.write(details);
        } catch (Exception exception) {
            // 数据库或反序列化失败时恢复原队列顺序，等待下次定时任务重试。
            restore(jsonValues);
            if (alarmRecorder != null) {
                // 每个接口最多产生一条批次告警，原野值明细仍按原队列重试。
                java.util.Set<Long> interfaces = new java.util.HashSet<Long>();
                for (String json : jsonValues) {
                    try {
                        CalibrationRecordDetailCache detail = objectMapper.readValue(json, CalibrationRecordDetailCache.class);
                        if (interfaces.add(detail.getInterfaceId())) {
                            alarmRecorder.recordForInterface(detail.getInterfaceId(), com.example.common.enums.DataAlarmType.CALIBRATION_PERSIST,
                                    "本批次野值明细入库失败，已恢复队列等待重试；数量：" + jsonValues.size() + "；原因：" + exception.getMessage());
                        }
                    } catch (Exception ignored) {
                        // 无法解析的上下文保留服务日志，不虚构参数或接口。
                    }
                }
            }
            LOGGER.error("校准野值详情批量写入数据库失败，本批次已恢复到缓存队列", exception);
        }
    }

    /** 从队首取出一个受配置限制的批次。 */
    private List<String> popBatch() {
        List<String> result = new ArrayList<String>(batchSize);
        try {
            for (int index = 0; index < batchSize; index++) {
                String value = redisTemplate.opsForList().leftPop(
                        CalibrationRedisKeys.PENDING_QUEUE);
                if (value == null) {
                    break;
                }
                result.add(value);
            }
        } catch (Exception exception) {
            restore(result);
            LOGGER.error("读取待落库校准野值详情失败", exception);
            return Collections.emptyList();
        }
        return result;
    }

    /** 从后向前压回队首，恢复原来的消费顺序。 */
    private void restore(List<String> jsonValues) {
        try {
            for (int index = jsonValues.size() - 1; index >= 0; index--) {
                redisTemplate.opsForList().leftPush(
                        CalibrationRedisKeys.PENDING_QUEUE, jsonValues.get(index));
            }
        } catch (Exception exception) {
            LOGGER.error("恢复待落库校准野值详情失败", exception);
        }
    }
}
