package com.example.dataprocess.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 定时上报处理服务存活时间，多实例共用时表示至少一个实例存活。 */
@Component
public class ProcessHeartbeatReporter {

    /** 与管理服务读取逻辑保持一致的心跳键。 */
    private static final String HEARTBEAT_KEY = "system:monitor:process-heartbeat";
    /** 心跳写入日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHeartbeatReporter.class);
    /** 复用已有Redis访问组件。 */
    private final StringRedisTemplate redisTemplate;
    /** 心跳键过期时长，单位毫秒。 */
    private final long expiryMillis;

    /** 注入缓存组件并校验心跳周期，防止正常运行时提前过期。 */
    public ProcessHeartbeatReporter(
            StringRedisTemplate redisTemplate,
            @Value("${system-monitor.process-heartbeat-interval-millis:5000}") long intervalMillis,
            @Value("${system-monitor.process-heartbeat-expiry-millis:30000}") long expiryMillis) {
        // 第一步：校验写入间隔与过期时间的大小关系。
        if (intervalMillis <= 0 || expiryMillis <= intervalMillis) {
            throw new IllegalArgumentException("处理服务心跳间隔必须大于零，过期时间必须大于心跳间隔");
        }
        // 第二步：保存复用组件和过期配置。
        this.redisTemplate = redisTemplate;
        this.expiryMillis = expiryMillis;
    }

    /** 启动后定时写入心跳；失败后由下一轮继续重试。 */
    @Scheduled(fixedRateString = "${system-monitor.process-heartbeat-interval-millis:5000}")
    public void reportHeartbeat() {
        try {
            // 将毫秒时间戳和过期时间在一次Redis操作中原子写入。
            redisTemplate.opsForValue().set(
                    HEARTBEAT_KEY, Long.toString(System.currentTimeMillis()),
                    expiryMillis, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            // 心跳失败仅记录日志，不向处理业务传播异常。
            LOGGER.warn("处理服务心跳写入失败，将在下一轮重试", exception);
        }
    }
}
