package com.example.dataadmin.storage;

import com.example.dataadmin.config.MinioFileAccessProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/** 使用浏览器可达地址生成只读签名，不修改存储桶权限，也不持久化临时地址。 */
@Component
public class MinioFileUrlSigner {

    /** 文件访问配置只在服务端读取。 */
    private final MinioFileAccessProperties properties;
    /** 客户端延迟创建并复用，未查询文件时不影响其他业务。 */
    private volatile MinioClient signingClient;

    public MinioFileUrlSigner(MinioFileAccessProperties properties) {
        this.properties = properties;
    }

    /** 根据已保存的对象名称生成临时读取地址，历史文件同样适用。 */
    public String createUrl(String objectName) {
        // 第一步：对象名称只来自数据库，不从历史地址中截取，避免旧地址影响签名。
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new IllegalStateException("文件缺少对象名称，无法生成访问地址");
        }
        try {
            // 第二步：直接基于对外地址签名，生成后不再替换域名、端口或访问协议。
            return getSigningClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(requireText(properties.getBucketName(), "文件存储桶不能为空"))
                            .object(objectName)
                            .region(requireText(properties.getRegion(), "文件存储区域不能为空"))
                            .expiry(properties.getUrlExpirySeconds())
                            .build());
        } catch (Exception exception) {
            // 第三步：签名失败时阻止返回无权限的普通地址，具体原因保留给服务端日志。
            throw new IllegalStateException("生成文件临时访问地址失败，请检查对象存储访问配置", exception);
        }
    }

    /** 延迟创建签名客户端，显式区域配置保证签名过程无需访问对象存储网络。 */
    private MinioClient getSigningClient() {
        // 第一步：已经初始化时直接复用；修改连接配置后需重启管理服务。
        if (signingClient != null) {
            return signingClient;
        }
        synchronized (this) {
            // 第二步：再次检查以避免并发查询时重复初始化。
            if (signingClient == null) {
                signingClient = MinioClient.builder()
                        .endpoint(validatePublicUrl(properties.getPublicUrl()))
                        .region(requireText(properties.getRegion(), "文件存储区域不能为空"))
                        .credentials(
                                requireText(properties.getAccessKey(), "文件访问账号不能为空"),
                                requireText(properties.getSecretKey(), "文件访问密码不能为空"))
                        .build();
            }
            return signingClient;
        }
    }

    /** 对外地址必须为对象存储接口根地址，并能用于其他机器访问。 */
    private String validatePublicUrl(String value) {
        // 第一步：校验地址结构，不能把控制台页面、存储桶路径或查询参数写入根地址。
        String publicUrl = requireText(value, "文件对外访问地址不能为空");
        URI uri = URI.create(publicUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();
        if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || host == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                || (path != null && !path.isEmpty() && !"/".equals(path))) {
            throw new IllegalStateException("文件对外访问地址必须为HTTP或HTTPS接口根地址");
        }
        // 第二步：回环地址和通配监听地址不能作为其他机器使用的图片地址。
        String normalizedHost = host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")
                || normalizedHost.startsWith("127.") || "0.0.0.0".equals(normalizedHost)
                || "::".equals(normalizedHost) || "::1".equals(normalizedHost)
                || "0:0:0:0:0:0:0:0".equals(normalizedHost)
                || "0:0:0:0:0:0:0:1".equals(normalizedHost)) {
            throw new IllegalStateException("文件对外访问地址必须使用其他机器可达的IP或域名");
        }
        // 第三步：只清理根地址末尾斜线，对象路径统一由客户端编码。
        return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** 必填配置为空时明确报告中文原因。 */
    private String requireText(String value, String message) {
        // 配置项去除首尾空白，不改变实际内容。
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }
}
