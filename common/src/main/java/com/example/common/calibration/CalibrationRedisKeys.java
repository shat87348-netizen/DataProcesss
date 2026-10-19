package com.example.common.calibration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 统一维护校准样本在缓存中的键名。 */
public final class CalibrationRedisKeys {

    /** 待批量写入数据库的校准野值详情队列。 */
    public static final String PENDING_QUEUE = "calibration:persist:pending";

    /** 待批量累加到数据库的校准参数数量队列，不随样本过期清理。 */
    public static final String PENDING_COUNT_QUEUE = "calibration:persist:count:pending";

    /** 工具类不允许实例化。 */
    private CalibrationRedisKeys() {
    }

    /** 返回参与后续统计计算的有效样本键。 */
    public static String effectiveSamples(
            String taskId,
            String calibrationFormula,
            String telemetryCode) {
        return sampleKey("effective", taskId, calibrationFormula, telemetryCode);
    }

    /** 组合隔离到任务、校准公式和遥测参数的样本键。 */
    private static String sampleKey(
            String type,
            String taskId,
            String calibrationFormula,
            String telemetryCode) {
        return "calibration:sample:" + type + ":" + taskId + ":"
                + encode(calibrationFormula) + ":" + encode(telemetryCode);
    }

    /** 对可能包含分隔符的业务编号编码，避免缓存键相互冲突。 */
    private static String encode(String value) {
        String source = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                source.getBytes(StandardCharsets.UTF_8));
    }
}
