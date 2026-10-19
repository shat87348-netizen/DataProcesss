package com.example.dataprocess.protocol.rpc;

import java.util.Arrays;

/** 按当前数据域实际长度拆分后的PDXP数据。 */
public final class PdxpDataPayload {

    /** 数据域开头按配置长度提取的自定义数据。 */
    private final byte[] customData;
    /** 中间遥测原始数据。 */
    private final byte[] data;
    /** 数据域末尾按配置长度提取的遥测帧头。 */
    private final byte[] telemetryHeader;

    /** 保存三个已经完成长度校验的数据区域。 */
    PdxpDataPayload(
            byte[] customData,
            byte[] data,
            byte[] telemetryHeader) {
        this.customData = Arrays.copyOf(customData, customData.length);
        this.data = Arrays.copyOf(data, data.length);
        this.telemetryHeader = Arrays.copyOf(
                telemetryHeader, telemetryHeader.length);
    }

    /** @return 自定义数据副本，长度配置为零时为空数组 */
    public byte[] getCustomData() {
        return Arrays.copyOf(customData, customData.length);
    }

    /** @return 中间遥测原始数据副本 */
    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    /** @return 遥测帧头副本，长度配置为零时为空数组 */
    public byte[] getTelemetryHeader() {
        return Arrays.copyOf(telemetryHeader, telemetryHeader.length);
    }

    /** 还原由遥测帧头和原始数据组成的遥测帧。 */
    public byte[] restoreTelemetryFrame() {
        // 原始结构为实际长度的遥测帧头加数据；没有遥测帧头时只返回数据。
        byte[] restoredFrame = new byte[
                telemetryHeader.length + data.length];
        System.arraycopy(telemetryHeader, 0, restoredFrame,
                0, telemetryHeader.length);
        System.arraycopy(data, 0, restoredFrame,
                telemetryHeader.length, data.length);
        return restoredFrame;
    }
}
