package com.example.dataadmin.dto.calibration;

/**
 * 新增或修改校准通道的请求参数。
 */
public class ChannelSaveRequest {

    /** 校准通道主键 ID；编辑时必填。 */
    private Long id;
    /** 试验任务 ID。 */
    private String taskId;
    /** 校准通道名称。 */
    private String channelName;
    /** 校准公式。 */
    private String calibrationFormula;
    /** 正常数值范围，仅阈值法必填。 */
    private String normalRange;
    /** 异常检测方法：1 莱特准则，2 阈值法，3 肖维涅法。 */
    private Integer detectMethod;
    /** 莱特准则使用的σ倍数。 */
    private Integer sigmaValue;
    /** 阈值法使用的阈值百分比。 */
    private Double fluctuationRate;
    /** 肖维涅法使用的判别常数，固定为0.5。 */
    private Double chauvenetCoef;
    /** 肖维涅法参与计算的最近样本数。 */
    private Integer minSampleCount;
    /** 肖维涅法迭代次数。 */
    private Integer iterateCount;
    /** 莱特准则使用的最近样本窗口大小。 */
    private Integer sampleWindow;
    /** 是否动态更新：0 否，1 是。 */
    private Integer dynamicUpdate;
    /** 是否自动排除异常值：0 否，1 是。 */
    private Integer autoClean;
    /** 是否启用：0 否，1 是。 */
    private Integer enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getCalibrationFormula() {
        return calibrationFormula;
    }

    public void setCalibrationFormula(String calibrationFormula) {
        this.calibrationFormula = calibrationFormula;
    }

    public String getNormalRange() {
        return normalRange;
    }

    public void setNormalRange(String normalRange) {
        this.normalRange = normalRange;
    }

    public Integer getDetectMethod() {
        return detectMethod;
    }

    public void setDetectMethod(Integer detectMethod) {
        this.detectMethod = detectMethod;
    }

    public Integer getSigmaValue() {
        return sigmaValue;
    }

    public void setSigmaValue(Integer sigmaValue) {
        this.sigmaValue = sigmaValue;
    }

    public Double getFluctuationRate() {
        return fluctuationRate;
    }

    public void setFluctuationRate(Double fluctuationRate) {
        this.fluctuationRate = fluctuationRate;
    }

    public Double getChauvenetCoef() {
        return chauvenetCoef;
    }

    public void setChauvenetCoef(Double chauvenetCoef) {
        this.chauvenetCoef = chauvenetCoef;
    }

    public Integer getMinSampleCount() {
        return minSampleCount;
    }

    public void setMinSampleCount(Integer minSampleCount) {
        this.minSampleCount = minSampleCount;
    }

    public Integer getIterateCount() {
        return iterateCount;
    }

    public void setIterateCount(Integer iterateCount) {
        this.iterateCount = iterateCount;
    }

    public Integer getSampleWindow() {
        return sampleWindow;
    }

    public void setSampleWindow(Integer sampleWindow) {
        this.sampleWindow = sampleWindow;
    }

    public Integer getDynamicUpdate() {
        return dynamicUpdate;
    }

    public void setDynamicUpdate(Integer dynamicUpdate) {
        this.dynamicUpdate = dynamicUpdate;
    }

    public Integer getAutoClean() {
        return autoClean;
    }

    public void setAutoClean(Integer autoClean) {
        this.autoClean = autoClean;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

}
