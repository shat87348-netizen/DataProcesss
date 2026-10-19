package com.example.dataadmin.vo.processing;

import java.time.LocalDateTime;

/** IoTDB处理后参数的页面返回结构。 */
public class ProcessedTelemetryVO {
    /** 已保存勾选记录所属的设备卫星主键。 */
    private Long deviceSatelliteId;
    /** 路径末级遥测代号。 */
    private String telemetryCode;
    /** Proto参数名称。 */
    private String parameter;
    /** Proto数值。 */
    private Double parameterValue;
    /** Proto状态中文名称。 */
    private String status;
    /** 实际处理时间。 */
    private LocalDateTime processTime;

    public Long getDeviceSatelliteId() { return deviceSatelliteId; }
    public void setDeviceSatelliteId(Long value) { this.deviceSatelliteId = value; }
    public String getTelemetryCode() { return telemetryCode; }
    public void setTelemetryCode(String value) { this.telemetryCode = value; }
    public String getParameter() { return parameter; }
    public void setParameter(String value) { this.parameter = value; }
    public Double getParameterValue() { return parameterValue; }
    public void setParameterValue(Double value) { this.parameterValue = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getProcessTime() { return processTime; }
    public void setProcessTime(LocalDateTime value) { this.processTime = value; }
}
