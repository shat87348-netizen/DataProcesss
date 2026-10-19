package com.example.dataadmin.mapper;

import com.example.common.enums.TelemetryFrameStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 使用MyBatis映射生成整帧查询，由IoTDB会话执行而非关系数据库连接。 */
@Component
public class IoTDBFrameQueryMapper {
    /** 复用应用已经加载的映射配置。 */
    private final SqlSessionFactory sqlSessionFactory;

    /** 注入映射工厂。 */
    public IoTDBFrameQueryMapper(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /** 生成任务范围内检查正常的整帧分页查询。 */
    public String realtimeFrames(
            String devicePath,
            int limit,
            long offset) {
        // 第一步：通过已校验设备路径限定任务和采集接口。
        Map<String, Object> parameters = baseParameters(devicePath);
        // 正常帧条件使用已有检查结果枚举，与入库使用的中文名称一致。
        parameters.put("normalStatus", TelemetryFrameStatus.NORMAL.getLabel());
        // 第二步：传递分页参数并生成IoTDB可直接执行的语句。
        parameters.put("limit", limit);
        parameters.put("offset", offset);
        return mappedSql("realtimeFrames", parameters);
    }

    /** 生成任务范围内检查正常的整帧总数查询，与列表筛选保持一致。 */
    public String countRealtimeFrames(String devicePath) {
        Map<String, Object> parameters = baseParameters(devicePath);
        // 分页总数沿用列表相同的正常状态，避免把异常帧计入总数。
        parameters.put("normalStatus", TelemetryFrameStatus.NORMAL.getLabel());
        return mappedSql("countRealtimeFrames", parameters);
    }

    /** 生成单个遥测参数最新一条数据的查询。 */
    public String latestProcessedTelemetry(String devicePath) {
        return mappedSql("latestProcessedTelemetry", baseParameters(devicePath));
    }

    /** 生成单个遥测参数最新一百二十条曲线点的查询。 */
    public String recentProcessedTelemetry(String devicePath) {
        return mappedSql("recentProcessedTelemetry", baseParameters(devicePath));
    }

    /** 生成异常整帧分页查询。 */
    public String invalidFrames(
            String devicePath,
            int limit,
            long offset) {
        Map<String, Object> parameters = baseParameters(devicePath);
        parameters.put("limit", limit);
        parameters.put("offset", offset);
        return mappedSql("invalidFrames", parameters);
    }

    /** 生成异常整帧总数查询。 */
    public String countInvalidFrames(String devicePath) {
        Map<String, Object> parameters = baseParameters(devicePath);
        return mappedSql("countInvalidFrames", parameters);
    }

    /** 创建所有IoTDB查询共用的安全设备路径参数。 */
    private Map<String, Object> baseParameters(String devicePath) {
        // 第一步：设备路径只能使用业务层生成的root.db范围，禁止执行其他语句。
        if (devicePath == null
                || !devicePath.startsWith("root.db.")
                || devicePath.contains(";")
                || devicePath.matches(".*\\s+.*")) {
            throw new IllegalArgumentException("IoTDB设备路径不合法");
        }
        // 第二步：映射仅拼接经过校验的设备路径。
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("devicePath", devicePath);
        return parameters;
    }

    /** 从MyBatis映射中取得最终IoTDB语句。 */
    private String mappedSql(
            String statementId,
            Map<String, Object> parameters) {
        // IoTDB会话不支持MyBatis绑定参数，映射只接收已转义文本和强类型数值。
        return sqlSessionFactory.getConfiguration()
                .getMappedStatement(
                        IoTDBFrameQueryMapper.class.getName()
                                + "." + statementId)
                .getBoundSql(parameters)
                .getSql();
    }
}
