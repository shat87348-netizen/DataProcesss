package com.example.dataadmin.config;

import com.example.common.retention.DataRetentionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 管理服务读取统一的数据保留策略，所有天数默认未配置。 */
@Configuration
public class DataRetentionConfig {
    @Bean
    @ConfigurationProperties(prefix = "data-retention")
    public DataRetentionPolicy dataRetentionPolicy() {
        return new DataRetentionPolicy();
    }
}
