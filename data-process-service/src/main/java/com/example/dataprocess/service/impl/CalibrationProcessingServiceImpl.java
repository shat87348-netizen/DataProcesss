package com.example.dataprocess.service.impl;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.Telemetry;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import com.example.common.calibration.CalibrationCountCache;
import com.example.common.calibration.CalibrationRedisKeys;
import com.example.common.calibration.CalibrationRecordDetailCache;
import com.example.common.calibration.CalibrationSample;
import com.example.dataprocess.entity.CalibrationChannelRuntimeConfig;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.enums.CalibrationDetectMethod;
import com.example.dataprocess.service.CalibrationProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用校准通道规则检测遥测野值，并把样本写入缓存。 */
@Service
public class CalibrationProcessingServiceImpl implements CalibrationProcessingService {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;

    /** 提交异常上下文，采集线程不直接写数据库。 */
    private void alarm(com.example.dataprocess.entity.CollectInterfaceRuntimeConfig config,
            com.example.common.enums.DataAlarmType type, String detail) {
        if (alarmRecorder != null) { alarmRecorder.record(config, type, detail); }
    }


    /** 校准处理日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CalibrationProcessingServiceImpl.class);
    /** 解析两个带符号边界及其中间的范围分隔符。 */
    private static final Pattern RANGE = Pattern.compile(
            "^\\s*([-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*"
                    + "(?:~|～|至|—|–|-)\\s*"
                    + "([-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)).*$");
    /** 数据库中的否值。 */
    private static final int NO = 0;
    /** 数据库中的是值。 */
    private static final int YES = 1;

    /** 字符串缓存访问组件。 */
    private final StringRedisTemplate redisTemplate;
    /** 样本序列化组件。 */
    private final ObjectMapper objectMapper;
    /** 有效样本缓存天数。 */
    private final long cacheDays;

    /** 注入校准处理依赖和可配置的缓存策略。 */
    public CalibrationProcessingServiceImpl(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${calibration.effective-cache-days:7}") long cacheDays) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheDays = cacheDays;
    }

    /** 使用接口预加载的公式Map为每个遥测参数独立执行检测。 */
    @Override
    public TelemetryMessage process(
            CollectInterfaceRuntimeConfig interfaceConfig,
            TelemetryMessage message) {
        if (interfaceConfig == null || message == null) {
            return message;
        }
        // 参数值范围检查未开启时，不执行野值检测、样本缓存和详情记录。
        if (!Integer.valueOf(YES).equals(
                interfaceConfig.getParameterRangeCheckEnabled())) {
            return message;
        }
        Map<String, CalibrationChannelRuntimeConfig> configs =
                interfaceConfig.getCalibrationConfigMap();

        // 第一步：按校准通道合并本条消息的参数数量，避免每个正常参数单独入队。
        Map<Long, Long> calibrationCounts = new LinkedHashMap<Long, Long>();
        // 第二步：清空遥测映射，让每个参数使用自身公式匹配到的配置更新野值标识。
        TelemetryMessage.Builder result = message.toBuilder().clearTelemetries();
        try {
            for (Map.Entry<String, Telemetry> entry : message.getTelemetriesMap().entrySet()) {
                Telemetry telemetry = entry.getValue();
                CalibrationChannelRuntimeConfig config = configs.get(
                        trimmed(telemetry.getCalibrationFormula()));
                Telemetry processed;
                try {
                    processed = config == null
                            ? telemetry
                            : processTelemetry(interfaceConfig, config, message, telemetry);
                    // 仅累加已匹配启用通道且完成校准处理的参数，正常值和野值均计数。
                    if (config != null) {
                        calibrationCounts.put(config.getId(),
                                calibrationCounts.getOrDefault(config.getId(), 0L) + 1L);
                    }
                } catch (Exception exception) {
                    if (alarmRecorder != null) {
                        alarmRecorder.record(interfaceConfig,
                                com.example.common.enums.DataAlarmType.CALIBRATION_RULE,
                                telemetry.getTmSymbol(), telemetry.getTmName(),
                                config == null ? null : config.getId(),
                                telemetry.getCalibrationFormula(),
                                "校准判定失败：" + exception.getMessage());
                    }
                    throw new com.example.dataprocess.exception.RecordedDataAlarmException(
                            "校准判定失败", exception);
                }
                result.putTelemetries(entry.getKey(), processed);
            }
            return result.build();
        } finally {
            // 第三步：即使后续参数处理失败，也保留本条消息中已经完成校准的数量。
            cacheCalibrationCounts(interfaceConfig, calibrationCounts);
        }
    }

    /** 将当前消息的通道校准数量一次性写入待落库队列。 */
    private void cacheCalibrationCounts(
            CollectInterfaceRuntimeConfig interfaceConfig,
            Map<Long, Long> calibrationCounts) {
        // 第一步：没有匹配校准通道的消息无需访问数量队列。
        if (calibrationCounts.isEmpty()) {
            return;
        }
        try {
            // 第二步：按通道生成数量增量，采集线程只写缓存，不直接访问数据库。
            List<String> jsonValues = new ArrayList<String>(calibrationCounts.size());
            for (Map.Entry<Long, Long> entry : calibrationCounts.entrySet()) {
                CalibrationCountCache count = new CalibrationCountCache();
                count.setTaskId(interfaceConfig.getTaskId());
                count.setChannelId(entry.getKey());
                count.setInterfaceId(interfaceConfig.getInterfaceId());
                count.setCalibrationCount(entry.getValue());
                jsonValues.add(objectMapper.writeValueAsString(count));
            }
            // 第三步：通过一次缓存操作提交全部通道的数量，交给定时任务合并落库。
            redisTemplate.opsForList().rightPushAll(
                    CalibrationRedisKeys.PENDING_COUNT_QUEUE, jsonValues);
        } catch (Exception exception) {
            // 数量缓存失败沿用现有校准缓存告警，避免影响遥测数据处理结果。
            alarm(interfaceConfig, com.example.common.enums.DataAlarmType.CALIBRATION_CACHE,
                    "校准参数数量未进入缓存：" + exception.getMessage());
            LOGGER.error("保存校准参数数量缓存失败，试验任务：{}，采集接口：{}",
                    interfaceConfig.getTaskId(), interfaceConfig.getInterfaceId(), exception);
        }
    }

    /** 检测单个遥测参数并分别维护原始样本和有效样本。 */
    private Telemetry processTelemetry(
            CollectInterfaceRuntimeConfig interfaceConfig,
            CalibrationChannelRuntimeConfig config,
            TelemetryMessage message,
            Telemetry telemetry) {
        String taskId = interfaceConfig.getTaskId();
        String telemetryCode = telemetry.getTmSymbol();
        String effectiveKey = CalibrationRedisKeys.effectiveSamples(
                taskId, config.getCalibrationFormula(), telemetryCode);
        List<Double> baseline = readValues(interfaceConfig, effectiveKey, requiredSamples(config));
        DetectionResult detection = detect(config, baseline, telemetry.getValue());
        int cleaned = detection.outlier
                && Integer.valueOf(YES).equals(config.getAutoClean())
                ? YES : NO;
        CalibrationSample sample = sample(
                taskId, config, telemetryCode, message, telemetry,
                detection.outlier, cleaned);
        cacheEffectiveSample(interfaceConfig, config, sample, effectiveKey, baseline.size());
        if (alarmRecorder != null) {
            if (detection.outlier) {
                alarmRecorder.record(interfaceConfig, com.example.common.enums.DataAlarmType.CALIBRATION_OUTLIER,
                        telemetryCode, telemetry.getTmName(), config.getId(), config.getCalibrationFormula(), detection.detail);
            } else {
                alarmRecorder.recover(interfaceConfig, com.example.common.enums.DataAlarmType.CALIBRATION_OUTLIER,
                        telemetryCode, config.getId());
            }
        }
        if (detection.outlier) {
            cacheRecordDetail(
                    interfaceConfig, config, message, telemetry, detection.detail);
        }
        return telemetry.toBuilder().setIsWildValue(detection.outlier).build();
    }

    /** 根据配置选择检测方法。 */
    private DetectionResult detect(
            CalibrationChannelRuntimeConfig config,
            List<Double> baseline,
            double value) {
        CalibrationDetectMethod method = CalibrationDetectMethod.fromValue(
                config.getDetectMethod());
        if (method == CalibrationDetectMethod.THRESHOLD) {
            return threshold(config, value);
        }
        if (method == CalibrationDetectMethod.WRIGHT) {
            int window = positive(config.getSampleWindow(), 3);
            return baseline.size() >= window
                    ? wright(baseline, value, number(config.getSigmaValue(), 3.0D))
                    : DetectionResult.normal();
        }
        int minimum = positive(config.getMinSampleCount(), 3);
        return baseline.size() >= minimum
                ? chauvenet(
                        baseline,
                        value,
                        number(config.getChauvenetCoef(), 0.5D),
                        positive(config.getIterateCount(), 1))
                : DetectionResult.normal();
    }

    /** 使用正常范围和浮动百分比判断阈值野值。 */
    private DetectionResult threshold(
            CalibrationChannelRuntimeConfig config,
            double value) {
        List<Double> limits = numbers(config.getNormalRange());
        if (limits.size() < 2) {
            throw new IllegalArgumentException(
                    "校准通道正常范围无法解析：" + config.getNormalRange());
        }
        double lower = Math.min(limits.get(0), limits.get(1));
        double upper = Math.max(limits.get(0), limits.get(1));
        double extension = (upper - lower)
                * number(config.getFluctuationRate(), 0.0D) / 100.0D;
        double effectiveLower = lower - extension;
        double effectiveUpper = upper + extension;
        if (value < effectiveLower) {
            return DetectionResult.outlier("遥测值" + format(value)
                    + "低于正常范围下限" + format(effectiveLower)
                    + "，低于" + format(effectiveLower - value) + "，判定为野值");
        }
        if (value > effectiveUpper) {
            return DetectionResult.outlier("遥测值" + format(value)
                    + "超出正常范围上限" + format(effectiveUpper)
                    + "，超出" + format(value - effectiveUpper) + "，判定为野值");
        }
        return DetectionResult.normal();
    }

    /** 使用最近样本的均值和样本标准差执行莱特准则。 */
    private DetectionResult wright(
            List<Double> baseline,
            double value,
            double sigma) {
        Statistics statistics = statistics(baseline);
        double difference = Math.abs(value - statistics.mean);
        if (statistics.deviation > 0.0D
                && difference > sigma * statistics.deviation) {
            return DetectionResult.outlier("遥测值" + format(value)
                    + "与样本均值" + format(statistics.mean)
                    + "的偏差为" + format(difference)
                    + "，超过" + format(sigma) + "σ范围，判定为野值");
        }
        return DetectionResult.normal();
    }

    /** 逐次剔除最偏离均值的点，并判断当前值是否被肖维涅准则剔除。 */
    private DetectionResult chauvenet(
            List<Double> baseline,
            double value,
            double coefficient,
            int iterations) {
        List<Candidate> candidates = new ArrayList<Candidate>(baseline.size() + 1);
        for (Double item : baseline) {
            candidates.add(new Candidate(item, false));
        }
        candidates.add(new Candidate(value, true));
        for (int iteration = 0; iteration < iterations && candidates.size() >= 3; iteration++) {
            List<Double> values = new ArrayList<Double>(candidates.size());
            for (Candidate candidate : candidates) {
                values.add(candidate.value);
            }
            Statistics statistics = statistics(values);
            if (statistics.deviation <= 0.0D) {
                return DetectionResult.normal();
            }
            Candidate farthest = candidates.get(0);
            for (Candidate candidate : candidates) {
                if (Math.abs(candidate.value - statistics.mean)
                        > Math.abs(farthest.value - statistics.mean)) {
                    farthest = candidate;
                }
            }
            double z = Math.abs(farthest.value - statistics.mean)
                    / statistics.deviation;
            double twoTailProbability = complementaryErrorFunction(
                    z / Math.sqrt(2.0D));
            if (candidates.size() * twoTailProbability >= coefficient) {
                return DetectionResult.normal();
            }
            candidates.remove(farthest);
            if (farthest.current) {
                int sampleCount = candidates.size() + 1;
                double probabilityProduct = sampleCount * twoTailProbability;
                return DetectionResult.outlier("遥测值" + format(value)
                        + "在第" + (iteration + 1) + "次判定中偏离样本均值"
                        + format(statistics.mean) + "，标准差为"
                        + format(statistics.deviation) + "，标准化偏差为"
                        + format(z) + "，样本数为" + sampleCount
                        + "，概率乘积" + format(probabilityProduct)
                        + "小于判别系数" + format(coefficient) + "，判定为野值");
            }
        }
        return DetectionResult.normal();
    }

    /** 计算均值和样本标准差。 */
    private Statistics statistics(List<Double> values) {
        double sum = 0.0D;
        for (Double value : values) {
            sum += value;
        }
        double mean = sum / values.size();
        double squared = 0.0D;
        for (Double value : values) {
            double difference = value - mean;
            squared += difference * difference;
        }
        double deviation = values.size() > 1
                ? Math.sqrt(squared / (values.size() - 1)) : 0.0D;
        return new Statistics(mean, deviation);
    }

    /** 使用数值近似计算互补误差函数。 */
    private double complementaryErrorFunction(double value) {
        double absolute = Math.abs(value);
        double temporary = 1.0D / (1.0D + 0.5D * absolute);
        double result = temporary * Math.exp(-absolute * absolute - 1.26551223D
                + temporary * (1.00002368D + temporary * (0.37409196D
                + temporary * (0.09678418D + temporary * (-0.18628806D
                + temporary * (0.27886807D + temporary * (-1.13520398D
                + temporary * (1.48851587D + temporary * (-0.82215223D
                + temporary * 0.17087277D)))))))));
        return value >= 0.0D ? result : 2.0D - result;
    }

    /** 从有效样本缓存读取最近的统计基线。 */
    private List<Double> readValues(CollectInterfaceRuntimeConfig interfaceConfig, String key, int limit) {
        try {
            Set<String> jsonValues = redisTemplate.opsForZSet().range(
                    key, -Math.max(limit, 1), -1);
            if (jsonValues == null || jsonValues.isEmpty()) {
                return Collections.emptyList();
            }
            List<Double> result = new ArrayList<Double>(jsonValues.size());
            for (String json : jsonValues) {
                CalibrationSample item = objectMapper.readValue(
                        json, CalibrationSample.class);
                result.add(item.getValue());
            }
            return result;
        } catch (Exception exception) {
            alarm(interfaceConfig, com.example.common.enums.DataAlarmType.CALIBRATION_CACHE, "有效样本读取失败，当前检测无法取得完整基线：" + exception.getMessage());
            LOGGER.error("读取校准样本缓存失败，缓存键：{}", key, exception);
            return Collections.emptyList();
        }
    }

    /** 保存后续检测需要使用的有效样本。 */
    private void cacheEffectiveSample(
            CollectInterfaceRuntimeConfig interfaceConfig,
            CalibrationChannelRuntimeConfig config,
            CalibrationSample sample,
            String effectiveKey,
            int baselineSize) {
        try {
            // 自动清洗后的野值不进入后续算法使用的统计基线。
            if (shouldAddEffective(config, sample, baselineSize)) {
                String json = objectMapper.writeValueAsString(sample);
                redisTemplate.opsForZSet().add(effectiveKey, json, sample.getSampleTime());
                trim(effectiveKey, requiredSamples(config));
                redisTemplate.expire(effectiveKey, cacheDays, TimeUnit.DAYS);
            }
        } catch (Exception exception) {
            alarm(interfaceConfig, com.example.common.enums.DataAlarmType.CALIBRATION_CACHE, "有效样本保存失败；参数：" + sample.getTelemetryCode() + "；原因：" + exception.getMessage());
            LOGGER.error("保存校准有效样本缓存失败，校准公式：{}，遥测代号：{}",
                    sample.getCalibrationFormula(), sample.getTelemetryCode(), exception);
        }
    }

    /** 将一条野值详情写入待落库队列。 */
    private void cacheRecordDetail(
            CollectInterfaceRuntimeConfig interfaceConfig,
            CalibrationChannelRuntimeConfig config,
            TelemetryMessage message,
            Telemetry telemetry,
            String detail) {
        try {
            CalibrationRecordDetailCache record = new CalibrationRecordDetailCache();
            record.setTaskId(interfaceConfig.getTaskId());
            record.setChannelId(config.getId());
            record.setInterfaceId(interfaceConfig.getInterfaceId());
            record.setChannelName(trimmed(message.getChannelName()));
            record.setParameterName(trimmed(telemetry.getTmName()));
            record.setTmSymbol(trimmed(telemetry.getTmSymbol()));
            record.setTelemetryValue(telemetry.getValue());
            record.setDetail(detail);
            record.setCreateTime(messageTime(message));
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForList().rightPush(
                    CalibrationRedisKeys.PENDING_QUEUE, json);
        } catch (Exception exception) {
            alarm(interfaceConfig, com.example.common.enums.DataAlarmType.CALIBRATION_CACHE,
                    "校准通道ID：" + config.getId() + "；参数：" + telemetry.getTmSymbol() + "；野值详情未进入缓存：" + exception.getMessage());
            LOGGER.error("保存校准野值详情缓存失败，校准通道ID：{}，遥测代号：{}",
                    config.getId(), telemetry.getTmSymbol(), exception);
        }
    }

    /** 判断当前样本是否可加入后续检测的统计基线。 */
    private boolean shouldAddEffective(
            CalibrationChannelRuntimeConfig config,
            CalibrationSample sample,
            int baselineSize) {
        if (Integer.valueOf(YES).equals(sample.getCleaned())) {
            return false;
        }
        CalibrationDetectMethod method = CalibrationDetectMethod.fromValue(
                config.getDetectMethod());
        // 莱特准则关闭动态更新后，只建立一次固定样本窗口。
        return method != CalibrationDetectMethod.WRIGHT
                || Integer.valueOf(YES).equals(config.getDynamicUpdate())
                || baselineSize < requiredSamples(config);
    }

    /** 删除有序集合中超过保留数量的最早样本。 */
    private void trim(String key, int limit) {
        long removeEnd = -Math.max(limit, 1) - 1L;
        redisTemplate.opsForZSet().removeRange(key, 0, removeEnd);
    }

    /** 构造一条原始校准样本。 */
    private CalibrationSample sample(
            String taskId,
            CalibrationChannelRuntimeConfig config,
            String telemetryCode,
            TelemetryMessage message,
            Telemetry telemetry,
            boolean outlier,
            int cleaned) {
        CalibrationSample result = new CalibrationSample();
        result.setSampleId(UUID.randomUUID().toString());
        result.setTaskId(taskId);
        result.setChannelId(config.getId());
        result.setCalibrationFormula(config.getCalibrationFormula());
        result.setTelemetryCode(telemetryCode);
        result.setSampleTime(messageTime(message));
        result.setValue(telemetry.getValue());
        result.setOutlier(outlier ? YES : NO);
        result.setCleaned(cleaned);
        return result;
    }

    /** 返回检测方法需要的最近样本数量。 */
    private int requiredSamples(CalibrationChannelRuntimeConfig config) {
        CalibrationDetectMethod method = CalibrationDetectMethod.fromValue(
                config.getDetectMethod());
        if (method == CalibrationDetectMethod.WRIGHT) {
            return positive(config.getSampleWindow(), 3);
        }
        if (method == CalibrationDetectMethod.CHAUVENET) {
            return positive(config.getMinSampleCount(), 3);
        }
        return 1;
    }

    /** 从正常范围提取全部数值。 */
    private List<Double> numbers(String value) {
        List<Double> result = new ArrayList<Double>();
        Matcher matcher = RANGE.matcher(value == null ? "" : value);
        if (matcher.matches()) {
            result.add(Double.valueOf(matcher.group(1)));
            result.add(Double.valueOf(matcher.group(2)));
        }
        return result;
    }

    /** 返回正整数配置或默认值。 */
    private int positive(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    /** 返回有效数值配置或默认值。 */
    private double number(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    /** 返回消息时间的毫秒值，消息未携带时间时使用当前时间。 */
    private long messageTime(TelemetryMessage message) {
        return message.hasTime()
                ? message.getTime().getSeconds() * 1000L
                + message.getTime().getNanos() / 1_000_000L
                : System.currentTimeMillis();
    }

    /** 将检测计算中的数值转换为简洁十进制文本。 */
    private String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** 返回去除首尾空白后的字符串，空值保持为空。 */
    private String trimmed(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /** 保存野值判断结果和仅在野值时存在的原因。 */
    private static final class DetectionResult {
        /** 是否判定为野值。 */ private final boolean outlier;
        /** 判定为野值的详细原因。 */ private final String detail;
        /** 创建检测结果。 */
        private DetectionResult(boolean outlier, String detail) {
            this.outlier = outlier;
            this.detail = detail;
        }
        /** 返回正常检测结果。 */
        private static DetectionResult normal() {
            return new DetectionResult(false, null);
        }
        /** 返回携带原因的野值检测结果。 */
        private static DetectionResult outlier(String detail) {
            return new DetectionResult(true, detail);
        }
    }

    /** 保存均值和样本标准差。 */
    private static final class Statistics {
        /** 均值。 */ private final double mean;
        /** 样本标准差。 */ private final double deviation;
        /** 创建统计结果。 */
        private Statistics(double mean, double deviation) {
            this.mean = mean;
            this.deviation = deviation;
        }
    }

    /** 保存肖维涅迭代中的候选值。 */
    private static final class Candidate {
        /** 数值。 */ private final double value;
        /** 是否为本次待检测值。 */ private final boolean current;
        /** 创建候选值。 */
        private Candidate(double value, boolean current) {
            this.value = value;
            this.current = current;
        }
    }
}
