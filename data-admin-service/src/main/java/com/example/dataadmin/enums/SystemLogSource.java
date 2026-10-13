package com.example.dataadmin.enums;

/** 操作日志来源，编码用于录入和筛选，中文名称用于展示。 */
public enum SystemLogSource implements LabeledEnum {
    COLLECTION("COLLECTION", "数据采集"),
    PROCESSING("PROCESSING", "数据处理"),
    CALIBRATION("CALIBRATION", "数据校准"),
    VISUALIZATION("VISUALIZATION", "数据可视化"),
    TELEMETRY_FILTER("TELEMETRY_FILTER", "遥测参数筛选"),
    ALARM_MANAGEMENT("ALARM_MANAGEMENT", "告警管理"),
    USER_MANAGEMENT("USER_MANAGEMENT", "用户管理"),
    ROLE_MANAGEMENT("ROLE_MANAGEMENT", "角色管理"),
    SATELLITE_CATALOG("SATELLITE_CATALOG", "卫星目录"),
    AUTHENTICATION("AUTHENTICATION", "用户认证");

    /** 接口传输和数据库保存使用的稳定英文编码。 */
    private final String value;
    /** 前端展示使用的中文名称。 */
    private final String label;

    SystemLogSource(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /** 校验可选的来源编码，不接受中文名称或未知编码。 */
    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        // 来源统一按枚举值匹配，避免数据库再次出现自由填写的来源名称。
        String code = value.trim();
        for (SystemLogSource source : values()) {
            if (source.value.equals(code)) {
                return code;
            }
        }
        throw new IllegalArgumentException("日志来源编码无效，请使用枚举接口返回的编码");
    }

    /** 按编码返回中文名称，未知或历史自由来源返回空值。 */
    public static String labelOf(String value) {
        for (SystemLogSource source : values()) {
            if (source.value.equals(value)) {
                return source.label;
            }
        }
        return null;
    }
}
