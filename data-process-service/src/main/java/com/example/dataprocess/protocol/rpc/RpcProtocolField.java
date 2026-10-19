package com.example.dataprocess.protocol.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** RPC协议配置中的单个字段。 */
// 仅兼容历史配置中的废弃数据类型属性，避免整份旧配置解析失败。
@JsonIgnoreProperties("dataType")
public class RpcProtocolField {

    /** FrameRequest字段名。 */
    private String field;
    /** 字段中文名称。 */
    private String fieldName;
    /** 字段配置值，允许接收JSON中的字符串、数字和布尔值。 */
    private Object value;

    /** 获取固定字段或自定义字段的标识。 */
    public String getField() {
        return field;
    }

    /** 设置固定字段或自定义字段的标识。 */
    public void setField(String field) {
        this.field = field;
    }

    /** 获取字段中文名称。 */
    public String getFieldName() {
        return fieldName;
    }

    /** 设置字段中文名称。 */
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /** 获取字段原始配置值，组装请求时沿用字符串转换规则。 */
    public Object getValue() {
        return value;
    }

    /** 接收字段配置值，兼容历史数字和布尔值。 */
    public void setValue(Object value) {
        this.value = value;
    }
}
