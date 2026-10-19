package com.example.dataprocess.scheduler;

import com.example.common.calibration.CalibrationCountCache;
import com.example.common.calibration.CalibrationRedisKeys;
import com.example.common.calibration.CalibrationRecordDetailCache;
import com.example.dataprocess.service.CalibrationPersistenceWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按配置周期将校准数量及野值详情在同一事务内批量写入数据库。 */
@Component
public class CalibrationPersistenceScheduler {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;



    /** 校准落库日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CalibrationPersistenceScheduler.class);
    /** 字符串缓存访问组件。 */
    private final StringRedisTemplate redisTemplate;
    /** 校准数量及野值详情序列化组件。 */
    private final ObjectMapper objectMapper;
    /** 事务写入组件。 */
    private final CalibrationPersistenceWriter writer;
    /** 每个队列单次最多取出的数量增量或野值详情条数。 */
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
        // 第一步：分别取出两个队列的批次，纯正常参数也需要累加校准数量。
        List<String> detailJsonValues = popBatch(CalibrationRedisKeys.PENDING_QUEUE);
        List<String> countJsonValues = popBatch(CalibrationRedisKeys.PENDING_COUNT_QUEUE);
        if (detailJsonValues.isEmpty() && countJsonValues.isEmpty()) {
            return;
        }
        try {
            // 第二步：还原野值详情及通道数量增量，交给同一个事务批量写入。
            List<CalibrationRecordDetailCache> details =
                    new ArrayList<CalibrationRecordDetailCache>(detailJsonValues.size());
            for (String json : detailJsonValues) {
                details.add(objectMapper.readValue(
                        json, CalibrationRecordDetailCache.class));
            }
            List<CalibrationCountCache> calibrationCounts =
                    new ArrayList<CalibrationCountCache>(countJsonValues.size());
            for (String json : countJsonValues) {
                calibrationCounts.add(objectMapper.readValue(json, CalibrationCountCache.class));
            }
            writer.write(details, calibrationCounts);
        } catch (Exception exception) {
            // 第三步：事务或反序列化失败后恢复两个队列，避免重试时重复累加数量。
            restore(CalibrationRedisKeys.PENDING_QUEUE, detailJsonValues);
            restore(CalibrationRedisKeys.PENDING_COUNT_QUEUE, countJsonValues);
            recordFailure(detailJsonValues, countJsonValues, exception);
            LOGGER.error("校准数量及野值详情批量入库失败，已尝试恢复缓存队列等待重试", exception);
        }
    }

    /** 对本批次涉及的每个采集接口最多记录一条校准落库告警。 */
    private void recordFailure(
            List<String> detailJsonValues,
            List<String> countJsonValues,
            Exception exception) {
        // 第一步：未配置告警组件时只保留服务日志。
        if (alarmRecorder == null) {
            return;
        }
        // 第二步：合并两个队列的接口上下文，数量增量失败也能定位采集接口。
        Set<Long> interfaces = new HashSet<Long>();
        List<String> jsonValues = new ArrayList<String>(detailJsonValues);
        jsonValues.addAll(countJsonValues);
        for (String json : jsonValues) {
            try {
                JsonNode context = objectMapper.readTree(json);
                if (context.hasNonNull("interfaceId")
                        && context.path("interfaceId").canConvertToLong()) {
                    long interfaceId = context.path("interfaceId").asLong();
                    // 第三步：同一接口即使同时有数量及野值详情，也只记录一次告警。
                    if (interfaces.add(interfaceId)) {
                        alarmRecorder.recordForInterface(interfaceId,
                                com.example.common.enums.DataAlarmType.CALIBRATION_PERSIST,
                                "校准数量及野值详情入库失败，已尝试恢复队列等待重试；"
                                        + "数量增量条数：" + countJsonValues.size()
                                        + "；野值详情条数：" + detailJsonValues.size()
                                        + "；原因：" + exception.getMessage());
                    }
                }
            } catch (Exception ignored) {
                // 无法解析的上下文保留服务日志，不虚构参数或接口。
            }
        }
    }

    /** 从队首取出一个受配置限制的批次。 */
    private List<String> popBatch(String queueKey) {
        // 第一步：逐条读取指定队列，限制每次数据库事务处理的批次大小。
        List<String> result = new ArrayList<String>(batchSize);
        try {
            for (int index = 0; index < batchSize; index++) {
                String value = redisTemplate.opsForList().leftPop(queueKey);
                if (value == null) {
                    break;
                }
                result.add(value);
            }
        } catch (Exception exception) {
            // 第二步：读取中断时恢复已经取出的数据，等待下一轮重新处理。
            restore(queueKey, result);
            LOGGER.error("读取校准待落库队列失败，缓存键：{}", queueKey, exception);
            return Collections.emptyList();
        }
        return result;
    }

    /** 从后向前压回队首，恢复原来的消费顺序。 */
    private void restore(String queueKey, List<String> jsonValues) {
        try {
            // 从后向前压回指定队列，保持原有数据顺序。
            for (int index = jsonValues.size() - 1; index >= 0; index--) {
                redisTemplate.opsForList().leftPush(queueKey, jsonValues.get(index));
            }
        } catch (Exception exception) {
            LOGGER.error("恢复校准待落库队列失败，缓存键：{}", queueKey, exception);
        }
    }
}
