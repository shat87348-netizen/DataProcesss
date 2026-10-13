package com.example.simulator.enums;

/**
 * 本模块支持的协议组合，并非重建完整业务枚举。
 * 编码保持与旧管理模块BusinessEnums一致；独立服务不依赖旧管理模块。
 */
public enum ProtocolProfile {
    PDXP(2, 1, "PDXP", "用户数据报传输", 9001, 500L, 1),
    FEP(3, 2, "FEP", "传输控制协议", 19003, 10L, 0);

    public final int protocol;
    public final int transport;
    public final String label;
    public final String transportLabel;
    public final int port;
    public final long interval;
    public final int loop;

    ProtocolProfile(int protocol, int transport, String label, String transportLabel,
                    int port, long interval, int loop) {
        this.protocol = protocol;
        this.transport = transport;
        this.label = label;
        this.transportLabel = transportLabel;
        this.port = port;
        this.interval = interval;
        this.loop = loop;
    }

    /** 限定本次支持的协议范围。 */
    public static ProtocolProfile of(Integer code) {
        for (ProtocolProfile profile : values()) {
            if (Integer.valueOf(profile.protocol).equals(code)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("仅支持PDXP和FEP模拟源");
    }
}

