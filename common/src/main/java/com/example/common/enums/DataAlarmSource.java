package com.example.common.enums;

import java.util.ArrayList;
import java.util.List;

/** 自动数据告警来源，英文编码用于入库和筛选，中文名称用于展示。 */
public enum DataAlarmSource {
    COLLECTION("COLLECTION", "数据采集"),
    PROCESSING("PROCESSING", "数据处理"),
    CALIBRATION("CALIBRATION", "数据校准");

    /** 接口传输和数据库保存使用的稳定编码。 */
    private final String value;
    /** 前端展示使用的中文名称。 */
    private final String label;

    DataAlarmSource(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /** 校验可选的来源筛选编码，不接受中文名称或未知编码。 */
    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        // 去除输入两侧空格后严格匹配枚举编码，不将展示名称转换为编码。
        String code = value.trim();
        for (DataAlarmSource source : values()) {
            if (source.value.equals(code)) {
                return code;
            }
        }
        throw new IllegalArgumentException("告警来源编码无效，请使用枚举接口返回的编码");
    }

    /** 根据已保存的编码返回中文名称，未知编码不伪造展示名称。 */
    public static String labelOf(String value) {
        for (DataAlarmSource source : values()) {
            if (source.value.equals(value)) {
                return source.label;
            }
        }
        return null;
    }

    /** 中文关键字匹配来源名称后转为编码，保持关键字查询的中文使用习惯。 */
    public static List<String> matchingValues(String keyword) {
        List<String> matches = new ArrayList<String>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return matches;
        }
        // 来源名称仅参与展示层匹配，传给数据库的条件仍然是英文编码。
        for (DataAlarmSource source : values()) {
            if (source.label.contains(keyword.trim())) {
                matches.add(source.value);
            }
        }
        return matches;
    }
}
