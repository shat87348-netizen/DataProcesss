package com.example.dataadmin.entity;

import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.EnumData;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 采集接口管理表实体。
 *
 * 对应数据库表 collect_interface_config，用于承载业务数据和MyBatis查询结果。
 */
public class CollectInterfaceConfig {
    /** 自增主键。 */
    private Long id;
    /** 试验任务ID。 */
    private String taskId;
    /** 接口名称。 */
    private String interfaceName;
    /** 接口类型：1外部接口 2内部接口。 */
    private Integer interfaceType;
    /** 发送方标识。 */
    private String sendFrom;
    /** 消息内容。 */
    private String messageContent;
    /** 传输方式：1UDP 2TCP 3HTTP。 */
    private Integer transferType;
    /** 传输协议：1 JSON，2 PDXP，3 FEP。 */
    private Integer transferProtocol;
    /** 协议配置ID。 */
    private Long protocolConfigId;

    /** 详情展示使用的协议配置名称，不参与新增和修改入库。 */
    private String protocolConfigName;

    /** 数据处理服务本机监听网卡IP地址。 */
    private String host;
    /** 数据处理服务本机监听端口。 */
    private Integer port;
    /** UDP组播地址，为空时按单播接收。 */
    private String multicastIp;
    /** 启用状态：0禁用 1启用。 */
    private Integer enabled;
    /** 是否启用RPC处理：0否 1是。 */
    private Integer rpcEnabled;
    /** 累计数据包数量。 */
    private Long dataPacketCount;
    /** 分页列表返回的最新采集速率，单位KB/s，不参与配置入库。 */
    private Double interfaceRateKbps;
    /** 接口备注。 */
    private String remark;
    /** 是否删除：0否 1是。 */
    private Integer isDeleted;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public Integer getInterfaceType() {
        return interfaceType;
    }

    public void setInterfaceType(Integer interfaceType) {
        this.interfaceType = interfaceType;
    }

    /** 返回接口类型中文名称。 */
    public String getInterfaceTypeName() {
        return EnumData.labelOf(BusinessEnums.InterfaceType.values(), interfaceType);
    }

    public String getSendFrom() {
        return sendFrom;
    }

    public void setSendFrom(String sendFrom) {
        this.sendFrom = sendFrom;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public Integer getTransferType() {
        return transferType;
    }

    public void setTransferType(Integer transferType) {
        this.transferType = transferType;
    }

    /** 返回传输方式中文名称。 */
    public String getTransferTypeName() {
        return EnumData.labelOf(BusinessEnums.TransferType.values(), transferType);
    }

    public Integer getTransferProtocol() {
        return transferProtocol;
    }

    public void setTransferProtocol(Integer transferProtocol) {
        this.transferProtocol = transferProtocol;
    }

    /** 返回传输协议名称。 */
    public String getTransferProtocolName() {
        return EnumData.labelOf(
                BusinessEnums.TransferProtocol.values(), transferProtocol);
    }

    /** 获取详情关联的协议配置名称。 */
    public String getProtocolConfigName() {
        return protocolConfigName;
    }

    /** 接收详情查询返回的协议配置名称。 */
    public void setProtocolConfigName(String protocolConfigName) {
        this.protocolConfigName = protocolConfigName;
    }

    public Long getProtocolConfigId() {
        return protocolConfigId;
    }

    public void setProtocolConfigId(Long protocolConfigId) {
        this.protocolConfigId = protocolConfigId;
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

    /** 启用开关是在线状态的唯一依据，不读取历史状态列。 */
    public Integer getStatus() {
        return Integer.valueOf(1).equals(enabled) ? 1 : 0;
    }

    /** 返回由启用开关决定的在线状态中文名称。 */
    public String getStatusName() {
        return EnumData.labelOf(BusinessEnums.InterfaceStatus.values(), getStatus());
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    /** 返回启用状态中文名称。 */
    public String getEnabledName() {
        return EnumData.labelOf(BusinessEnums.Enabled.values(), enabled);
    }

    public Integer getRpcEnabled() {
        return rpcEnabled;
    }

    public void setRpcEnabled(Integer rpcEnabled) {
        this.rpcEnabled = rpcEnabled;
    }

    /** 返回是否启用RPC处理的中文名称。 */
    public String getRpcEnabledName() {
        return EnumData.labelOf(BusinessEnums.YesNo.values(), rpcEnabled);
    }

    public Long getDataPacketCount() {
        return dataPacketCount;
    }

    public void setDataPacketCount(Long dataPacketCount) {
        this.dataPacketCount = dataPacketCount;
    }

    /** 仅在分页列表加载实时指标后返回采集速率。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double getInterfaceRateKbps() {
        return interfaceRateKbps;
    }

    /** 回填分页列表的实时采集速率。 */
    public void setInterfaceRateKbps(Double interfaceRateKbps) {
        this.interfaceRateKbps = interfaceRateKbps;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
