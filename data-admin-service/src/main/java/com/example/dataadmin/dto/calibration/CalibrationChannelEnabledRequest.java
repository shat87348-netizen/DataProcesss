package com.example.dataadmin.dto.calibration;

import com.example.dataadmin.dto.collection.EnabledUpdateRequest;
import javax.validation.constraints.NotBlank;

/** 校准通道启用切换参数，复用已有主键和启用状态校验。 */
public class CalibrationChannelEnabledRequest extends EnabledUpdateRequest {

    /** 外部试验任务编号，用于限定本次操作所属任务。 */
    @NotBlank(message = "试验任务编号不能为空")
    private String taskId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
