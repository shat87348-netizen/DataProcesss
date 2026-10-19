package com.example.dataadmin.vo.calibration;

import com.example.dataadmin.entity.CalibRecord;

/** 校准记录列表返回对象，复用汇总记录字段并补充累计校准数量。 */
public class CalibRecordListVO extends CalibRecord {

    /** 当前校准通道累计参与校准的遥测参数数量，包含正常值和野值。 */
    private Long calibrationCount = 0L;

    /** 返回当前通道的累计校准数量。 */
    public Long getCalibrationCount() {
        return calibrationCount;
    }

    /** 设置数据库查询得到的累计校准数量。 */
    public void setCalibrationCount(Long calibrationCount) {
        this.calibrationCount = calibrationCount;
    }
}
