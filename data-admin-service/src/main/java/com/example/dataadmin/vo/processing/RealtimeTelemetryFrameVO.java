package com.example.dataadmin.vo.processing;

import java.time.LocalDateTime;

/** IoTDB完整采集帧，包括正常处理结果及已入库的异常帧。 */
public class RealtimeTelemetryFrameVO {
    /** 本地设备卫星主键，来自IoTDB路径；RPC独立身份没有此主键，返回空值。 */
    private Long deviceSatelliteId;
    /** 消息时间。 */
    private LocalDateTime time;
    /** 本地导入设备名称；RPC使用返回的卫星名称，缺失时显示卫星代号。 */
    private String deviceSatelliteName;
    /** Proto通道名称；本地解析失败时取设备名称的下划线前缀。 */
    private String channelName;
    /** 完整帧原码字节数，包含各类头。 */
    private Integer rawLength;
    /** 包含各类头的完整帧原码十六进制文本。 */
    private String rawFrame;
    /** 帧检查结果中文名称。 */
    private String checkResultName;

    public Long getDeviceSatelliteId() { return deviceSatelliteId; }
    public void setDeviceSatelliteId(Long value) { this.deviceSatelliteId = value; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime value) { this.time = value; }
    public String getDeviceSatelliteName() { return deviceSatelliteName; }
    public void setDeviceSatelliteName(String value) { this.deviceSatelliteName = value; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String value) { this.channelName = value; }
    public Integer getRawLength() { return rawLength; }
    public void setRawLength(Integer value) { this.rawLength = value; }
    public String getRawFrame() { return rawFrame; }
    public void setRawFrame(String value) { this.rawFrame = value; }
    public String getCheckResultName() { return checkResultName; }
    public void setCheckResultName(String value) { this.checkResultName = value; }
}
