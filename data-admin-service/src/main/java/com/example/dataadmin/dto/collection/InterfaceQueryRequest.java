package com.example.dataadmin.dto.collection;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 采集接口分页查询参数。
 */
public class InterfaceQueryRequest {

    /** 试验任务 ID，不传时查询全部任务。 */
    /** 按试验任务过滤。 */
    private String taskId;

    /** 接口类型：1 外部接口，2 内部接口。 */
    /** 按接口类型过滤：1 外部接口，2 内部接口。 */
    private Integer interfaceType;

    /** 传输方式：1 UDP，2 TCP，3 HTTP。 */
    /** 按传输方式过滤：1 UDP，2 TCP，3 HTTP。 */
    private Integer transferType;

    /** 按启用开关对应的在线状态过滤：0离线，1在线。 */
    @Min(value = 0, message = "接口状态只能为0（离线）或1（在线）")
    @Max(value = 1, message = "接口状态只能为0（离线）或1（在线）")
    private Integer status;

    /** 启用状态：0 禁用，1 启用。 */
    /** 按启用状态过滤：0 禁用，1 启用。 */
    private Integer enabled;

    /** 当前页码。 */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    /** 当前页码，从 1 开始。 */
    private Integer pageNum = 1;

    /** 每页记录数，限制最大 100 条。 */
    @NotNull(message = "每页记录数不能为空")
    @Min(value = 1, message = "每页记录数不能小于1")
    @Max(value = 100, message = "每页记录数不能超过100")
    /** 每页记录数量。 */
    private Integer pageSize = 10;

    /**
     * 计算数据库分页偏移量。
     *
     * @return SQL 查询偏移量
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getInterfaceType() {
        return interfaceType;
    }

    public void setInterfaceType(Integer interfaceType) {
        this.interfaceType = interfaceType;
    }

    public Integer getTransferType() {
        return transferType;
    }

    public void setTransferType(Integer transferType) {
        this.transferType = transferType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
