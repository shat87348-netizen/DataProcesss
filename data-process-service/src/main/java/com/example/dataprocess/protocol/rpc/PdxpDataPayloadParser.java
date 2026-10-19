package com.example.dataprocess.protocol.rpc;

import java.util.Arrays;

/** 按实际长度拆分PDXP数据域。 */
public final class PdxpDataPayloadParser {

    /** 自定义数据默认长度。 */
    public static final int CUSTOM_DATA_LENGTH = 5;
    /** 遥测帧头默认长度。 */
    public static final int TELEMETRY_HEADER_LENGTH = 4;

    /** 工具类不允许实例化。 */
    private PdxpDataPayloadParser() {
    }

    /** 保留默认五字节自定义数据和四字节遥测帧头的调用入口。 */
    public static PdxpDataPayload parse(byte[] payload) {
        return parse(payload, CUSTOM_DATA_LENGTH, TELEMETRY_HEADER_LENGTH);
    }

    /** 按配置长度拆分，长度为零的部分返回空数组。 */
    public static PdxpDataPayload parse(byte[] payload,
            int customDataLength, int telemetryHeaderLength) {
        // 第一步：拒绝负数配置，并按实际配置检查最小数据域长度。
        if (customDataLength < 0 || telemetryHeaderLength < 0) {
            throw new IllegalArgumentException("自定义数据和遥测帧头字节数不能为负数");
        }
        long minimumPayloadLength = (long) customDataLength + telemetryHeaderLength;
        if (payload == null || payload.length < minimumPayloadLength) {
            throw new IllegalArgumentException(
                    "PDXP数据域长度不能少于" + minimumPayloadLength
                            + "字节，实际长度："
                            + (payload == null ? 0 : payload.length));
        }

        // 第二步：按配置取得数据域开头的自定义数据，零长度不消耗任何字节。
        byte[] customData = Arrays.copyOfRange(
                payload, 0, customDataLength);
        // 第三步：根据实际数据域长度计算中间遥测数据长度。
        int telemetryDataEnd = payload.length - telemetryHeaderLength;
        // 第四步：中间全部数据作为遥测原始数据。
        byte[] data = Arrays.copyOfRange(
                payload,
                customDataLength,
                telemetryDataEnd);
        // 第五步：末尾按配置取得遥测帧头，零长度时保留空数组。
        byte[] telemetryHeader = Arrays.copyOfRange(
                payload,
                telemetryDataEnd,
                payload.length);
        return new PdxpDataPayload(
                customData, data, telemetryHeader);
    }
}
