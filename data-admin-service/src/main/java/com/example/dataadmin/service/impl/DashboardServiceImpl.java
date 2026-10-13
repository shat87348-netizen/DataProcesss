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
        mapper.findCollectionStatisticsRange(taskId, spec.start);
    Map<Long, RealtimeCollectionMetric> realtime = collectionRealtime(taskId);
    Map<Long, CollectInterfaceConfig> interfaceMap = interfaceMap(interfaces);
    Map<String, Long> totals = collectionTotals(points, interfaceMap);

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
        mapper.findProcessingStatisticsRange(taskId, spec.start);
    ProcessingRealtime realtime = processingRealtime(taskId);

    ProcessingDashboardVO result = new ProcessingDashboardVO();
    result.setTaskId(taskId);
    result.setRange(spec.value);
    result.setSampleTime(realtime.sampleTime);
    result.setLineChart(processingChart(points, interfaceMap(interfaces), spec));
    result.setTable(processingTable(points, interfaceMap(interfaces)));

    ProcessingDashboardVO.Summary summary = result.getSummary();
    summary.setProcessedCount(sumProcessed(points));
    summary.setAbnormalCount(sumAbnormal(points));
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

  /** 将采集曲线点按发送方和时间桶累计为折线图序列。 */
  private CollectionDashboardVO.LineChart collectionChart(List<CollectInterfaceStatistics> points,
      Map<Long, CollectInterfaceConfig> interfaceMap, RangeSpec spec) {
    List<Long> buckets = buckets(spec);
    Map<String, long[]> values = new LinkedHashMap<>();
    for (CollectInterfaceStatistics point : points) {
      String name = sender(interfaceMap.get(point.getInterfaceId()));
      long[] data = values.computeIfAbsent(name, key -> new long[buckets.size()]);
      int index = index(point.getStatisticsTime(), spec, buckets.size());
      if (index >= 0) { data[index] += zero(point.getCollectionCount()); }
    }
    CollectionDashboardVO.LineChart chart = new CollectionDashboardVO.LineChart();
    chart.setTimePoints(labels(buckets, spec));
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

  /** 将处理曲线点按发送方和时间桶累计为折线图序列。 */
  private ProcessingDashboardVO.LineChart processingChart(List<DataProcessingStatistics> points,
      Map<Long, CollectInterfaceConfig> interfaceMap, RangeSpec spec) {
    List<Long> buckets = buckets(spec);
    Map<String, long[]> values = new LinkedHashMap<>();
    for (DataProcessingStatistics point : points) {
      int index = index(point.getStatisticsTime(), spec, buckets.size());
      if (index >= 0) {
        String name = sender(interfaceMap.get(point.getInterfaceId()));
        long[] data = values.computeIfAbsent(name, key -> new long[buckets.size()]);
        data[index] += zero(point.getProcessingCount());
      }
    }
    ProcessingDashboardVO.LineChart chart = new ProcessingDashboardVO.LineChart();
    chart.setTimePoints(labels(buckets, spec));
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
    return new RangeSpec(selected.getValue(), selected.getHours(), selected.getBucketMinutes(), selected.isDateLabel());
  }

  /** 查询范围、分桶大小及时间标签格式。 */
  private static class RangeSpec {
    final String value;
    final int hours;
    final int bucketMinutes;
    final boolean dateLabel;
    final LocalDateTime start;
    RangeSpec(String value, int hours, int bucketMinutes, boolean dateLabel) {
      this.value = value;
      this.hours = hours;
      this.bucketMinutes = bucketMinutes;
      this.dateLabel = dateLabel;
      start = LocalDateTime.now().minusHours(hours);
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

  /** 生成完整时间桶，空桶也会保留为0。 */
  private List<Long> buckets(RangeSpec spec) {
    int count = (spec.hours * 60) / spec.bucketMinutes + 1;
    long start = bucketEpoch(spec.start, spec.bucketMinutes);
    List<Long> result = new ArrayList<>();
    for (int index = 0; index < count; index++) { result.add(start + index * spec.bucketMinutes * 60L); }
    return result;
  }

  /** 计算曲线点所属的时间桶下标。 */
  private int index(LocalDateTime time, RangeSpec spec, int size) {
    if (time == null) { return -1; }
    long start = bucketEpoch(spec.start, spec.bucketMinutes);
    long current = bucketEpoch(time, spec.bucketMinutes);
    long index = (current - start) / (spec.bucketMinutes * 60L);
    return index < 0 || index >= size ? -1 : (int) index;
  }

  /** 将时间向下对齐到指定分钟粒度。 */
  private long bucketEpoch(LocalDateTime time, int minutes) {
    long epoch = time.atZone(ZoneId.systemDefault()).toEpochSecond();
    return epoch / (minutes * 60L) * (minutes * 60L);
  }

  /** 将时间桶转换为前端横轴标签。 */
  private List<String> labels(List<Long> buckets, RangeSpec spec) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(spec.dateLabel ? "MM-dd HH:mm" : "HH:mm");
    List<String> result = new ArrayList<>();
    for (long epoch : buckets) {
      result.add(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()).format(formatter));
    }
    return result;
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
