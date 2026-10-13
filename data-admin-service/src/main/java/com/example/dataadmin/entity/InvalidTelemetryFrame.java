package com.example.dataadmin.entity;

import java.time.LocalDateTime;

/** 帧检查结果不为正常的完整采集帧，包括本地参数解析失败的帧。 */
public class InvalidTelemetryFrame {
    /** 设备卫星主键。 */
    private Long deviceSatelliteId;
    /** 消息时间。 */
    private LocalDateTime receiveTime;
    /** 导入工作表名称。 */
    private String deviceSatelliteName;
    /** Proto通道名称；本地解析失败时取设备名称的下划线前缀。 */
    private String channelName;
    /** 帧检查结果中文名称。 */
    private String checkResultName;
    /** 完整帧原码字节数，包含各类头。 */
    private Integer rawLength;
    /** 包含各类头的完整帧原码十六进制文本。 */
    private String rawFrame;

    public Long getDeviceSatelliteId() { return deviceSatelliteId; }
    public void setDeviceSatelliteId(Long value) { this.deviceSatelliteId = value; }
    public LocalDateTime getReceiveTime() { return receiveTime; }
    public void setReceiveTime(LocalDateTime value) { this.receiveTime = value; }
    public String getDeviceSatelliteName() { return deviceSatelliteName; }
    public void setDeviceSatelliteName(String value) { this.deviceSatelliteName = value; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String value) { this.channelName = value; }
    public String getCheckResultName() { return checkResultName; }
    public void setCheckResultName(String value) { this.checkResultName = value; }
    public Integer getRawLength() { return rawLength; }
    public void setRawLength(Integer value) { this.rawLength = value; }
    public String getRawFrame() { return rawFrame; }
    public void setRawFrame(String value) { this.rawFrame = value; }
}
