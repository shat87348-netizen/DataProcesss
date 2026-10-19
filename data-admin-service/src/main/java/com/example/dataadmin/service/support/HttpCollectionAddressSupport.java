package com.example.dataadmin.service.support;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/** 统一HTTP采集地址的规范化、校验和展示拼接，保存与回显复用同一规则。 */
public final class HttpCollectionAddressSupport {

    /** 接收路径使用统一业务前缀，避免与管理接口路径混用。 */
    public static final String RECEIVE_PATH_PREFIX = "/collection/receive/";

    /** 工具类只提供静态方法。 */
    private HttpCollectionAddressSupport() {
    }

    /** 规范化对外地址，不执行域名解析或网络连通性检查。 */
    public static String normalizeHost(String host) {
        // 第一步：地址字段只保存IP或域名，拒绝协议、路径及地址中的空白。
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("HTTP接收地址不能为空且不能超过100个字符");
        }
        // 第二步：IPv6只解析字面地址，不允许作用域标识，也不调用域名查询。
        if (normalized.contains(":")) {
            if (!normalized.matches("[0-9a-f:]+")) {
                throw invalidHost();
            }
            try {
                InetAddress address = InetAddress.getByName(normalized);
                if (address.isAnyLocalAddress()) {
                    throw new IllegalArgumentException("HTTP对外接收地址不能使用通配监听地址");
                }
                return address.getHostAddress();
            } catch (UnknownHostException exception) {
                throw invalidHost();
            }
        }
        // 第三步：IPv4逐段检查范围，通配地址不能作为发送方访问的目标。
        if (normalized.matches("[0-9.]+")) {
            String[] parts = normalized.split("\\.", -1);
            if (parts.length != 4) {
                throw invalidHost();
            }
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) {
                    throw invalidHost();
                }
            }
            if ("0.0.0.0".equals(normalized)) {
                throw new IllegalArgumentException("HTTP对外接收地址不能使用0.0.0.0");
            }
            return normalized;
        }
        // 第四步：逐个域名片段校验字符和长度，允许开发环境使用单段主机名。
        for (String label : normalized.split("\\.", -1)) {
            if (label.length() > 63 || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                throw invalidHost();
            }
        }
        return normalized;
    }

    /** 规范化HTTP接收路径，保证存储路径与请求路由有唯一、明确的表示。 */
    public static String normalizeReceivePath(String receivePath) {
        // 第一步：去除两端空白，限制路径前缀、总长度与可使用字符。
        String normalized = receivePath == null ? "" : receivePath.trim();
        if (!normalized.startsWith(RECEIVE_PATH_PREFIX)
                || normalized.length() <= RECEIVE_PATH_PREFIX.length()
                || normalized.length() > 255) {
            throw new IllegalArgumentException("HTTP接收路径必须以/collection/receive/开头并填写路径名称，长度不能超过255个字符");
        }
        if (!normalized.matches("/[A-Za-z0-9/_.-]+")) {
            throw new IllegalArgumentException("HTTP接收路径只能包含英文字母、数字、斜杠、下划线、短横线和点号");
        }
        // 第二步：拒绝空路径段、末尾斜杠和相对路径，避免同一路由存在不同写法。
        for (String segment : normalized.substring(1).split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("HTTP接收路径不能包含连续斜杠、末尾斜杠或相对路径段");
            }
        }
        return normalized;
    }

    /** 为已保存配置计算完整接收地址，历史配置缺少路径时不生成虚构地址。 */
    public static String receiveUrl(String scheme, String host, Integer port, String receivePath) {
        // 第一步：历史HTTP记录尚未补齐必需字段时返回空，允许前端重新编辑。
        if (host == null || host.trim().isEmpty() || port == null
                || receivePath == null || receivePath.trim().isEmpty()) {
            return null;
        }
        // 第二步：使用标准地址构造器处理IPv6括号，统一输出可复制的完整地址。
        try {
            return new URI(scheme, null, host, port, receivePath, null, null).toASCIIString();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    /** 对不同地址格式错误复用一致的中文提示。 */
    private static IllegalArgumentException invalidHost() {
        return new IllegalArgumentException("HTTP接收地址必须是合法IP或域名，不能包含协议、路径或端口");
    }
}
