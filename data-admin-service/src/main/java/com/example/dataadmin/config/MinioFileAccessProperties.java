package com.example.dataadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 图片和文件查询使用的对象存储访问配置，与处理服务使用同一个存储桶和账号。 */
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioFileAccessProperties {

    /** 浏览器可以访问的对象存储接口根地址，用于生成签名。 */
    private String publicUrl;
    /** 后端签名账号，不作为独立配置字段返回给前端。 */
    private String accessKey;
    /** 后端签名密码，不通过接口返回给前端。 */
    private String secretKey;
    /** 与处理服务上传文件使用的存储桶保持一致。 */
    private String bucketName;
    /** 明确指定签名区域，避免查询列表时再向对象存储请求区域信息。 */
    private String region = "us-east-1";
    /** 图片和文件临时访问地址默认一小时后失效。 */
    private int urlExpirySeconds = 3600;

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getUrlExpirySeconds() { return urlExpirySeconds; }

    /** 有效期遵循对象存储签名限制，配置错误时直接给出中文提示。 */
    public void setUrlExpirySeconds(int urlExpirySeconds) {
        // 签名有效期必须在一秒到七天之间，避免生成不可用的地址。
        if (urlExpirySeconds < 1 || urlExpirySeconds > 604800) {
            throw new IllegalArgumentException("文件访问地址有效期必须为1至604800秒");
        }
        this.urlExpirySeconds = urlExpirySeconds;
    }
}
