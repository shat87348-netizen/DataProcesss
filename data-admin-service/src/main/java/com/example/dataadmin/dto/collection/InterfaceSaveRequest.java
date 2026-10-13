package com.example.dataadmin.dto.collection;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 新增或修改采集接口的请求参数。
 */
public class InterfaceSaveRequest {

    /** 采集接口主键 ID；编辑时必填。 */
    private Long id;

    /** 所属试验任务 ID。 */
    @NotBlank(message = "试验任务编号不能为空")
    @Size(max = 100, message = "试验任务编号不能超过100个字符")
    private String taskId;

    @NotBlank(message = "接口名称不能为空")
    @Size(max = 100, message = "接口名称不能超过100个字符")
    /** 采集接口名称。 */
    private String interfaceName;

    @NotNull(message = "接口类型不能为空")
    /** 接口类型：1 外部接口，2 内部接口。 */
    private Integer interfaceType;

    @Size(max = 100, message = "发送方不能超过100个字符")
    /** 数据发送来源。 */
    private String sendFrom;

    @Size(max = 500, message = "信息内容不能超过500个字符")
    /** 消息内容或格式说明。 */
    private String messageContent;

    @NotNull(message = "传输方式不能为空")
    /** 传输方式：1 UDP，2 TCP，3 HTTP。 */
    private Integer transferType;

    /** 传输协议：1 JSON，2 PDXP，3 FEP。 */
    @NotNull(message = "传输协议不能为空")
    @Min(value = 1, message = "传输协议只能为1到3")
    @Max(value = 3, message = "传输协议只能为1到3")
    private Integer transferProtocol;

    /** 关联协议配置 ID。 */
    private Long protocolConfigId;

    @NotBlank(message = "主机地址不能为空")
    @Size(max = 100, message = "主机地址不能超过100个字符")
    /** 数据处理服务本机监听地址。 */
    private String host;

    @NotNull(message = "端口号不能为空")
    @Min(value = 1, message = "端口号不能小于1")
    @Max(value = 65535, message = "端口号不能大于65535")
    /** 数据处理服务本机监听端口。 */
    private Integer port;

    /** UDP组播地址；为空时按UDP单播接收。 */
    @Size(max = 100, message = "组播地址不能超过100个字符")
    private String multicastIp;

    /** 是否启用：0 否，1 是。 */
    private Integer enabled;

    /** 是否启用RPC处理：0否 1是。 */
    @Min(value = 0, message = "RPC处理开关不能小于0")
    @Max(value = 1, message = "RPC处理开关不能大于1")
    private Integer rpcEnabled;

    @Size(max = 500, message = "备注不能超过500个字符")
    /** 备注信息。 */
    private String remark;

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

    public Integer getTransferProtocol() {
        return transferProtocol;
    }

    public void setTransferProtocol(Integer transferProtocol) {
        this.transferProtocol = transferProtocol;
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

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getRpcEnabled() {
        return rpcEnabled;
    }

    public void setRpcEnabled(Integer rpcEnabled) {
        this.rpcEnabled = rpcEnabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
