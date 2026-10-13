package com.example.dataadmin.enums;

/** 集中定义各业务模块使用的固定枚举。 */
public final class BusinessEnums {

    /** 枚举容器不允许实例化。 */
    private BusinessEnums() {
    }

    /** 通用启用状态。 */
    public enum Enabled implements LabeledEnum {
        DISABLED(0, "禁用"), ENABLED(1, "启用");
        private final int value;
        private final String label;
        Enabled(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 通用是否状态。 */
    public enum YesNo implements LabeledEnum {
        NO(0, "否"), YES(1, "是");
        private final int value;
        private final String label;
        YesNo(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 菜单显示状态。 */
    public enum Visible implements LabeledEnum {
        HIDDEN(0, "隐藏"), DISPLAYED(1, "显示");
        private final int value;
        private final String label;
        Visible(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 处理数据类型。 */
    public enum ProcessingDataType implements LabeledEnum {
        REALTIME_TELEMETRY(1, "实时遥测"), DELAYED_TELEMETRY(2, "延时遥测"), PAYLOAD_BUSINESS(3, "载荷业务");
        private final int value;
        private final String label;
        ProcessingDataType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 采集接口类型。 */
    public enum InterfaceType implements LabeledEnum {
        EXTERNAL(1, "外部接口"), INTERNAL(2, "内部接口");
        private final int value;
        private final String label;
        InterfaceType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 采集传输方式。 */
    public enum TransferType implements LabeledEnum {
        UDP(1, "UDP"), TCP(2, "TCP"), HTTP(3, "HTTP");
        private final int value;
        private final String label;
        TransferType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 采集接口传输协议。 */
    public enum TransferProtocol implements LabeledEnum {
        JSON(1, "JSON"), PDXP(2, "PDXP"), FEP(3, "FEP");
        private final int value;
        private final String label;
        TransferProtocol(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 根据启用开关展示的采集接口在线状态。 */
    public enum InterfaceStatus implements LabeledEnum {
        OFFLINE(0, "离线"), ONLINE(1, "在线");
        private final int value;
        private final String label;
        InterfaceStatus(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 采集协议类型。 */
    public enum ProtocolType implements LabeledEnum {
        JSON(1, "JSON"), PDXP(2, "PDXP"), PROTOBUF(3, "Protobuf"),
        RPC(4, "RPC");
        private final int value;
        private final String label;
        ProtocolType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 采集数据来源类型。 */
    public enum DataSourceType implements LabeledEnum {
        TELEMETRY(1, "遥测"), REMOTE_SENSING(2, "遥感"), DEVICE(3, "设备"), ENVIRONMENT(4, "环境");
        private final int value;
        private final String label;
        DataSourceType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 可视化组件类型。 */
    public enum WidgetType implements LabeledEnum {
        REALTIME_CURVE(1, "实时曲线"),
        REALTIME_DATA(2, "实时数据"),
        REALTIME_ALARM(3, "实时告警"),
        PAYLOAD_IMAGE(4, "载荷图像"),
        FILE_TABLE(5, "文件表格");
        private final int value;
        private final String label;
        WidgetType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 遥测参数勾选的使用场景。 */
    public enum SelectionType implements LabeledEnum {
        PROCESSED_TABLE(1, "处理后数据表格"),
        PROCESSED_CURVE(2, "处理后数据曲线"),
        VISUALIZATION_TABLE(3, "数据可视化表格"),
        VISUALIZATION_CURVE(4, "数据可视化曲线");
        private final int value;
        private final String label;
        SelectionType(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
        public int getCode() { return value; }

        /** 根据编码确定场景，拒绝未知编码。 */
        public static SelectionType fromCode(Integer code) {
            for (SelectionType type : values()) {
                if (code != null && type.value == code) { return type; }
            }
            throw new IllegalArgumentException("参数勾选类型无效");
        }
    }

    /** 组件数据项选择状态。 */
    public enum Selected implements LabeledEnum {
        UNSELECTED(0, "未选中"), SELECTED(1, "已选中");
        private final int value;
        private final String label;
        Selected(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 数据处理任务状态。 */
    public enum ProcessTaskStatus implements LabeledEnum {
        WAITING(0, "等待中"), RUNNING(1, "运行中"), COMPLETED(2, "已完成");
        private final int value;
        private final String label;
        ProcessTaskStatus(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 野值检测方法。 */
    public enum DetectMethod implements LabeledEnum {
        WRIGHT(1, "莱特准则"), THRESHOLD(2, "阈值法"), CHAUVENET(3, "肖维涅法");
        private final int value;
        private final String label;
        DetectMethod(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 数据告警级别。 */
    public enum DataAlarmLevel implements LabeledEnum {
        GENERAL(2, "一般"), SERIOUS(4, "严重");
        private final int value;
        private final String label;
        DataAlarmLevel(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 系统告警级别。 */
    public enum SystemAlarmLevel implements LabeledEnum {
        GENERAL(2, "一般"), SERIOUS(3, "严重");
        private final int value;
        private final String label;
        SystemAlarmLevel(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 数据告警处理状态。 */
    public enum DataAlarmStatus implements LabeledEnum {
        UNHANDLED(0, "未处理"), HANDLED(1, "已处理");
        private final int value;
        private final String label;
        DataAlarmStatus(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 系统告警恢复状态。 */
    public enum SystemAlarmStatus implements LabeledEnum {
        UNRECOVERED(0, "未恢复"), RECOVERED(1, "已恢复");
        private final int value;
        private final String label;
        SystemAlarmStatus(int value, String label) { this.value = value; this.label = label; }
        public Object getValue() { return value; }
        public String getLabel() { return label; }
    }

}
