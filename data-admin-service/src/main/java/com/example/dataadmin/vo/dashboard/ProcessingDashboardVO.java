package com.example.dataadmin.vo.dashboard;

import java.util.ArrayList;
import java.util.List;

/** 数据处理总览：汇总卡片、三类处理曲线和表格。 */
public class ProcessingDashboardVO {
  /** 试验任务ID。 */
  private String taskId;

  /** 查询时间范围。 */
  private String range;

  /** 实时指标最后更新时间。 */
  private String sampleTime;

  /** 页面顶部汇总卡片数据。 */
  private Summary summary = new Summary();

  /** 按发送方分组的处理量时间序列。 */
  private LineChart lineChart = new LineChart();

  /** 按发送方聚合的处理明细。 */
  private List<TableRow> table = new ArrayList<>();

  public String getTaskId() { return taskId; }
  public void setTaskId(String v) { taskId = v; }
  public String getRange() { return range; }
  public void setRange(String v) { range = v; }
  public String getSampleTime() { return sampleTime; }
  public void setSampleTime(String v) { sampleTime = v; }

  public Summary getSummary() {
    return summary;
  }

  public void setSummary(Summary v) {
    summary = v;
  }

  public LineChart getLineChart() {
    return lineChart;
  }

  public void setLineChart(LineChart v) {
    lineChart = v;
  }

  public List<TableRow> getTable() {
    return table;
  }

  public void setTable(List<TableRow> v) {
    table = v;
  }

  /** 数据处理汇总卡片。 */
  public static class Summary {
    /** 当前未处理告警数量。 */
    private long unhandledAlarmCount;

    /** 查询时间范围内成功处理的数据总条数。 */
    private long processedCount;

    /** 查询时间范围内异常数据总条数。 */
    private long abnormalCount;

    public long getUnhandledAlarmCount() {
      return unhandledAlarmCount;
    }

    public void setUnhandledAlarmCount(long v) {
      unhandledAlarmCount = v;
    }

    public long getProcessedCount() {
      return processedCount;
    }

    public void setProcessedCount(long v) {
      processedCount = v;
    }

    public long getAbnormalCount() {
      return abnormalCount;
    }

    public void setAbnormalCount(long v) {
      abnormalCount = v;
    }
  }

  /** 折线图横轴和数据序列。 */
  public static class LineChart {
    /** 各统计区间的结束时间，最后一个滚动点保留秒数，与序列值严格对应。 */
    private List<String> timePoints = new ArrayList<>();

    /** 最新滚动点的统计起点，使用完整本地时间，包含此时刻。 */
    private String latestWindowStartTime;

    /** 最新滚动点的统计截止时间，使用完整本地时间，不包含此时刻。 */
    private String latestWindowEndTime;

    /** 每个发送方的一条处理量曲线。 */
    private List<Series> series = new ArrayList<>();

    public List<String> getTimePoints() {
      return timePoints;
    }

    public void setTimePoints(List<String> v) {
      timePoints = v;
    }

    /** 返回最新滚动点的统计起点，供前端展示实际覆盖范围。 */
    public String getLatestWindowStartTime() {
      return latestWindowStartTime;
    }

    /** 设置最新滚动点的统计起点。 */
    public void setLatestWindowStartTime(String v) {
      latestWindowStartTime = v;
    }

    /** 返回扣除落库缓冲后的最新统计截止时间。 */
    public String getLatestWindowEndTime() {
      return latestWindowEndTime;
    }

    /** 设置最新滚动点的统计截止时间。 */
    public void setLatestWindowEndTime(String v) {
      latestWindowEndTime = v;
    }

    public List<Series> getSeries() {
      return series;
    }

    public void setSeries(List<Series> v) {
      series = v;
    }
  }

  /** 单个发送方的处理量曲线。 */
  public static class Series {
    /** 发送方系统名称。 */
    private String systemName;

    /** 各时间点内累计的成功处理条数。 */
    private List<Long> values = new ArrayList<>();

    public String getSystemName() {
      return systemName;
    }

    public void setSystemName(String v) {
      systemName = v;
    }

    public List<Long> getValues() {
      return values;
    }

    public void setValues(List<Long> v) {
      values = v;
    }
  }

  /** 按发送方聚合的表格行。 */
  public static class TableRow {
    /** 发送方系统名称。 */
    private String systemName;

    /** 查询时间范围内成功处理的数据条数。 */
    private long processedCount;

    /** 查询时间范围内去除的重复数据条数。 */
    private long deduplicatedCount;

    /** 查询时间范围内异常数据条数。 */
    private long abnormalCount;

    public String getSystemName() {
      return systemName;
    }

    public void setSystemName(String v) {
      systemName = v;
    }

    public long getProcessedCount() {
      return processedCount;
    }

    public void setProcessedCount(long v) {
      processedCount = v;
    }

    public long getDeduplicatedCount() {
      return deduplicatedCount;
    }

    public void setDeduplicatedCount(long v) {
      deduplicatedCount = v;
    }

    public long getAbnormalCount() {
      return abnormalCount;
    }

    public void setAbnormalCount(long v) {
      abnormalCount = v;
    }
  }
}
