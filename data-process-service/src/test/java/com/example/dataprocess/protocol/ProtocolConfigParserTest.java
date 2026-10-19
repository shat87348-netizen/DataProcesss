package com.example.dataprocess.protocol;

import com.example.dataprocess.protocol.rpc.PdxpProtocolField;
import com.example.dataprocess.protocol.rpc.ProtocolConfigParser;
import com.example.dataprocess.protocol.rpc.RpcProtocolField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通用协议动态配置解析测试。 */
class ProtocolConfigParserTest {

    /** 验证RPC配置可以转换为RPC字段类。 */
    @Test
    void shouldParseRpcProtocolFields() {
        // 保留旧配置的数据类型属性，确认删除属性后历史配置仍能正确解析。
        String configParams = "{\"fields\":["
                + "{\"field\":\"sat_code\",\"fieldName\":\"卫星代号\","
                + "\"dataType\":\"string\",\"value\":\"SAT-001\"}]}";

        // 执行通用解析并指定RPC字段类型。
        List<RpcProtocolField> fields = parser().parseFields(
                configParams, RpcProtocolField.class);

        // 校验通用解析器忽略废弃属性，并保留仍在使用的字段信息。
        assertEquals(1, fields.size());
        assertEquals("sat_code", fields.get(0).getField());
        assertEquals("卫星代号", fields.get(0).getFieldName());
        assertEquals("SAT-001", fields.get(0).getValue());
    }

    /** 验证RPC字段值可以保留JSON数字类型。 */
    @Test
    void shouldKeepRpcNumericFieldValue() {
        // 准备包含数字配置值的RPC字段JSON。
        String configParams = "{\"fields\":["
                + "{\"field\":\"sat_code\",\"value\":1001}]}";

        // 执行通用解析并读取字段值。
        List<RpcProtocolField> fields = parser().parseFields(
                configParams, RpcProtocolField.class);

        // 数字值应按JSON原始类型保留，供请求组装时统一转为字符串。
        assertEquals(1001, fields.get(0).getValue());
    }

    /** 验证PDXP配置可以转换为PDXP字段类。 */
    @Test
    void shouldParsePdxpProtocolFields() {
        // 准备与参数解析配置字段一致的PDXP字段JSON。
        String configParams = "{\"fields\":["
                + "{\"id\":81,\"tableIndex\":\"1\",\"bitWidth\":\"16\","
                + "\"telemetryName\":\"温度\",\"telemetryCode\":\"TM001\","
                + "\"calibrationFormula\":\"x*0.1\"}]}";

        // 执行通用解析并指定PDXP字段类型。
        List<PdxpProtocolField> fields = parser().parseFields(
                configParams, PdxpProtocolField.class);

        // 校验通用解析器保留PDXP字段的所有属性。
        assertEquals(1, fields.size());
        assertEquals(81L, fields.get(0).getId());
        assertEquals(1, fields.get(0).getTableIndex());
        assertEquals(16, fields.get(0).getBitWidth());
        assertEquals("温度", fields.get(0).getTelemetryName());
        assertEquals("TM001", fields.get(0).getTelemetryCode());
        assertEquals("x*0.1", fields.get(0).getCalibrationFormula());
    }

    /** 验证缺少fields数组时只返回空列表。 */
    @Test
    void shouldReturnEmptyListWhenFieldsAreMissing() {
        // 解析不包含fields数组的协议配置。
        List<RpcProtocolField> fields = parser().parseFields(
                "{}", RpcProtocolField.class);

        // 缺少字段列表时不抛异常。
        assertTrue(fields.isEmpty());
    }

    /** 验证非法JSON时只返回空列表。 */
    @Test
    void shouldReturnEmptyListForInvalidJson() {
        // 解析不完整的JSON字符串。
        List<RpcProtocolField> fields = parser().parseFields(
                "{\"fields\":[", RpcProtocolField.class);

        // 非法JSON不阻断数据处理。
        assertTrue(fields.isEmpty());
    }

    /** 创建每个测试独立使用的通用协议配置解析器。 */
    private ProtocolConfigParser parser() {
        // 使用与Spring默认配置兼容的Jackson解析器。
        return new ProtocolConfigParser(new ObjectMapper());
    }
}
