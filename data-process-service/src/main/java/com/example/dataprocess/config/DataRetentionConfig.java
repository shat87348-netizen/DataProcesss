package com.example.dataprocess.config;

import com.example.common.retention.DataRetentionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 处理服务读取遥测数据保留天数，未配置时不清理IoTDB。 */
@Configuration
public class DataRetentionConfig {
    @Bean
    @ConfigurationProperties(prefix = "data-retention")
    public DataRetentionPolicy dataRetentionPolicy() {
        return new DataRetentionPolicy();
    }
}
