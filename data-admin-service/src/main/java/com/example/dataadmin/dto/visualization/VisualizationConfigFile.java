package com.example.dataadmin.dto.visualization;

import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.DeviceSatelliteType;
import com.example.dataadmin.enums.EnumData;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** 可移植的可视化配置文件，仅保存业务标识，不携带源数据库主键。 */
public class VisualizationConfigFile {
    /** 文件格式版本，第一版固定为一。 */
    private Integer schemaVersion;
    /** 导出时间，使用包含时区偏移的时间文本。 */
    private String exportedAt;
    /** 整页组件，包含隐藏组件；空列表表示空页面。 */
    private List<Widget> widgets;

    /** 读取格式版本。 */
    public Integer getSchemaVersion() { return schemaVersion; }
    /** 设置格式版本。 */
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    /** 读取导出时间。 */
    public String getExportedAt() { return exportedAt; }
    /** 设置导出时间。 */
    public void setExportedAt(String exportedAt) { this.exportedAt = exportedAt; }
    /** 读取组件列表。 */
    public List<Widget> getWidgets() { return widgets; }
    /** 设置组件列表。 */
    public void setWidgets(List<Widget> widgets) { this.widgets = widgets; }

    /** 单个组件的布局、非遥测数据项和已勾选遥测参数。 */
    public static class Widget {
        /** 用户任务范围内唯一的组件标识。 */
        private String widgetKey;
        /** 复用现有组件类型枚举编码。 */
        private Integer widgetType;
        /** 页面标题。 */
        private String widgetTitle;
        /** 横向网格坐标。 */
        private Integer gridX;
        /** 纵向网格坐标。 */
        private Integer gridY;
        /** 占用网格列数。 */
        private Integer gridWidth;
        /** 占用网格行数。 */
        private Integer gridHeight;
        /** 复用现有显示状态编码。 */
        private Integer enabled;
        /** 非遥测数据项，保留各自勾选状态。 */
        private List<Item> items = new ArrayList<>();
        /** 此组件已经勾选的遥测参数，不包含未勾选参数。 */
        private List<Parameter> selectedParameters = new ArrayList<>();

        /** 读取组件标识。 */
        public String getWidgetKey() { return widgetKey; }
        /** 设置组件标识。 */
        public void setWidgetKey(String widgetKey) { this.widgetKey = widgetKey; }
        /** 读取组件类型。 */
        public Integer getWidgetType() { return widgetType; }
        /** 设置组件类型。 */
        public void setWidgetType(Integer widgetType) { this.widgetType = widgetType; }
        /** 组件类型中文名称只用于展示，导入忽略该名称。 */
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public String getWidgetTypeName() {
            return EnumData.labelOf(BusinessEnums.WidgetType.values(), widgetType);
        }
        /** 读取组件标题。 */
        public String getWidgetTitle() { return widgetTitle; }
        /** 设置组件标题。 */
        public void setWidgetTitle(String widgetTitle) { this.widgetTitle = widgetTitle; }
        /** 读取横向位置。 */
        public Integer getGridX() { return gridX; }
        /** 设置横向位置。 */
        public void setGridX(Integer gridX) { this.gridX = gridX; }
        /** 读取纵向位置。 */
        public Integer getGridY() { return gridY; }
        /** 设置纵向位置。 */
        public void setGridY(Integer gridY) { this.gridY = gridY; }
        /** 读取网格宽度。 */
        public Integer getGridWidth() { return gridWidth; }
        /** 设置网格宽度。 */
        public void setGridWidth(Integer gridWidth) { this.gridWidth = gridWidth; }
        /** 读取网格高度。 */
        public Integer getGridHeight() { return gridHeight; }
        /** 设置网格高度。 */
        public void setGridHeight(Integer gridHeight) { this.gridHeight = gridHeight; }
        /** 读取显示状态。 */
        public Integer getEnabled() { return enabled; }
        /** 设置显示状态。 */
        public void setEnabled(Integer enabled) { this.enabled = enabled; }
        /** 显示状态中文名称只用于展示。 */
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public String getEnabledName() {
            return EnumData.labelOf(BusinessEnums.Visible.values(), enabled);
        }
        /** 读取非遥测数据项。 */
        public List<Item> getItems() { return items; }
        /** 设置非遥测数据项。 */
        public void setItems(List<Item> items) { this.items = items; }
        /** 读取已勾选参数。 */
        public List<Parameter> getSelectedParameters() { return selectedParameters; }
        /** 设置已勾选参数。 */
        public void setSelectedParameters(List<Parameter> selectedParameters) {
            this.selectedParameters = selectedParameters;
        }
    }

    /** 用设备类型、设备编码和遥测代号定位参数，名称及系统路径供核对。 */
    public static class Parameter {
        /** 复用设备卫星类型枚举编码。 */
        private String deviceType;
        /** 设备卫星编码。 */
        private String deviceCode;
        /** 导出时的设备卫星名称。 */
        private String deviceName;
        /** 设备内唯一的遥测代号。 */
        private String telemetryCode;
        /** 导出时的遥测名称。 */
        private String telemetryName;
        /** 导出时的所属系统完整路径。 */
        private String systemPath;
        /** 此参数已有的数据项配置；筛选树直接勾选时可以为空。 */
        private List<Item> dataItems = new ArrayList<>();

        /** 读取设备类型。 */
        public String getDeviceType() { return deviceType; }
        /** 设置设备类型。 */
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        /** 返回设备类型中文名称，导入时仍使用类型编码匹配。 */
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public String getDeviceTypeName() {
            return EnumData.labelOf(DeviceSatelliteType.values(), deviceType);
        }
        /** 读取设备编码。 */
        public String getDeviceCode() { return deviceCode; }
        /** 设置设备编码。 */
        public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
        /** 读取设备名称。 */
        public String getDeviceName() { return deviceName; }
        /** 设置设备名称。 */
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        /** 读取遥测代号。 */
        public String getTelemetryCode() { return telemetryCode; }
        /** 设置遥测代号。 */
        public void setTelemetryCode(String telemetryCode) { this.telemetryCode = telemetryCode; }
        /** 读取遥测名称。 */
        public String getTelemetryName() { return telemetryName; }
        /** 设置遥测名称。 */
        public void setTelemetryName(String telemetryName) { this.telemetryName = telemetryName; }
        /** 读取系统完整路径。 */
        public String getSystemPath() { return systemPath; }
        /** 设置系统完整路径。 */
        public void setSystemPath(String systemPath) { this.systemPath = systemPath; }
        /** 读取已有数据项。 */
        public List<Item> getDataItems() { return dataItems; }
        /** 设置已有数据项。 */
        public void setDataItems(List<Item> dataItems) { this.dataItems = dataItems; }
    }

    /** 组件数据项通过名称重新关联采集接口和协议，不复制源主键。 */
    public static class Item {
        /** 数据项代号。 */
        private String itemCode;
        /** 数据项名称。 */
        private String itemName;
        /** 数据项是否勾选；遥测参数下的数据项始终为已勾选。 */
        private Boolean selected;
        /** 可选的采集接口名称，目标任务内必须唯一。 */
        private String interfaceName;
        /** 可选的协议配置名称，目标任务内必须唯一。 */
        private String protocolConfigName;

        /** 读取数据项代号。 */
        public String getItemCode() { return itemCode; }
        /** 设置数据项代号。 */
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        /** 读取数据项名称。 */
        public String getItemName() { return itemName; }
        /** 设置数据项名称。 */
        public void setItemName(String itemName) { this.itemName = itemName; }
        /** 读取数据项勾选状态。 */
        public Boolean getSelected() { return selected; }
        /** 设置数据项勾选状态。 */
        public void setSelected(Boolean selected) { this.selected = selected; }
        /** 读取采集接口名称。 */
        public String getInterfaceName() { return interfaceName; }
        /** 设置采集接口名称。 */
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
        /** 读取协议配置名称。 */
        public String getProtocolConfigName() { return protocolConfigName; }
        /** 设置协议配置名称。 */
        public void setProtocolConfigName(String protocolConfigName) { this.protocolConfigName = protocolConfigName; }
    }
}
