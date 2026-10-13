package com.example.dataadmin.entity;

import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.EnumData;

import java.time.LocalDateTime;

/**
 * 校准通道配置表实体。
 *
 * 对应数据库表 calib_channel_config，用于承载业务数据和MyBatis查询结果。
 */
public class CalibChannelConfig {
    /** 自增主键ID。 */
    private Long id;
    /** 试验任务ID。 */
    private String taskId;
    /** 通道名称。 */
    private String channelName;
    /** 校准公式。 */
    private String calibrationFormula;
    /** 正常范围，如0~36V，仅阈值法必填。 */
    private String normalRange;
    /** 野值检测方法：1莱特准则 2阈值法 3肖维涅法。 */
    private Integer detectMethod;
    /** σ倍数，用于莱特准则，如3表示3σ。 */
    private Integer sigmaValue;
    /** 波动阈值百分比，用于阈值法，如10.5表示10.5%。 */
    private Double fluctuationRate;
    /** 肖维涅判别常数，固定为0.5。 */
    private Double chauvenetCoef;
    /** 肖维涅法参与计算的最近样本数。 */
    private Integer minSampleCount;
    /** 肖维涅法迭代次数。 */
    private Integer iterateCount;
    /** 莱特准则使用的最近样本窗口。 */
    private Integer sampleWindow;
    /** 是否动态更新：0否 1是。 */
    private Integer dynamicUpdate;
    /** 是否自动排除异常值：0否 1是。 */
    private Integer autoClean;
    /** 是否启用通道：0否 1是。 */
    private Integer enabled;
    /** 通道状态：0停止 1正常 2异常。 */
    private Integer channelStatus;
    /** 是否删除：0否 1是。 */
    private Integer isDeleted;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;

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

    /** 返回野值检测方法中文名称。 */
    public String getDetectMethodName() {
        return EnumData.labelOf(BusinessEnums.DetectMethod.values(), detectMethod);
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

    /** 返回动态更新状态中文名称。 */
    public String getDynamicUpdateName() {
        return EnumData.labelOf(BusinessEnums.YesNo.values(), dynamicUpdate);
    }

    public Integer getAutoClean() {
        return autoClean;
    }

    public void setAutoClean(Integer autoClean) {
        this.autoClean = autoClean;
    }

    /** 返回自动清洗状态中文名称。 */
    public String getAutoCleanName() {
        return EnumData.labelOf(BusinessEnums.YesNo.values(), autoClean);
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    /** 返回通道启用状态中文名称。 */
    public String getEnabledName() {
        return EnumData.labelOf(BusinessEnums.Enabled.values(), enabled);
    }

    public Integer getChannelStatus() {
        return channelStatus;
    }

    public void setChannelStatus(Integer channelStatus) {
        this.channelStatus = channelStatus;
    }

    /** 返回通道运行状态中文名称。 */
    public String getChannelStatusName() {
        return EnumData.labelOf(BusinessEnums.ChannelStatus.values(), channelStatus);
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
