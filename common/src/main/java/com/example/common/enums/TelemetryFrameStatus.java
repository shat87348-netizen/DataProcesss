package com.example.common.enums;

/** 整帧检查结果，中文名称用于帧记录和页面展示。 */
public enum TelemetryFrameStatus {
    /** 整帧处理或检查正常。 */
    NORMAL("正常"),
    /** 远程帧检查失败或本地参数解析失败。 */
    ABNORMAL("异常");

    /** 帧检查结果中文名称。 */
    private final String label;

    TelemetryFrameStatus(String label) {
        this.label = label;
    }

    /** 返回帧记录使用的中文检查结果。 */
    public String getLabel() {
        return label;
    }
}
