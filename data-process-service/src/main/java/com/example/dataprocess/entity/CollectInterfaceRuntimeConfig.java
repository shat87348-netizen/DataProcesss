package com.example.dataprocess.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import com.example.dataprocess.protocol.rpc.PdxpProtocolField;

/** 数据处理服务运行采集接口所需的配置快照。 */
public class CollectInterfaceRuntimeConfig {

    /** 采集接口主键。 */
    private Long interfaceId;
    /** 采集接口名称。 */
    private String interfaceName;
    /** 采集接口所属试验任务主键。 */
    private String taskId;
    /** 采集接口本机监听地址。 */
    private String host;
    /** 采集接口本机监听端口。 */
    private Integer port;
    /** UDP组播地址，为空时按单播接收。 */
    private String multicastIp;
    /** 传输方式：1UDP、2TCP、3HTTP。 */
    private Integer transferType;
    /** 传输协议：1JSON、2PDXP、3FEP。 */
    private Integer transferProtocol;
    /** 是否启用RPC处理。 */
    private Integer rpcEnabled;
    /** 关联协议配置主键。 */
    private Long protocolConfigId;
    /** 参数值范围检查是否开启：0关闭、1开启。 */
    private volatile Integer parameterRangeCheckEnabled;

    /** 协议动态配置参数。 */
    private String protocolConfigParams;
    /** PDXP本地处理关联的设备卫星主键；RPC运行时不设置此值。 */
    private volatile Long deviceSatelliteId;
    /** 导入工作表对应的设备卫星名称。 */
    private volatile String deviceSatelliteName;
    /** 接口启动时加载的参数解析配置快照。 */
    private volatile List<PdxpProtocolField> pdxpFields = Collections.emptyList();

    public Long getDeviceSatelliteId() { return deviceSatelliteId; }
    public void setDeviceSatelliteId(Long deviceSatelliteId) { this.deviceSatelliteId = deviceSatelliteId; }
    public String getDeviceSatelliteName() { return deviceSatelliteName; }
    public void setDeviceSatelliteName(String name) { this.deviceSatelliteName = name; }
    public List<PdxpProtocolField> getPdxpFields() { return pdxpFields; }
    public void setPdxpFields(List<PdxpProtocolField> fields) {
        this.pdxpFields = fields == null ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(fields));
    }
    /** 提交采集任务前加载的卫星名称快照，按卫星编码索引。 */
    private volatile Map<String, BaselineSatelliteInfo> baselineSatellites = Collections.emptyMap();

    /** 获取采集和处理线程共用的只读目录。 */
    public Map<String, BaselineSatelliteInfo> getBaselineSatellites() {
        return baselineSatellites;
    }

    /** 原子替换快照，避免跨线程读取正在修改的目录。 */
    public void setBaselineSatellites(Map<String, BaselineSatelliteInfo> satellites) {
        baselineSatellites = satellites == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(satellites));
    }
    /** 当前任务按校准公式索引的有效校准通道配置。 */
    private volatile Map<String, CalibrationChannelRuntimeConfig>
            calibrationConfigMap = Collections.emptyMap();

    public Long getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(Long interfaceId) {
        this.interfaceId = interfaceId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getMulticastIp() {
        return multicastIp;
    }

    public void setMulticastIp(String multicastIp) {
        this.multicastIp = multicastIp;
    }

    public Integer getTransferType() {
        return transferType;
    }

    public void setTransferType(Integer transferType) {
        this.transferType = transferType;
    }

    public Integer getTransferProtocol() {
        return transferProtocol;
    }

    public void setTransferProtocol(Integer transferProtocol) {
        this.transferProtocol = transferProtocol;
    }

    public Integer getRpcEnabled() {
        return rpcEnabled;
    }

    public void setRpcEnabled(Integer rpcEnabled) {
        this.rpcEnabled = rpcEnabled;
    }

    public Long getProtocolConfigId() {
        return protocolConfigId;
    }

    public void setProtocolConfigId(Long protocolConfigId) {
        this.protocolConfigId = protocolConfigId;
    }

    public Integer getParameterRangeCheckEnabled() {
        return parameterRangeCheckEnabled;
    }

    public void setParameterRangeCheckEnabled(
            Integer parameterRangeCheckEnabled) {
        this.parameterRangeCheckEnabled = parameterRangeCheckEnabled;
    }

    public String getProtocolConfigParams() {
        return protocolConfigParams;
    }

    public void setProtocolConfigParams(String protocolConfigParams) {
        this.protocolConfigParams = protocolConfigParams;
    }

    public Map<String, CalibrationChannelRuntimeConfig> getCalibrationConfigMap() {
        return calibrationConfigMap;
    }

    /** 原子替换当前接口使用的只读校准配置快照。 */
    public void setCalibrationConfigMap(
            Map<String, CalibrationChannelRuntimeConfig> calibrationConfigMap) {
        this.calibrationConfigMap = calibrationConfigMap == null
                ? Collections.emptyMap() : calibrationConfigMap;
    }

    /** 判断影响监听或处理分支的配置是否相同。 */
    public boolean hasSameRuntimeSettings(CollectInterfaceRuntimeConfig other) {
        // 逐项比较运行关键字段，任一变化都需要重启对应接口。
        return other != null
                && same(host, other.host)
                && same(taskId, other.taskId)
                && same(port, other.port)
                && same(multicastIp, other.multicastIp)
                && same(transferType, other.transferType)
                && same(transferProtocol, other.transferProtocol)
                && same(rpcEnabled, other.rpcEnabled)
                && same(protocolConfigId, other.protocolConfigId)
                && same(protocolConfigParams, other.protocolConfigParams);
    }

    /** 对可空配置值执行一致性比较。 */
    private boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
