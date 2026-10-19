package com.example.dataadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** HTTP采集地址的系统级展示配置，不为每个采集接口重复保存访问协议。 */
@Component
@ConfigurationProperties(prefix = "data-processing.http")
public class HttpCollectionProperties {

    /** 默认使用普通HTTP，部署HTTPS入口时由配置中心统一修改。 */
    private String scheme = "http";

    /** 返回完整接收地址使用的访问协议。 */
    public String getScheme() {
        return scheme;
    }

    /** 统一校验配置值，避免生成无法使用的接收地址。 */
    public void setScheme(String scheme) {
        // 清理配置空白并统一小写，只接受两种实际支持的访问协议。
        String normalized = scheme == null ? "" : scheme.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"http".equals(normalized) && !"https".equals(normalized)) {
            throw new IllegalArgumentException("HTTP采集地址访问协议只能配置为http或https");
        }
        this.scheme = normalized;
    }
}
