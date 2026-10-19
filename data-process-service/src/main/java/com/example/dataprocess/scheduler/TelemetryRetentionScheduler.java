package com.example.dataprocess.scheduler;

import com.example.common.retention.DataRetentionPolicy;
import com.example.dataprocess.mapper.IoTDBRetentionMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.iotdb.session.pool.SessionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 独立于采集线程清理过期遥测数据，未配置或关闭IoTDB时不执行。 */
@Component
@ConditionalOnProperty(prefix = "iotdb", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryRetentionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryRetentionScheduler.class);
    private final DataRetentionPolicy policy;
    private final SessionPool sessionPool;
    private final IoTDBRetentionMapper mapper;

    public TelemetryRetentionScheduler(DataRetentionPolicy policy,
            SessionPool sessionPool, IoTDBRetentionMapper mapper) {
        this.policy = policy;
        this.sessionPool = sessionPool;
        this.mapper = mapper;
    }

    /** 不设置持续生效的TTL，留空配置并重新加载服务后即可停止清理。 */
    @Scheduled(cron = "0 15 * * * *")
    public void clean() {
        // 第一步：读取可选保留天数；未配置时不连接IoTDB、不执行删除。
        Integer days = policy.getTelemetryDays();
        if (days == null) { return; }
        try {
            // 第二步：按统一毫秒精度删除截止时间之前的参数和全部整帧测点。
            long before = Instant.now().minus(days, ChronoUnit.DAYS).toEpochMilli();
            if (before <= 0) { return; }
            sessionPool.executeNonQueryStatement(mapper.deleteBefore(before));
            LOGGER.info("遥测过期数据清理完成，保留天数：{}，截止时间戳：{}", days, before);
        } catch (Exception exception) {
            // 第三步：清理失败只记录中文日志，下次任务重试，不中断接收。
            LOGGER.error("遥测过期数据清理失败，下轮继续重试", exception);
        }
    }
}
