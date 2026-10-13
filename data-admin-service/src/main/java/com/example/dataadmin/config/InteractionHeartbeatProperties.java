package com.example.dataadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 交互系统心跳上报配置。 */
@Component
@ConfigurationProperties(prefix = "interaction.heartbeat")
public class InteractionHeartbeatProperties {

    /** 是否开启交互系统心跳上报，默认开启。 */
    private boolean enabled = true;

    /** 交互系统接口协议。 */
    private String scheme = "http";

    /** 交互系统服务IP。 */
    private String host = "10.126.100.247";

    /** 交互系统服务端口。 */
    private int port = 8063;

    /** 交互系统接口上下文路径。 */
    private String contextPath = "/inst2";

    /** 心跳上报接口路径。 */
    private String reportPath = "/wjp-admin/kzt/status/report";

    /** 当前系统在交互系统中的标识。 */
    private String systemId = "sjcl";

    /** 心跳上报间隔，单位毫秒。 */
    private long intervalMillis = 10_000L;

    /** HTTP连接超时时间，单位毫秒。 */
    private int connectTimeoutMillis = 5_000;

    /** HTTP响应读取超时时间，单位毫秒。 */
    private int readTimeoutMillis = 10_000;

    /** 返回交互系统心跳上报开关。 */
    public boolean isEnabled() {
        // 读取当前配置中的开关状态。
        return enabled;
    }

    /** 设置交互系统心跳上报开关。 */
    public void setEnabled(boolean enabled) {
        // 保存配置绑定的开关状态。
        this.enabled = enabled;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /** 组合生成完整的心跳上报地址。 */
    public String buildReportUrl() {
        // 第一步：清理协议和主机配置两端的空白字符。
        String actualScheme = requireText(scheme, "交互系统接口协议");
        String actualHost = requireText(host, "交互系统服务IP");

        // 第二步：校验端口范围，避免生成无法访问的接口地址。
        if (port <= 0 || port > 65_535) {
            throw new IllegalStateException("交互系统服务端口必须在1到65535之间");
        }

        // 第三步：统一路径斜线后拼接完整接口地址。
        return actualScheme
                + "://"
                + actualHost
                + ":"
                + port
                + normalizePath(contextPath)
                + normalizePath(reportPath);
    }

    /** 校验并返回去除首尾空白的必填配置。 */
    private String requireText(String value, String fieldName) {
        // 配置为空时立即提示具体缺失项。
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    /** 将非空路径统一转换为单个正斜线开头且不以斜线结尾。 */
    private String normalizePath(String path) {
        // 空路径不参与最终地址拼接。
        if (path == null || path.trim().isEmpty() || "/".equals(path.trim())) {
            return "";
        }

        // 清理路径两端多余斜线，防止地址中出现重复分隔符。
        String normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return "/" + normalized;
    }
}
