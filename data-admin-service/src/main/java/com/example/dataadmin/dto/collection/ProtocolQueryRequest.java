package com.example.dataadmin.dto.collection;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 协议配置分页查询参数，复用采集接口列表的分页约定。 */
public class ProtocolQueryRequest {

    /** 试验任务编号，不传时查询全部任务。 */
    private String taskId;
    /** 协议类型：1 JSON，2 PDXP，3 Protobuf，4 RPC；不传时不限类型。 */
    @Min(value = 1, message = "协议类型只能为1到4")
    @Max(value = 4, message = "协议类型只能为1到4")
    private Integer protocolType;
    /** 可选启用状态，HTTP选择框仅加载已启用JSON配置。 */
    @Min(value = 0, message = "协议启用状态只能为0或1")
    @Max(value = 1, message = "协议启用状态只能为0或1")
    private Integer enabled;
    /** 配置名称，按包含关系模糊匹配；空白时不限名称。 */
    private String configName;
    /** 当前页码，从1开始。 */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;
    /** 每页记录数，默认10条，最大100条。 */
    @NotNull(message = "每页记录数不能为空")
    @Min(value = 1, message = "每页记录数不能小于1")
    @Max(value = 100, message = "每页记录数不能超过100")
    private Integer pageSize = 10;

    /** 使用长整型计算偏移量，避免较大页码发生整数溢出。 */
    public long getOffset() {
        return (pageNum.longValue() - 1L) * pageSize;
    }

    /** 获取试验任务编号。 */
    public String getTaskId() {
        return taskId;
    }

    /** 清理任务编号两端空白，空字符串由查询条件统一忽略。 */
    public void setTaskId(String taskId) {
        this.taskId = taskId == null ? null : taskId.trim();
    }

    /** 获取协议类型编码。 */
    public Integer getProtocolType() {
        return protocolType;
    }

    /** 获取可选的协议启用状态筛选。 */
    public Integer getEnabled() {
        return enabled;
    }

    /** 设置筛选值，空值表示不限制启用状态。 */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    /** 设置协议类型编码，沿用现有协议枚举。 */
    public void setProtocolType(Integer protocolType) {
        this.protocolType = protocolType;
    }

    /** 获取名称筛选条件。 */
    public String getConfigName() {
        return configName;
    }

    /** 清理名称两端空白，避免纯空白名称参与筛选。 */
    public void setConfigName(String configName) {
        this.configName = configName == null ? null : configName.trim();
    }

    /** 获取当前页码。 */
    public Integer getPageNum() {
        return pageNum;
    }

    /** 设置当前页码，由请求校验限制有效范围。 */
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    /** 获取每页记录数。 */
    public Integer getPageSize() {
        return pageSize;
    }

    /** 设置每页记录数，由请求校验限制有效范围。 */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
