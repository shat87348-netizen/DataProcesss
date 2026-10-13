package com.example.dataadmin.enums;

/**
 * 系统运行日志级别枚举。
 *
 * 枚举编码直接保存到sys_runtime_log.log_level，并作为接口查询参数返回。
 */
public enum SystemLogLevel implements LabeledEnum {

    /** 普通运行信息。 */
    INFO("INFO", "信息"),

    /** 不影响系统继续运行的警告。 */
    WARN("WARN", "警告"),

    /** 系统功能执行失败或运行异常。 */
    ERROR("ERROR", "错误");

    /** 数据库存储和接口传输使用的编码。 */
    private final String code;

    /** 前端展示使用的中文说明。 */
    private final String description;

    SystemLogLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    @Override
    public Object getValue() {
        // 枚举查询接口沿用数据库保存的字符串编码。
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String getLabel() {
        // 枚举查询接口沿用原有中文说明。
        return description;
    }

    /**
     * 校验并统一日志级别编码。
     *
     * @param code 请求或待写入的日志级别
     * @return 大写的标准枚举编码；未传时返回null
     */
    public static String normalize(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (SystemLogLevel level : values()) {
            if (level.code.equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException(
                "日志级别只能为INFO、WARN或ERROR");
    }

    /** 根据枚举编码返回中文说明，未知或空编码返回null。 */
    public static String descriptionOf(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (SystemLogLevel level : values()) {
            if (level.code.equals(normalized)) {
                return level.description;
            }
        }
        return null;
    }
}
