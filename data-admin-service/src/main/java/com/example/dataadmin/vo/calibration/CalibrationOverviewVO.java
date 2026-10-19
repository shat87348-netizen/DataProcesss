package com.example.dataadmin.vo.calibration;

/** 数据校准页面累计校准、清洗及野值数量总览。 */
public class CalibrationOverviewVO {

    /** 当前试验任务累计参与校准的遥测参数数量，包括正常值和野值。 */
    private Long calibrationCount = 0L;

    /** 当前试验任务累计清洗数量，与累计野值数量保持一致。 */
    private Long cleanedCount = 0L;

    /** 当前试验任务累计检出的野值数量。 */
    private Long outlierCount = 0L;

    /** 返回累计参与校准的遥测参数数量。 */
    public Long getCalibrationCount() {
        return calibrationCount;
    }

    /** 设置数据库汇总得到的累计校准数量。 */
    public void setCalibrationCount(Long calibrationCount) {
        this.calibrationCount = calibrationCount;
    }

    /** 返回与野值数量相同的累计清洗数量。 */
    public Long getCleanedCount() {
        return cleanedCount;
    }

    /** 设置复用野值统计结果的累计清洗数量。 */
    public void setCleanedCount(Long cleanedCount) {
        this.cleanedCount = cleanedCount;
    }

    /** 返回累计检出的野值数量。 */
    public Long getOutlierCount() {
        return outlierCount;
    }

    /** 设置数据库汇总得到的累计野值数量。 */
    public void setOutlierCount(Long outlierCount) {
        this.outlierCount = outlierCount;
    }
}
