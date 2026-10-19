package com.example.common.calibration;

/** 待批量落库的校准参数数量，每条消息按校准通道合并。 */
public class CalibrationCountCache {

    /** 试验任务编号，用于累计数量的任务隔离。 */
    private String taskId;

    /** 校准通道主键，定位该通道的累计汇总记录。 */
    private Long channelId;

    /** 采集接口主键，用于落库失败时记录接口告警。 */
    private Long interfaceId;

    /** 本条消息中完成校准处理的遥测参数数量，包含正常值和野值。 */
    private Long calibrationCount;

    /** 返回试验任务编号。 */
    public String getTaskId() {
        return taskId;
    }

    /** 设置试验任务编号。 */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /** 返回校准通道主键。 */
    public Long getChannelId() {
        return channelId;
    }

    /** 设置校准通道主键。 */
    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    /** 返回采集接口主键。 */
    public Long getInterfaceId() {
        return interfaceId;
    }

    /** 设置采集接口主键。 */
    public void setInterfaceId(Long interfaceId) {
        this.interfaceId = interfaceId;
    }

    /** 返回本次参与校准的遥测参数数量。 */
    public Long getCalibrationCount() {
        return calibrationCount;
    }

    /** 设置本次参与校准的遥测参数数量。 */
    public void setCalibrationCount(Long calibrationCount) {
        this.calibrationCount = calibrationCount;
    }
}
