package com.example.dataadmin.enums;

import com.example.dataadmin.vo.EnumOptionVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 按控制器整理其页面涉及的全部固定枚举。 */
public final class ControllerEnumData {

    /** 工具类不允许实例化。 */
    private ControllerEnumData() {
    }

    /** 返回不包含固定枚举的空数据。 */
    public static Map<String, List<EnumOptionVO>> empty() {
        return EnumData.create();
    }

    /** 返回试验任务模块枚举。 */
    public static Map<String, List<EnumOptionVO>> experimentTask() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "taskStatus", ExperimentTaskStatus.values());
        EnumData.add(data, "endReason", TaskEndReason.values());
        return data;
    }

    /** 返回首页总览模块枚举。 */
    public static Map<String, List<EnumOptionVO>> dashboard() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "range", DashboardRange.values());
        EnumData.add(data, "dataType", BusinessEnums.ProcessingDataType.values());
        EnumData.add(data, "status", BusinessEnums.InterfaceStatus.values());
        return data;
    }

    /** 返回采集管理模块枚举。 */
    public static Map<String, List<EnumOptionVO>> collection() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "interfaceType", BusinessEnums.InterfaceType.values());
        EnumData.add(data, "transferType", BusinessEnums.TransferType.values());
        EnumData.add(data, "transferProtocol", BusinessEnums.TransferProtocol.values());
        EnumData.add(data, "protocolType", BusinessEnums.ProtocolType.values());
        EnumData.add(data, "dataSourceType", BusinessEnums.DataSourceType.values());
        EnumData.add(data, "status", BusinessEnums.InterfaceStatus.values());
        EnumData.add(data, "enabled", BusinessEnums.Enabled.values());
        return data;
    }

    /** 返回处理管理模块枚举。 */
    public static Map<String, List<EnumOptionVO>> processing() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "taskStatus", BusinessEnums.ProcessTaskStatus.values());
        EnumData.add(data, "ruleEnabled", BusinessEnums.Enabled.values());
        EnumData.add(data, "storeFlag", BusinessEnums.YesNo.values());
        EnumData.add(data, "alarmFlag", BusinessEnums.YesNo.values());
        EnumData.add(data, "deviceSatelliteType", DeviceSatelliteType.values());
        EnumData.add(data, "selectionType", BusinessEnums.SelectionType.values());
        return data;
    }

    /** 返回卫星目录管理模块枚举。 */
    public static Map<String, List<EnumOptionVO>> satelliteManagement() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "tableType", SatelliteTableType.values());
        return data;
    }

    /** 返回校准管理模块枚举。 */
    public static Map<String, List<EnumOptionVO>> calibration() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "detectMethod", BusinessEnums.DetectMethod.values());
        EnumData.add(data, "dynamicUpdate", BusinessEnums.YesNo.values());
        EnumData.add(data, "autoClean", BusinessEnums.YesNo.values());
        EnumData.add(data, "enabled", BusinessEnums.Enabled.values());
        EnumData.add(data, "isOutlier", BusinessEnums.YesNo.values());
        return data;
    }

    /** 返回告警管理模块枚举。 */
    public static Map<String, List<EnumOptionVO>> alarm() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        // 来源和事件类型来自公共枚举，管理服务和处理服务保持一致。
        List<EnumOptionVO> sources = new ArrayList<EnumOptionVO>();
        for (com.example.common.enums.DataAlarmSource source : com.example.common.enums.DataAlarmSource.values()) {
            sources.add(new EnumOptionVO(source.getValue(), source.getLabel()));
        }
        data.put("dataAlarmSource", sources);
        List<EnumOptionVO> types = new java.util.ArrayList<EnumOptionVO>();
        for (com.example.common.enums.DataAlarmType type : com.example.common.enums.DataAlarmType.values()) {
            types.add(new EnumOptionVO(type.name(), type.getLabel()));
        }
        data.put("dataAlarmEventType", types);
        EnumData.add(data, "dataAlarmLevel", BusinessEnums.DataAlarmLevel.values());
        EnumData.add(data, "dataAlarmStatus", BusinessEnums.DataAlarmStatus.values());
        EnumData.add(data, "systemAlarmLevel", BusinessEnums.SystemAlarmLevel.values());
        EnumData.add(data, "systemAlarmStatus", BusinessEnums.SystemAlarmStatus.values());
        // 系统告警类型与监测指标复用同一编码定义，不重复创建类型枚举。
        EnumData.add(data, "systemAlarmType", SystemMonitorMetric.values());
        EnumData.add(data, "serviceStatus", SystemMonitorServiceStatus.values());
        EnumData.add(data, "metricCode", SystemMonitorMetric.values());
        EnumData.add(data, "heartbeatStatus", SystemHeartbeatStatus.values());
        EnumData.add(data, "changeType", SystemHealthAlarmChangeType.values());
        return data;
    }

    /** 返回可视化模块枚举。 */
    public static Map<String, List<EnumOptionVO>> visualization() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "widgetType", BusinessEnums.WidgetType.values());
        EnumData.add(data, "enabled", BusinessEnums.Visible.values());
        EnumData.add(data, "isSelected", BusinessEnums.Selected.values());
        EnumData.add(data, "selectionType", BusinessEnums.SelectionType.values());
        return data;
    }

    /** 返回用户模块枚举。 */
    public static Map<String, List<EnumOptionVO>> user() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "status", BusinessEnums.Enabled.values());
        return data;
    }

    /** 返回角色与菜单模块枚举。 */
    public static Map<String, List<EnumOptionVO>> role() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "status", BusinessEnums.Enabled.values());
        return data;
    }

    /** 返回系统日志模块枚举。 */
    public static Map<String, List<EnumOptionVO>> systemLog() {
        Map<String, List<EnumOptionVO>> data = EnumData.create();
        EnumData.add(data, "logLevel", SystemLogLevel.values());
        // 人工操作和枚举选项引用同一个来源枚举，数据库编码与展示名称分开维护。
        EnumData.add(data, "logSource", SystemLogSource.values());
        return data;
    }
}
