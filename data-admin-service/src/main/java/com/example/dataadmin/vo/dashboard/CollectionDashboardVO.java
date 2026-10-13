package com.example.dataadmin.vo.dashboard;

import java.util.ArrayList;
import java.util.List;

/** 数据采集总览：实时汇总、折线图和表格。 */
public class CollectionDashboardVO {
  /** 试验任务ID。 */
  private String taskId;

  /** 查询时间范围。 */
  private String range;

  /** 实时指标最后更新时间。 */
  private String sampleTime;

  /** 页面顶部汇总卡片数据。 */
  private Summary summary = new Summary();

  /** 按发送方分组的采集量时间序列。 */
  private LineChart lineChart = new LineChart();

  /** 按发送方聚合的采集明细。 */
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

  /** 数据采集汇总卡片。 */
  public static class Summary {
    /** 至少有一个在线采集接口的发送方数量。 */
    private long onlineAgentCount;

    /** 发送方总数。 */
    private long senderCount;

    /** 查询时间范围内的采集总条数。 */
    private long collectionCount;

    /** 各采集接口最新接口流量之和，单位KB/s。 */
    private double interfaceRateKbps;

    public long getOnlineAgentCount() {
      return onlineAgentCount;
    }

    public void setOnlineAgentCount(long v) {
      onlineAgentCount = v;
    }

    public long getSenderCount() {
      return senderCount;
    }

    public void setSenderCount(long v) {
      senderCount = v;
    }

    public long getCollectionCount() {
      return collectionCount;
    }

    public void setCollectionCount(long v) {
      collectionCount = v;
    }

    public double getInterfaceRateKbps() {
      return interfaceRateKbps;
    }

    public void setInterfaceRateKbps(double v) {
      interfaceRateKbps = v;
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

    /** 每个发送方的一条采集量曲线。 */
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

  /** 单个发送方的采集量曲线。 */
  public static class Series {
    /** 系统名称。 */
    private String systemName;

    /** 各时间点内累计的采集条数。 */
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
    /** 系统名称。 */
    private String systemName;

    /** 该发送方配置的采集接口数量。 */
    private int interfaceCount;

    /** 该发送方使用的协议集合。 */
    private List<String> protocols = new ArrayList<>();

    /** 查询时间范围内的采集条数。 */
    private long collectionCount;

    /** 该发送方采集量占总采集量的百分比。 */
    private double percentage;

    /** 该发送方所有接口最新流量之和，单位KB/s。 */
    private double interfaceRateKbps;

    /** 状态中文名称。 */
    private String statusName;

    public String getSystemName() {
      return systemName;
    }

    public void setSystemName(String v) {
      systemName = v;
    }

    public int getInterfaceCount() {
      return interfaceCount;
    }

    public void setInterfaceCount(int v) {
      interfaceCount = v;
    }

    public List<String> getProtocols() {
      return protocols;
    }

    public void setProtocols(List<String> v) {
      protocols = v;
    }

    public long getCollectionCount() {
      return collectionCount;
    }

    public void setCollectionCount(long v) {
      collectionCount = v;
    }

    public double getPercentage() { return percentage; }
    public void setPercentage(double v) { percentage = v; }

    public double getInterfaceRateKbps() {
      return interfaceRateKbps;
    }

    public void setInterfaceRateKbps(double v) {
      interfaceRateKbps = v;
    }

    public String getStatusName() {
      return statusName;
    }

    public void setStatusName(String v) {
      statusName = v;
    }
  }
}
