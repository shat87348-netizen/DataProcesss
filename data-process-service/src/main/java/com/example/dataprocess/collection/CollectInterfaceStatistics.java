package com.example.dataprocess.collection;

import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.entity.CollectInterfaceStatisticsRecord;
import com.example.dataprocess.entity.DataProcessingStatisticsRecord;
import com.example.dataprocess.service.InterfaceStatisticsWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/** 采集线程和处理线程共用的秒级Redis、五秒数据库统计组件。 */
@Component
public class CollectInterfaceStatistics {

    /** 五秒统计窗口长度。 */
    private static final long WINDOW_SECONDS = 5L;
    /** 采集实时指标Redis键前缀。 */
    private static final String COLLECTION_CURRENT_PREFIX =
            "dashboard:collection:current:";
    /** 处理实时指标Redis键前缀。 */
    private static final String PROCESSING_CURRENT_PREFIX =
            "dashboard:processing:current:";
    /** 实时指标缓存保留秒数。 */
    private static final long REALTIME_TTL_SECONDS = 5L;
    /** 等待数据库落库的统计窗口键前缀。 */
    private static final String PENDING_BUCKET_PREFIX =
            "dashboard:statistics:bucket:";
    /** 等待数据库落库的统计窗口索引。 */
    private static final String PENDING_BUCKET_INDEX =
            "dashboard:statistics:pending-buckets";
    /** 统计窗口缓存保留时间。 */
    private static final long BUCKET_TTL_DAYS = 2L;
    /** 实时采样时间格式。 */
    private static final DateTimeFormatter SAMPLE_TIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    /** 统计组件日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CollectInterfaceStatistics.class);
    /** 原子累加五项指标、登记窗口并设置过期时间的Redis脚本。 */
    private static final DefaultRedisScript<Long> INCREMENT_BUCKET_SCRIPT =
            new DefaultRedisScript<Long>(
                    "for i = 1, 10, 2 do "
                            + "local amount = tonumber(ARGV[i + 1]); "
                            + "if amount > 0 then redis.call('HINCRBY', KEYS[1], ARGV[i], amount); end; "
                            + "end; "
                            + "redis.call('SADD', KEYS[2], KEYS[1]); "
                            + "redis.call('EXPIRE', KEYS[1], ARGV[11]); "
                            + "return 1;",
                    Long.class);

    /** 各任务和接口尚未生成秒级快照的内存计数器。 */
    private final ConcurrentMap<StatisticsKey, InterfaceCounter> counters =
            new ConcurrentHashMap<StatisticsKey, InterfaceCounter>();
    /** Redis字符串访问组件。 */
    private final StringRedisTemplate redisTemplate;
    /** 实时指标序列化组件。 */
    private final ObjectMapper objectMapper;
    /** 五秒统计事务写入组件。 */
    private final InterfaceStatisticsWriter writer;

    public CollectInterfaceStatistics(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            InterfaceStatisticsWriter writer) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.writer = writer;
    }

    /** 记录传输层实际接收的字节数，用于计算接口速率。 */
    public void recordReceivedBytes(
            CollectInterfaceRuntimeConfig config,
            long byteCount) {
        if (byteCount > 0) {
            counter(config).receivedBytes.add(byteCount);
        }
    }

    /** 记录一个完整PDXP帧或一个完整FEP文件。 */
    public void recordCollection(CollectInterfaceRuntimeConfig config) {
        counter(config).collectionCount.increment();
    }

    /** 记录一个成功完成全部后续步骤的帧或文件。 */
    public void recordProcessing(String taskId, Long interfaceId) {
        counter(taskId, interfaceId).processingCount.increment();
    }

    /** 记录业务处理完成且两个结果任务提交成功的PDXP帧，不代表异步入库或发送成功。 */
    public void recordFrameProcessing(String taskId, Long interfaceId) {
        InterfaceCounter counter = counter(taskId, interfaceId);
        // 第一步：累计历史处理量，继续写入五秒数据库统计窗口。
        counter.processingCount.increment();
        // 第二步：单独累计帧级数量，避免FEP文件混入实时条数速率。
        counter.realtimeProcessingCount.increment();
    }

    /** 记录本地处理时被后值覆盖的重复遥测参数数量。 */
    public void recordDuplicate(
            CollectInterfaceRuntimeConfig config,
            long duplicateCount) {
        if (duplicateCount > 0) {
            counter(config).duplicateCount.add(duplicateCount);
        }
    }

    /** 记录校准处理判定出的野值参数数量。 */
    public void recordWildValue(
            CollectInterfaceRuntimeConfig config,
            long wildValueCount) {
        if (wildValueCount > 0) {
            counter(config).wildValueCount.add(wildValueCount);
        }
    }

    /** 每秒生成实时速率，并把当前秒增量累计到Redis五秒窗口。 */
    @Scheduled(fixedRateString = "${data-processing.statistics-sample-millis:1000}")
    public void snapshotSecond() {
        LocalDateTime sampleTime = LocalDateTime.now();
        long bucketEpoch = completedSecondBucket(Instant.now().getEpochSecond());
        String bucketKey = PENDING_BUCKET_PREFIX + bucketEpoch;
        for (Map.Entry<StatisticsKey, InterfaceCounter> entry : counters.entrySet()) {
            StatisticsKey key = entry.getKey();
            // 使用单调时钟记录接口实际统计窗口，避免调度延迟和系统时间调整影响速率。
            long sampleNanos = System.nanoTime();
            double elapsedSeconds = Math.max(1L,
                    sampleNanos - entry.getValue().lastSampleNanos) / 1_000_000_000D;
            CounterSnapshot snapshot = entry.getValue().drain();
            // 第一步：增量先原子写入Redis窗口，失败时完整恢复内存计数。
            if (snapshot.hasAnyValue()) {
                try {
                    incrementBucket(bucketKey, key, snapshot);
                } catch (Exception exception) {
                    entry.getValue().restore(snapshot);
                    LOGGER.error("接口秒级增量写入Redis失败，任务编号：{}，接口编号：{}",
                            key.taskId, key.interfaceId, exception);
                    continue;
                }
            }
            // 增量写入成功后推进接口窗口；失败恢复的帧继续使用原窗口计算速率。
            entry.getValue().lastSampleNanos = sampleNanos;
            try {
                // 第二步：实时采集速率始终刷新，空闲接口下一秒自然归零。
                writeCollectionRealtime(key, snapshot.receivedBytes, sampleTime);
                // 第三步：实时处理速率始终刷新，空闲接口下一秒自然归零。
                writeProcessingRealtime(
                        key, snapshot.realtimeProcessingCount / elapsedSeconds, sampleTime);
            } catch (Exception exception) {
                // 增量已经可靠进入窗口，实时快照失败不能再次恢复以免重复累计。
                LOGGER.error("接口实时统计写入Redis失败，任务编号：{}，接口编号：{}",
                        key.taskId, key.interfaceId, exception);
            }
        }
    }

    /** 每五秒把Redis中已经结束的窗口写入数据库。 */
    @Scheduled(fixedDelayString = "${data-processing.statistics-flush-millis:5000}")
    public void flushClosedBuckets() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(
                    PENDING_BUCKET_INDEX);
            if (members == null || members.isEmpty()) {
                return;
            }
            long currentBucket = floorWindow(Instant.now().getEpochSecond());
            List<String> bucketKeys = new ArrayList<String>(members);
            bucketKeys.sort(Comparator.comparingLong(this::bucketEpoch));
            for (String bucketKey : bucketKeys) {
                if (bucketEpoch(bucketKey) < currentBucket) {
                    flushBucket(bucketKey);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("五秒接口统计写入数据库失败，缓存数据将在下次重试", exception);
        }
    }

    /** 服务关闭前先保存最后一秒增量，再尝试写入已经结束的窗口。 */
    public void flush() {
        snapshotSecond();
        flushClosedBuckets();
    }

    /** 把一个已经关闭的Redis窗口转换为两张统计表记录。 */
    private void flushBucket(String bucketKey) throws Exception {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(bucketKey);
        if (values == null || values.isEmpty()) {
            removeBucket(bucketKey);
            return;
        }
        long epoch = bucketEpoch(bucketKey);
        LocalDateTime statisticsTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        Map<StatisticsKey, BucketValues> grouped = group(values);
        List<CollectInterfaceStatisticsRecord> collectionRecords =
                new ArrayList<CollectInterfaceStatisticsRecord>();
        List<DataProcessingStatisticsRecord> processingRecords =
                new ArrayList<DataProcessingStatisticsRecord>();
        for (Map.Entry<StatisticsKey, BucketValues> entry : grouped.entrySet()) {
            StatisticsKey key = entry.getKey();
            BucketValues value = entry.getValue();
            if (value.collectionCount > 0 || value.receivedBytes > 0) {
                collectionRecords.add(collectionRecord(
                        key, statisticsTime, value));
            }
            if (value.processingCount > 0 || value.duplicateCount > 0
                    || value.wildValueCount > 0) {
                processingRecords.add(processingRecord(
                        key, statisticsTime, value));
            }
        }
        writer.write(collectionRecords, processingRecords);
        // 数据库事务成功返回后才删除Redis窗口，异常时完整保留供下次重试。
        removeBucket(bucketKey);
    }

    /** 写入一个接口最新一秒的采集速率。 */
    private void writeCollectionRealtime(
            StatisticsKey key,
            long receivedBytes,
            LocalDateTime sampleTime) throws Exception {
        Map<String, Object> metric = new LinkedHashMap<String, Object>();
        metric.put("interfaceRateKbps", round(receivedBytes / 1024D));
        metric.put("sampleTime", sampleTime.format(SAMPLE_TIME_FORMAT));
        // 第一步：更新当前任务下接口的最新采集速率。
        String redisKey = COLLECTION_CURRENT_PREFIX + key.taskId;
        redisTemplate.opsForHash().put(
                redisKey,
                interfaceField(key.interfaceId),
                objectMapper.writeValueAsString(metric));
        // 第二步：复用处理指标的五秒有效期，服务停止后自动清理采集旧速率。
        redisTemplate.expire(
                redisKey, REALTIME_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /** 写入一个接口按实际采样间隔计算的成功处理帧速率。 */
    private void writeProcessingRealtime(
            StatisticsKey key,
            double processRate,
            LocalDateTime sampleTime) throws Exception {
        Map<String, Object> metric = new LinkedHashMap<String, Object>();
        metric.put("processRate", processRate);
        metric.put("sampleTime", sampleTime.format(SAMPLE_TIME_FORMAT));
        String redisKey = PROCESSING_CURRENT_PREFIX + key.taskId;
        redisTemplate.opsForHash().put(
                redisKey,
                interfaceField(key.interfaceId),
                objectMapper.writeValueAsString(metric));
        // 实时数据短期有效，处理服务停止后由Redis自动清理旧速率。
        redisTemplate.expire(
                redisKey, REALTIME_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /** 累加一个接口在当前Redis窗口中的全部指标。 */
    private void incrementBucket(
            String bucketKey,
            StatisticsKey key,
            CounterSnapshot snapshot) {
        redisTemplate.execute(
                INCREMENT_BUCKET_SCRIPT,
                Arrays.asList(bucketKey, PENDING_BUCKET_INDEX),
                field("collectionCount", key), String.valueOf(snapshot.collectionCount),
                field("receivedBytes", key), String.valueOf(snapshot.receivedBytes),
                field("processingCount", key), String.valueOf(snapshot.processingCount),
                field("duplicateCount", key), String.valueOf(snapshot.duplicateCount),
                field("wildValueCount", key), String.valueOf(snapshot.wildValueCount),
                String.valueOf(TimeUnit.DAYS.toSeconds(BUCKET_TTL_DAYS)));
    }

    /** 按任务和接口归并Redis窗口中的不同指标。 */
    private Map<StatisticsKey, BucketValues> group(Map<Object, Object> values) {
        Map<StatisticsKey, BucketValues> result =
                new HashMap<StatisticsKey, BucketValues>();
        for (Map.Entry<Object, Object> entry : values.entrySet()) {
            String[] parts = String.valueOf(entry.getKey()).split("\\|", -1);
            if (parts.length != 3) {
                continue;
            }
            try {
                StatisticsKey key = new StatisticsKey(
                        parts[1], Long.valueOf(parts[2]));
                BucketValues bucket = result.computeIfAbsent(
                        key, ignored -> new BucketValues());
                bucket.set(parts[0], Long.parseLong(String.valueOf(entry.getValue())));
            } catch (NumberFormatException ignored) {
                // 非本组件写入的无效字段不参与统计，保留窗口中的其他有效数据。
            }
        }
        return result;
    }

    /** 构造采集统计数据库记录。 */
    private CollectInterfaceStatisticsRecord collectionRecord(
            StatisticsKey key,
            LocalDateTime statisticsTime,
            BucketValues value) {
        CollectInterfaceStatisticsRecord record =
                new CollectInterfaceStatisticsRecord();
        record.setTaskId(key.taskId);
        record.setInterfaceId(key.interfaceId);
        record.setStatisticsTime(statisticsTime);
        record.setCollectionCount(value.collectionCount);
        record.setInterfaceRate(BigDecimal.valueOf(value.receivedBytes)
                .divide(BigDecimal.valueOf(WINDOW_SECONDS), 4, RoundingMode.HALF_UP));
        return record;
    }

    /** 构造处理统计数据库记录。 */
    private DataProcessingStatisticsRecord processingRecord(
            StatisticsKey key,
            LocalDateTime statisticsTime,
            BucketValues value) {
        DataProcessingStatisticsRecord record =
                new DataProcessingStatisticsRecord();
        record.setTaskId(key.taskId);
        record.setInterfaceId(key.interfaceId);
        record.setStatisticsTime(statisticsTime);
        record.setProcessingCount(value.processingCount);
        record.setDuplicateCount(value.duplicateCount);
        record.setWildValueCount(value.wildValueCount);
        return record;
    }

    /** 删除已经成功入库的Redis窗口和索引项。 */
    private void removeBucket(String bucketKey) {
        redisTemplate.delete(bucketKey);
        redisTemplate.opsForSet().remove(PENDING_BUCKET_INDEX, bucketKey);
    }

    /** 根据接口配置取得计数器。 */
    private InterfaceCounter counter(CollectInterfaceRuntimeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("采集接口配置不能为空");
        }
        return counter(config.getTaskId(), config.getInterfaceId());
    }

    /** 根据任务和接口取得计数器。 */
    private InterfaceCounter counter(String taskId, Long interfaceId) {
        if (taskId == null || taskId.trim().isEmpty() || interfaceId == null) {
            throw new IllegalArgumentException("统计任务和采集接口编号不能为空");
        }
        StatisticsKey key = new StatisticsKey(taskId, interfaceId);
        return counters.computeIfAbsent(key, ignored -> new InterfaceCounter());
    }

    /** 取得统计窗口键中的秒级时间戳。 */
    private long bucketEpoch(String bucketKey) {
        return Long.parseLong(bucketKey.substring(PENDING_BUCKET_PREFIX.length()));
    }

    /** 把当前秒之前的完整一秒归入对应五秒窗口。 */
    private long completedSecondBucket(long epochSecond) {
        return floorWindow(Math.max(0L, epochSecond - 1L));
    }

    /** 把秒级时间戳向下对齐到五秒。 */
    private long floorWindow(long epochSecond) {
        return epochSecond / WINDOW_SECONDS * WINDOW_SECONDS;
    }

    /** 构造Redis接口字段。 */
    private String interfaceField(Long interfaceId) {
        return "interface:" + interfaceId;
    }

    /** 构造Redis窗口指标字段。 */
    private String field(String metric, StatisticsKey key) {
        return metric + "|" + key.taskId + "|" + key.interfaceId;
    }

    /** 实时速率保留两位小数。 */
    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    /** 任务和接口联合统计键。 */
    private static final class StatisticsKey {
        private final String taskId;
        private final Long interfaceId;

        private StatisticsKey(String taskId, Long interfaceId) {
            this.taskId = taskId;
            this.interfaceId = interfaceId;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) { return true; }
            if (!(value instanceof StatisticsKey)) { return false; }
            StatisticsKey other = (StatisticsKey) value;
            return taskId.equals(other.taskId)
                    && interfaceId.equals(other.interfaceId);
        }

        @Override
        public int hashCode() {
            return 31 * taskId.hashCode() + interfaceId.hashCode();
        }
    }

    /** 单个接口尚未形成秒级快照的线程安全计数器。 */
    private static final class InterfaceCounter {
        /** 当前接口统计窗口的单调时钟起点，首次统计从接口建立时开始。 */
        private volatile long lastSampleNanos = System.nanoTime();
        private final LongAdder collectionCount = new LongAdder();
        private final LongAdder receivedBytes = new LongAdder();
        private final LongAdder processingCount = new LongAdder();
        private final LongAdder realtimeProcessingCount = new LongAdder();
        private final LongAdder duplicateCount = new LongAdder();
        private final LongAdder wildValueCount = new LongAdder();

        private CounterSnapshot drain() {
            return new CounterSnapshot(
                    collectionCount.sumThenReset(),
                    receivedBytes.sumThenReset(),
                    processingCount.sumThenReset(),
                    realtimeProcessingCount.sumThenReset(),
                    duplicateCount.sumThenReset(),
                    wildValueCount.sumThenReset());
        }

        private void restore(CounterSnapshot value) {
            collectionCount.add(value.collectionCount);
            receivedBytes.add(value.receivedBytes);
            processingCount.add(value.processingCount);
            realtimeProcessingCount.add(value.realtimeProcessingCount);
            duplicateCount.add(value.duplicateCount);
            wildValueCount.add(value.wildValueCount);
        }
    }

    /** 从内存计数器原子取出的单秒增量。 */
    private static final class CounterSnapshot {
        private final long collectionCount;
        private final long receivedBytes;
        private final long processingCount;
        private final long realtimeProcessingCount;
        private final long duplicateCount;
        private final long wildValueCount;

        private CounterSnapshot(
                long collectionCount,
                long receivedBytes,
                long processingCount,
                long realtimeProcessingCount,
                long duplicateCount,
                long wildValueCount) {
            this.collectionCount = collectionCount;
            this.receivedBytes = receivedBytes;
            this.processingCount = processingCount;
            this.realtimeProcessingCount = realtimeProcessingCount;
            this.duplicateCount = duplicateCount;
            this.wildValueCount = wildValueCount;
        }

        private boolean hasAnyValue() {
            return collectionCount > 0 || receivedBytes > 0
                    || hasProcessingActivity();
        }

        private boolean hasProcessingActivity() {
            return processingCount > 0 || duplicateCount > 0
                    || wildValueCount > 0;
        }
    }

    /** 从Redis窗口解析出的单接口统计值。 */
    private static final class BucketValues {
        private long collectionCount;
        private long receivedBytes;
        private long processingCount;
        private long duplicateCount;
        private long wildValueCount;

        private void set(String metric, long value) {
            if ("collectionCount".equals(metric)) { collectionCount = value; }
            else if ("receivedBytes".equals(metric)) { receivedBytes = value; }
            else if ("processingCount".equals(metric)) { processingCount = value; }
            else if ("duplicateCount".equals(metric)) { duplicateCount = value; }
            else if ("wildValueCount".equals(metric)) { wildValueCount = value; }
        }
    }
}
