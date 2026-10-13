package com.example.dataadmin.vo.visualization;

import java.util.ArrayList;
import java.util.List;

/** 导入检查和导入成功结果；检查仅返回统计及中文问题，不执行写入。 */
public class VisualizationConfigPreviewVO {
    /** 待导入组件数量。 */
    private int widgetCount;
    /** 待恢复的参数勾选数量，同一参数在不同组件分别计数。 */
    private int parameterCount;
    /** 待恢复的已有数据项数量。 */
    private int itemCount;
    /** 当前用户目标任务中将被替换的组件数量。 */
    private int existingWidgetCount;
    /** 中文检查问题；非空时禁止导入。 */
    private List<String> errors = new ArrayList<>();

    /** 无问题时允许导入，实际导入仍会重新检查。 */
    public boolean isValid() { return errors.isEmpty(); }
    /** 读取组件数量。 */
    public int getWidgetCount() { return widgetCount; }
    /** 设置组件数量。 */
    public void setWidgetCount(int widgetCount) { this.widgetCount = widgetCount; }
    /** 读取勾选参数数量。 */
    public int getParameterCount() { return parameterCount; }
    /** 设置勾选参数数量。 */
    public void setParameterCount(int parameterCount) { this.parameterCount = parameterCount; }
    /** 读取已有数据项数量。 */
    public int getItemCount() { return itemCount; }
    /** 设置已有数据项数量。 */
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    /** 读取待替换组件数量。 */
    public int getExistingWidgetCount() { return existingWidgetCount; }
    /** 设置待替换组件数量。 */
    public void setExistingWidgetCount(int existingWidgetCount) { this.existingWidgetCount = existingWidgetCount; }
    /** 读取中文检查问题。 */
    public List<String> getErrors() { return errors; }
}
