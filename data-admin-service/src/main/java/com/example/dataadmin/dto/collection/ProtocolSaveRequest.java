package com.example.dataadmin.dto.collection;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 新增或修改协议配置的请求参数。
 */
public class ProtocolSaveRequest {

    /** 协议配置主键，修改时必填。 */
    /** 协议配置主键 ID；编辑时必填。 */
    private Long id;

    /** 所属试验任务 ID。 */
    /** 所属试验任务 ID。 */
    @NotBlank(message = "试验任务编号不能为空")
    @Size(max = 100, message = "试验任务编号不能超过100个字符")
    private String taskId;

    /** 配置名称。 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称不能超过100个字符")
    /** 协议配置名称。 */
    private String configName;

    /** 协议类型：1 JSON，2 PDXP，3 Protobuf，4 RPC。 */
    @NotNull(message = "协议类型不能为空")
    @Min(value = 1, message = "协议类型只能为1到4")
    @Max(value = 4, message = "协议类型只能为1到4")
    /** 协议类型：1 JSON，2 PDXP，3 Protobuf，4 RPC。 */
    private Integer protocolType;

    /** 配置说明。 */
    @Size(max = 500, message = "配置说明不能超过500个字符")
    /** 配置说明。 */
    private String configDesc;

    /** 协议动态参数；前端普通大文本框中的JSON解析为对象后提交，后端按协议校验。 */
    private Map<String, Object> configParams;

    /** 自定义解析器实现类。 */
    @Size(max = 200, message = "解析器类名不能超过200个字符")
    /** 解析器实现类全限定名。 */
    private String parserClass;

    /** 启用状态：0 禁用，1 启用。 */
    /** 是否启用：0 否，1 是。 */
    private Integer enabled;

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

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public Integer getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(Integer protocolType) {
        this.protocolType = protocolType;
    }

    public String getConfigDesc() {
        return configDesc;
    }

    public void setConfigDesc(String configDesc) {
        this.configDesc = configDesc;
    }

    public Map<String, Object> getConfigParams() {
        return configParams;
    }

    public void setConfigParams(Map<String, Object> configParams) {
        this.configParams = configParams;
    }

    public String getParserClass() {
        return parserClass;
    }

    public void setParserClass(String parserClass) {
        this.parserClass = parserClass;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
