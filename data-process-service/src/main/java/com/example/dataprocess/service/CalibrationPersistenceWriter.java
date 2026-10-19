package com.example.dataprocess.service;

import com.example.common.calibration.CalibrationCountCache;
import com.example.common.calibration.CalibrationRecordDetailCache;

import java.util.List;

/** 在一个数据库事务中写入一批校准样本。 */
public interface CalibrationPersistenceWriter {
    /** 同一事务内保存野值详情，并累计野值和参与校准的参数数量。 */
    void write(
            List<CalibrationRecordDetailCache> details,
            List<CalibrationCountCache> calibrationCounts);
}
