package com.example.common.enums;
/** 数据告警类型、来源和默认级别统一维护，数据告警仅使用2一般、4严重。 */
public enum DataAlarmType {
    INTERFACE_SYNC("采集运行状态同步失败", DataAlarmSource.COLLECTION, 4),
    INTERFACE_START("接口启动失败", DataAlarmSource.COLLECTION, 4),
    INTERFACE_STOP("采集接口停止接收", DataAlarmSource.COLLECTION, 4),
    COLLECTION_RECEIVE("接口接收异常", DataAlarmSource.COLLECTION, 2),
    PACKET_PARSE("报文解析失败", DataAlarmSource.COLLECTION, 2),
    FILE_RECEIVE("文件接收失败", DataAlarmSource.COLLECTION, 2),
    PROCESS_QUEUE("处理队列已满", DataAlarmSource.PROCESSING, 4),
    PROCESS_CONFIG("参数处理失败", DataAlarmSource.PROCESSING, 4),
    RPC_FAILURE("远程处理失败", DataAlarmSource.PROCESSING, 4),
    PARAMETER_RANGE("遥测参数越限", DataAlarmSource.PROCESSING, 2),
    STORAGE_FAILURE("数据存储失败", DataAlarmSource.PROCESSING, 4),
    MESSAGE_FAILURE("结果消息发送失败", DataAlarmSource.PROCESSING, 4),
    FILE_RESULT("文件结果处理失败", DataAlarmSource.PROCESSING, 2),
    CALIBRATION_OUTLIER("校准野值", DataAlarmSource.CALIBRATION, 2),
    CALIBRATION_RULE("校准规则异常", DataAlarmSource.CALIBRATION, 4),
    CALIBRATION_CACHE("校准缓存失败", DataAlarmSource.CALIBRATION, 4),
    CALIBRATION_PERSIST("校准记录落库失败", DataAlarmSource.CALIBRATION, 4);
    private final String label;
    private final DataAlarmSource source;
    private final int level;
    DataAlarmType(String label, DataAlarmSource source, int level) {
        this.label = label;
        this.source = source;
        this.level = level;
    }
    public String getLabel() { return label; }
    public DataAlarmSource getSource() { return source; }
    public int getLevel() { return level; }
    /** 未设置自动事件类型或编码未知的记录不返回虚构中文名称。 */
    public static String labelOf(String code) {
        for (DataAlarmType type : values()) {
            if (type.name().equals(code)) { return type.label; }
        }
        return null;
    }
}
