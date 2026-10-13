package com.example.dataadmin.scheduler;

import com.example.common.tool.HttpRequestTool;
import com.example.dataadmin.config.InteractionHeartbeatProperties;
import com.example.dataadmin.dto.integration.InteractionHeartbeatRequest;
import com.example.dataadmin.enums.InteractionHeartbeatStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时向交互系统上报当前系统心跳。 */
@Component
public class InteractionHeartbeatReporter {

    /** 心跳上报日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            InteractionHeartbeatReporter.class);

    /** 交互系统心跳配置。 */
    private final InteractionHeartbeatProperties properties;

    /** 心跳上报使用的公共HTTP工具。 */
    private final HttpRequestTool httpRequestTool;

    public InteractionHeartbeatReporter(
            InteractionHeartbeatProperties properties) {
        this.properties = properties;

        // 按心跳配置创建可复用的HTTP请求工具。
        this.httpRequestTool = new HttpRequestTool(
                properties.getConnectTimeoutMillis(),
                properties.getReadTimeoutMillis());
    }

    /** 按配置间隔上报一次正常心跳，单次失败不影响后续调度。 */
    @Scheduled(fixedRateString = "${interaction.heartbeat.interval-millis:10000}")
    public void report() {
        // 开关关闭时跳过本次上报，不构造请求或访问交互系统。
        if (!properties.isEnabled()) {
            return;
        }

        try {
            // 第一步：从配置和状态枚举构造本次心跳请求。
            InteractionHeartbeatRequest request = buildNormalRequest();

            // 第二步：拼接目标地址并以JSON格式发送心跳。
            String reportUrl = properties.buildReportUrl();
            httpRequestTool.postJson(reportUrl, request, String.class);

            // 第三步：使用信息级别记录成功结果，便于确认定时任务正常执行。
            LOGGER.info(
                    "交互系统心跳上报成功，系统标识：{}，状态：{}",
                    request.getSystemId(),
                    InteractionHeartbeatStatus.NORMAL.getLabel());
        } catch (Exception exception) {
            // 单次失败仅记录异常，等待下一次定时任务继续上报。
            LOGGER.error(
                    "交互系统心跳上报失败，原因：{}",
                    exception.getMessage(),
                    exception);
        }
    }

    /** 构造状态为正常且上报时间为空的心跳请求。 */
    private InteractionHeartbeatRequest buildNormalRequest() {
        // 第一步：校验系统标识，避免发送无法识别的心跳。
        String systemId = properties.getSystemId();
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalStateException("交互系统心跳系统标识不能为空");
        }

        // 第二步：按接口约定设置固定系统标识、正常状态和空时间戳。
        InteractionHeartbeatRequest request = new InteractionHeartbeatRequest();
        request.setSystemId(systemId.trim());
        request.setStatus(InteractionHeartbeatStatus.NORMAL.getCode());
        request.setReportTs(null);
        return request;
    }
}
