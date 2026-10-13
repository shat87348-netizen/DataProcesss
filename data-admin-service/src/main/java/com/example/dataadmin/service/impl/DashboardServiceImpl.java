package com.example.dataadmin.service.impl;

import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.CollectInterfaceStatistics;
import com.example.dataadmin.entity.DataProcessingStatistics;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.DashboardRange;
import com.example.dataadmin.enums.EnumData;
import com.example.dataadmin.mapper.DashboardMapper;
import com.example.dataadmin.service.DashboardService;
import com.example.dataadmin.vo.dashboard.CollectionDashboardVO;
import com.example.dataadmin.vo.dashboard.ProcessingDashboardVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 总览查询、Redis实时指标读取及历史曲线聚合实现。 */
@Service
public class DashboardServiceImpl implements DashboardService {
  /** 采集实时指标的Redis键前缀。 */
  private static final String COLLECTION_KEY_PREFIX = "dashboard:collection:current:";
  /** 处理实时指标的Redis键前缀。 */
  private static final String PROCESSING_KEY_PREFIX = "dashboard:processing:current:";
  /** 处理指标中未处理告警数量的字段名。 */
  private static final String UNHANDLED_ALARM_FIELD = "unhandledAlarmCount";
  /** 数据库统计窗口为五秒，曲线截止时间必须对齐窗口边界。 */
  private static final long STATISTICS_WINDOW_SECONDS = 5L;
  /** 为秒级采样和五秒落库任务预留十秒缓冲，不代表故障时的落库完成水位。 */
  private static final long CHART_FLUSH_BUFFER_SECONDS = 10L;

  private final DashboardMapper mapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public DashboardServiceImpl(DashboardMapper mapper, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  /** 按指定任务和时间范围组装数据采集总览。 */
  @Override
  public CollectionDashboardVO getCollectionDashboard(String taskId, DashboardRange range) {
    RangeSpec spec = range(range);
    List<CollectInterfaceConfig> interfaces = mapper.findInterfaces(taskId);
    List<CollectInterfaceStatistics> points =
        mapper.findCollectionStatisticsRange(taskId, spec.queryStart);
    // 曲线可能额外查询边界前的数据，卡片和表格仍只汇总原始查询范围。
    List<CollectInterfaceStatistics> summaryPoints = points.stream()
        .filter(point -> !point.getStatisticsTime().isBefore(spec.start))
        .collect(Collectors.toList());
    Map<Long, RealtimeCollectionMetric> realtime = collectionRealtime(taskId);
    Map<Long, CollectInterfaceConfig> interfaceMap = interfaceMap(interfaces);
    Map<String, Long> totals = collectionTotals(summaryPoints, interfaceMap);

    CollectionDashboardVO result = new CollectionDashboardVO();
    result.setTaskId(taskId);
    result.setRange(spec.value);
    result.setSampleTime(latestCollectionTime(realtime));
    result.setLineChart(collectionChart(points, interfaceMap, spec));
    result.setTable(collectionTable(interfaces, totals, realtime));

    CollectionDashboardVO.Summary summary = result.getSummary();
    summary.setCollectionCount(sum(totals));
    summary.setSenderCount(result.getTable().size());
    summary.setOnlineAgentCount(onlineInterfaceCount(interfaces));
    summary.setInterfaceRateKbps(round(collectionInterfaceRate(realtime)));
    return result;
  }

  /** 按指定任务和时间范围组装数据处理总览。 */
  @Override
  public ProcessingDashboardVO getProcessingDashboard(String taskId, DashboardRange range) {
    RangeSpec spec = range(range);
    List<CollectInterfaceConfig> interfaces = mapper.findInterfaces(taskId);
    List<DataProcessingStatistics> points =
        mapper.findProcessingStatisticsRange(taskId, spec.queryStart);
    // 最新滚动点允许与历史点重叠，总量必须按原始记录独立计算。
    List<DataProcessingStatistics> summaryPoints = points.stream()
        .filter(point -> !point.getStatisticsTime().isBefore(spec.start))
        .collect(Collectors.toList());
    ProcessingRealtime realtime = processingRealtime(taskId);

    ProcessingDashboardVO result = new ProcessingDashboardVO();
    result.setTaskId(taskId);
    result.setRange(spec.value);
    result.setSampleTime(realtime.sampleTime);
    result.setLineChart(processingChart(points, interfaceMap(interfaces), spec));
    result.setTable(processingTable(summaryPoints, interfaceMap(interfaces)));

    ProcessingDashboardVO.Summary summary = result.getSummary();
    summary.setProcessedCount(sumProcessed(summaryPoints));
    summary.setAbnormalCount(sumAbnormal(summaryPoints));
    summary.setUnhandledAlarmCount(realtime.unhandledAlarmCount == null
        ? mapper.countUnhandledAlarms(taskId) : realtime.unhandledAlarmCount);
    return result;
  }

  /** 从Redis读取任务下所有采集接口的实时指标。 */
  private Map<Long, RealtimeCollectionMetric> collectionRealtime(String taskId) {
    Map<Long, RealtimeCollectionMetric> result = new LinkedHashMap<>();
    if (taskId == null) { return result; }
    try {
      Map<Object, Object> entries = redisTemplate.opsForHash().entries(COLLECTION_KEY_PREFIX + taskId);
      for (Map.Entry<Object, Object> entry : entries.entrySet()) {
        Long interfaceId = collectionInterfaceId(String.valueOf(entry.getKey()));
        RealtimeCollectionMetric metric = readMetric(entry.getValue(), RealtimeCollectionMetric.class);
        if (interfaceId != null && metric != null) { result.put(interfaceId, metric); }
      }
    } catch (RuntimeException ignored) {
      // Redis暂时不可用时保留历史曲线查询能力，实时指标按0返回。
    }
    return result;
  }

  /** 从Redis读取任务下各采集接口的实时处理指标。 */
  private ProcessingRealtime processingRealtime(String taskId) {
    ProcessingRealtime result = new ProcessingRealtime();
    if (taskId == null) { return result; }
    try {
      Map<Object, Object> entries = redisTemplate.opsForHash().entries(PROCESSING_KEY_PREFIX + taskId);
      for (Map.Entry<Object, Object> entry : entries.entrySet()) {
        String field = String.valueOf(entry.getKey());
        if (UNHANDLED_ALARM_FIELD.equals(field)) {
          result.unhandledAlarmCount = longValue(entry.getValue());
        } else if (processingMetricField(field)) {
          RealtimeProcessingMetric metric = readMetric(entry.getValue(), RealtimeProcessingMetric.class);
          if (metric != null) {
            result.metrics.add(metric);
            result.sampleTime = later(result.sampleTime, metric.getSampleTime());
          }
        }
      }
    } catch (RuntimeException ignored) {
      // Redis暂时不可用时由金仓和告警表继续提供可查询数据。
    }
    return result;
  }

  /** 历史采集点按完整固定区间汇总，最新点按相同跨度滚动汇总。 */
  private CollectionDashboardVO.LineChart collectionChart(List<CollectInterfaceStatistics> points,
      Map<Long, CollectInterfaceConfig> interfaceMap, RangeSpec spec) {
    List<Long> buckets = buckets(spec);
    Map<String, long[]> values = new LinkedHashMap<>();
    for (CollectInterfaceStatistics point : points) {
      String name = sender(interfaceMap.get(point.getInterfaceId()));
      long[] data = values.computeIfAbsent(name, key -> new long[buckets.size()]);
      // 同一记录可以同时属于历史区间和最新滚动区间，两者分别累计。
      addChartValue(data, point.getStatisticsTime(), zero(point.getCollectionCount()), spec);
    }
    CollectionDashboardVO.LineChart chart = new CollectionDashboardVO.LineChart();
    chart.setTimePoints(labels(buckets, spec));
    chart.setLatestWindowStartTime(chartTime(spec.rollingStart));
    chart.setLatestWindowEndTime(chartTime(spec.chartEnd));
    List<CollectionDashboardVO.Series> series = new ArrayList<>();
    for (Map.Entry<String, long[]> entry : values.entrySet()) {
      CollectionDashboardVO.Series item = new CollectionDashboardVO.Series();
      item.setSystemName(entry.getKey());
      item.setValues(box(entry.getValue()));
      series.add(item);
    }
    chart.setSeries(series);
    return chart;
  }

  /** 历史处理点按完整固定区间汇总，最新点按相同跨度滚动汇总。 */
  private ProcessingDashboardVO.LineChart processingChart(List<DataProcessingStatistics> points,
      Map<Long, CollectInterfaceConfig> interfaceMap, RangeSpec spec) {
    List<Long> buckets = buckets(spec);
    Map<String, long[]> values = new LinkedHashMap<>();
    for (DataProcessingStatistics point : points) {
      // 为查询范围内的发送方保留序列，区间没有数据时正常返回零值。
      String name = sender(interfaceMap.get(point.getInterfaceId()));
      long[] data = values.computeIfAbsent(name, key -> new long[buckets.size()]);
      addChartValue(data, point.getStatisticsTime(), zero(point.getProcessingCount()), spec);
    }
    ProcessingDashboardVO.LineChart chart = new ProcessingDashboardVO.LineChart();
    chart.setTimePoints(labels(buckets, spec));
    chart.setLatestWindowStartTime(chartTime(spec.rollingStart));
    chart.setLatestWindowEndTime(chartTime(spec.chartEnd));
    List<ProcessingDashboardVO.Series> series = new ArrayList<>();
    for (Map.Entry<String, long[]> entry : values.entrySet()) {
      ProcessingDashboardVO.Series item = new ProcessingDashboardVO.Series();
      item.setSystemName(entry.getKey());
      item.setValues(box(entry.getValue()));
      series.add(item);
    }
    chart.setSeries(series);
    return chart;
  }

  /** 按发送方合并接口配置、实时速率、状态和范围内采集量。 */
  private List<CollectionDashboardVO.TableRow> collectionTable(List<CollectInterfaceConfig> interfaces,
      Map<String, Long> totals, Map<Long, RealtimeCollectionMetric> realtime) {
    Map<String, SenderAggregation> groups = new LinkedHashMap<>();
    for (CollectInterfaceConfig config : interfaces) {
      String name = sender(config);
      SenderAggregation group = groups.computeIfAbsent(name, key -> new SenderAggregation());
      group.interfaceCount++;
      group.protocols.add(protocol(config.getTransferType()));
      RealtimeCollectionMetric metric = realtime.get(config.getId());
      group.interfaceRateKbps += zero(metric == null ? null : metric.getInterfaceRateKbps());
      // 在线状态只取配置启用开关，Redis仍用于读取速率等实时指标。
      group.addStatus(config.getStatus());
    }
    long total = sum(totals);
    List<CollectionDashboardVO.TableRow> result = new ArrayList<>();
    for (Map.Entry<String, SenderAggregation> entry : groups.entrySet()) {
      SenderAggregation group = entry.getValue();
      CollectionDashboardVO.TableRow row = new CollectionDashboardVO.TableRow();
      row.setSystemName(entry.getKey());
      row.setInterfaceCount(group.interfaceCount);
      row.setProtocols(new ArrayList<>(group.protocols));
      row.setCollectionCount(totals.getOrDefault(entry.getKey(), 0L));
      row.setPercentage(total == 0 ? 0 : round(row.getCollectionCount() * 100D / total));
      row.setInterfaceRateKbps(round(group.interfaceRateKbps));
      row.setStatusName(EnumData.labelOf(BusinessEnums.InterfaceStatus.values(), group.status()));
      result.add(row);
    }
    return result;
  }

  /** 按发送方累计处理量、去重量和异常量，生成表格行。 */
  private List<ProcessingDashboardVO.TableRow> processingTable(List<DataProcessingStatistics> points,
      Map<Long, CollectInterfaceConfig> interfaceMap) {
    Map<String, long[]> sums = new LinkedHashMap<>();
    for (DataProcessingStatistics point : points) {
      String name = sender(interfaceMap.get(point.getInterfaceId()));
      long[] values = sums.computeIfAbsent(name, key -> new long[3]);
      values[0] += zero(point.getProcessingCount());
      values[1] += zero(point.getDuplicateCount());
      values[2] += zero(point.getWildValueCount());
    }
    List<ProcessingDashboardVO.TableRow> result = new ArrayList<>();
    for (Map.Entry<String, long[]> entry : sums.entrySet()) {
      ProcessingDashboardVO.TableRow row = new ProcessingDashboardVO.TableRow();
      row.setSystemName(entry.getKey());
      row.setProcessedCount(entry.getValue()[0]);
      row.setDeduplicatedCount(entry.getValue()[1]);
      row.setAbnormalCount(entry.getValue()[2]);
      result.add(row);
    }
    return result;
  }

  /** 按发送方汇总采集曲线点。 */
  private Map<String, Long> collectionTotals(List<CollectInterfaceStatistics> points,
      Map<Long, CollectInterfaceConfig> interfaceMap) {
    Map<String, Long> result = new LinkedHashMap<>();
    for (CollectInterfaceStatistics point : points) {
      String name = sender(interfaceMap.get(point.getInterfaceId()));
      result.put(name, result.getOrDefault(name, 0L) + zero(point.getCollectionCount()));
    }
    return result;
  }

  /** 将接口列表转换为按接口ID查询的映射。 */
  private Map<Long, CollectInterfaceConfig> interfaceMap(List<CollectInterfaceConfig> interfaces) {
    Map<Long, CollectInterfaceConfig> result = new LinkedHashMap<>();
    for (CollectInterfaceConfig config : interfaces) {
      if (config.getId() != null) { result.put(config.getId(), config); }
    }
    return result;
  }

  /** 按启用开关统计在线采集接口数量。 */
  private long onlineInterfaceCount(List<CollectInterfaceConfig> interfaces) {
    long count = 0;
    for (CollectInterfaceConfig config : interfaces) {
      // 与接口列表使用同一状态派生规则，避免实时缓存滞后影响在线数量。
      if (config.getStatus() == 1) { count++; }
    }
    return count;
  }

  /** 累加全部接口的实时流量。 */
  private double collectionInterfaceRate(Map<Long, RealtimeCollectionMetric> realtime) {
    double result = 0;
    for (RealtimeCollectionMetric metric : realtime.values()) { result += zero(metric.getInterfaceRateKbps()); }
    return result;
  }

  /** 汇总时间范围内的处理数量。 */
  private long sumProcessed(List<DataProcessingStatistics> points) {
    long result = 0;
    for (DataProcessingStatistics point : points) { result += zero(point.getProcessingCount()); }
    return result;
  }

  /** 汇总时间范围内的野值数量，接口字段继续使用abnormalCount。 */
  private long sumAbnormal(List<DataProcessingStatistics> points) {
    long result = 0;
    for (DataProcessingStatistics point : points) { result += zero(point.getWildValueCount()); }
    return result;
  }

  /** 解析前端时间范围并确定分桶粒度。 */
  private RangeSpec range(DashboardRange value) {
    DashboardRange selected = value == null ? DashboardRange.THREE_HOURS : value;
    // 每次查询只读取一次当前时间，所有窗口与汇总范围使用同一个时间基准。
    return new RangeSpec(selected.getValue(), selected.getHours(), selected.getBucketMinutes(),
        selected.isDateLabel(), LocalDateTime.now());
  }

  /** 查询范围、分桶大小及时间标签格式。 */
  private static class RangeSpec {
    /** 原有查询范围和时间标签配置。 */
    final String value;
    final boolean dateLabel;
    final LocalDateTime start;
    /** 固定历史区间数量，最后另加一个同粒度的滚动点。 */
    final int historyCount;
    /** 每个曲线点覆盖的秒数。 */
    final long bucketSeconds;
    /** 历史首个区间起点、最新滚动区间起点和统一截止时间。 */
    final long historyStart;
    final long rollingStart;
    final long chartEnd;
    /** 兼顾曲线完整首点及原有汇总口径的数据库查询起点。 */
    final LocalDateTime queryStart;
    RangeSpec(String value, int hours, int bucketMinutes, boolean dateLabel, LocalDateTime now) {
      this.value = value;
      this.dateLabel = dateLabel;
      // 第一步：使用本次查询的时间基准，保留卡片和表格的原始查询起点。
      start = now.minusHours(hours);
      bucketSeconds = bucketMinutes * 60L;
      historyCount = hours * 60 / bucketMinutes - 1;
      // 第二步：扣除落库缓冲，再向下对齐五秒，避免切入未结束的数据库窗口。
      long bufferedEpoch = now.atZone(ZoneId.systemDefault()).toEpochSecond()
          - CHART_FLUSH_BUFFER_SECONDS;
      chartEnd = Math.floorDiv(bufferedEpoch, STATISTICS_WINDOW_SECONDS)
          * STATISTICS_WINDOW_SECONDS;
      rollingStart = chartEnd - bucketSeconds;
      // 第三步：历史区间必须完整结束；恰好对齐时避免与最新点重复。
      long historyEnd = Math.floorDiv(chartEnd, bucketSeconds) * bucketSeconds;
      if (historyEnd == chartEnd) { historyEnd -= bucketSeconds; }
      historyStart = historyEnd - historyCount * bucketSeconds;
      // 第四步：必要时补查历史首点的边界数据，汇总时再过滤回原始范围。
      LocalDateTime chartStart = LocalDateTime.ofInstant(
          Instant.ofEpochSecond(historyStart), ZoneId.systemDefault());
      queryStart = chartStart.isBefore(start) ? chartStart : start;
    }
  }

  /** Redis中的单个采集接口实时指标。 */
  public static class RealtimeCollectionMetric {
    private Double interfaceRateKbps;
    private Integer status;
    private String sampleTime;
    public Double getInterfaceRateKbps() { return interfaceRateKbps; }
    public void setInterfaceRateKbps(Double v) { interfaceRateKbps = v; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer v) { status = v; }
    public String getSampleTime() { return sampleTime; }
    public void setSampleTime(String v) { sampleTime = v; }
  }

  /** Redis中的单个采集接口实时处理指标。 */
  public static class RealtimeProcessingMetric {
    private String sampleTime;
    public String getSampleTime() { return sampleTime; }
    public void setSampleTime(String v) { sampleTime = v; }
  }

  /** Redis处理实时指标读取结果。 */
  private static class ProcessingRealtime {
    private final List<RealtimeProcessingMetric> metrics = new ArrayList<>();
    private Long unhandledAlarmCount;
    private String sampleTime;
  }

  /** 发送方下接口聚合结果。 */
  private static class SenderAggregation {
    private int interfaceCount;
    private int online;
    private double interfaceRateKbps;
    private final Set<String> protocols = new LinkedHashSet<>();
    void addStatus(int status) {
      // 同一发送方下只累计已启用的在线接口。
      if (status == 1) { online++; }
    }
    int status() {
      // 有启用接口就显示在线，全部禁用时显示离线。
      return online > 0 ? 1 : 0;
    }
  }

  /** 生成各点的区间结束时间，最后一个点使用滚动统计的实际截止时间。 */
  private List<Long> buckets(RangeSpec spec) {
    List<Long> result = new ArrayList<>();
    // 历史横轴统一表示区间结束时间，与最新点的时间含义保持一致。
    for (int index = 1; index <= spec.historyCount; index++) {
      result.add(spec.historyStart + index * spec.bucketSeconds);
    }
    // 最新点始终覆盖完整跨度，不从当前固定区间起点重新累计。
    result.add(spec.chartEnd);
    return result;
  }

  /** 将五秒统计分别累计到固定历史区间及最新滚动区间，区间左闭右开。 */
  private void addChartValue(long[] values, LocalDateTime time, long count, RangeSpec spec) {
    // 无效时间和缓冲范围内的记录不参与曲线，卡片及表格仍独立汇总。
    if (time == null) { return; }
    long epoch = time.atZone(ZoneId.systemDefault()).toEpochSecond();
    if (epoch >= spec.chartEnd) { return; }
    // 完整历史区间按同一粒度定位，不向最新点写入固定区间的部分统计。
    if (epoch >= spec.historyStart) {
      long index = (epoch - spec.historyStart) / spec.bucketSeconds;
      if (index < spec.historyCount) { values[(int) index] += count; }
    }
    // 最新点单独覆盖最近五分钟、三十分钟或四小时，与历史区间可以重叠。
    if (epoch >= spec.rollingStart) { values[spec.historyCount] += count; }
  }

  /** 将时间桶转换为前端横轴标签。 */
  private List<String> labels(List<Long> buckets, RangeSpec spec) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(spec.dateLabel ? "MM-dd HH:mm" : "HH:mm");
    List<String> result = new ArrayList<>();
    for (long epoch : buckets) {
      // 最新时间标签保留秒数，准确展示扣除缓冲后的实际截止时间。
      DateTimeFormatter selected = epoch == spec.chartEnd
          ? DateTimeFormatter.ofPattern(spec.dateLabel ? "MM-dd HH:mm:ss" : "HH:mm:ss")
          : formatter;
      result.add(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()).format(selected));
    }
    return result;
  }

  /** 返回最新统计区间的完整本地时间，供前端提示具体起止范围。 */
  private String chartTime(long epoch) {
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  /** 将基础类型数组转换为JSON可序列化的列表。 */
  private List<Long> box(long[] values) {
    List<Long> result = new ArrayList<>();
    for (long value : values) { result.add(value); }
    return result;
  }

  /** 解析采集Redis字段中的接口ID。 */
  private Long collectionInterfaceId(String field) {
    if (!field.startsWith("interface:")) { return null; }
    try { return Long.valueOf(field.substring("interface:".length())); }
    catch (NumberFormatException ignored) { return null; }
  }

  /** 校验处理Redis字段是否符合采集接口字段格式。 */
  private boolean processingMetricField(String field) { return field.matches("interface:[0-9]+"); }

  /** 将Redis的JSON值反序列化为实时指标对象。 */
  private <T> T readMetric(Object value, Class<T> type) {
    if (value == null) { return null; }
    try { return objectMapper.readValue(String.valueOf(value), type); }
    catch (Exception ignored) { return null; }
  }

  /** 读取Redis中的长整型值。 */
  private Long longValue(Object value) {
    try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
    catch (NumberFormatException ignored) { return null; }
  }

  /** 取得较新的实时指标时间。 */
  private String later(String current, String candidate) {
    if (candidate == null || candidate.trim().isEmpty()) { return current; }
    return current == null || candidate.compareTo(current) > 0 ? candidate : current;
  }

  /** 查找采集接口实时指标中最新的更新时间。 */
  private String latestCollectionTime(Map<Long, RealtimeCollectionMetric> metrics) {
    String result = null;
    for (RealtimeCollectionMetric metric : metrics.values()) { result = later(result, metric.getSampleTime()); }
    return result;
  }

  /** 返回采集接口所属发送方名称。 */
  private String sender(CollectInterfaceConfig config) {
    return config == null || config.getSendFrom() == null || config.getSendFrom().trim().isEmpty()
        ? "未配置系统" : config.getSendFrom().trim();
  }

  /** 将采集传输方式转换为协议中文名称。 */
  private String protocol(Integer type) { return EnumData.labelOf(BusinessEnums.TransferType.values(), type); }
  /** 汇总映射中的全部长整型值。 */
  private long sum(Map<String, Long> values) {
    long result = 0;
    for (Long value : values.values()) { result += zero(value); }
    return result;
  }
  /** 将空的长整型指标按0参与统计。 */
  private long zero(Long value) { return value == null ? 0 : value; }
  /** 将空的整型指标按0参与统计。 */
  private int zero(Integer value) { return value == null ? 0 : value; }
  /** 将空的浮点型指标按0参与统计。 */
  private double zero(Double value) { return value == null ? 0 : value; }
  /** 汇总后的浮点指标统一保留两位小数。 */
  private double round(double value) { return Math.round(value * 100D) / 100D; }
}
