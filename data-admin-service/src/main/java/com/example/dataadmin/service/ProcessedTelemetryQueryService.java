package com.example.dataadmin.service;

import com.example.common.tool.IoTDBPathTool;
import com.example.dataadmin.config.IoTDBQueryConfig;
import com.example.dataadmin.entity.ProcessedTelemetryFilterSelection;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.DeviceSatelliteType;
import com.example.dataadmin.mapper.IoTDBFrameQueryMapper;
import com.example.dataadmin.mapper.ProcessedTelemetryFilterSelectionMapper;
import com.example.dataadmin.vo.processing.ProcessedTelemetryCurveVO;
import com.example.dataadmin.vo.processing.ProcessedTelemetryVO;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.isession.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

/** 按任务已勾选参数并发查询IoTDB最新结果与曲线。 */
@Service
public class ProcessedTelemetryQueryService {
    /** 每批提交的任务数不超过IoTDB查询线程数。 */
    private final int queryConcurrency;
    private final ProcessedTelemetryFilterSelectionMapper selectionMapper;
    private final IoTDBFrameQueryMapper queryMapper;
    private final SessionPool sessionPool;
    private final ExecutorService queryExecutor;
    private final TelemetrySelectionScopeService scopeService;

    public ProcessedTelemetryQueryService(
            ProcessedTelemetryFilterSelectionMapper selectionMapper,
            IoTDBFrameQueryMapper queryMapper,
            SessionPool sessionPool,
            IoTDBQueryConfig queryConfig,
            TelemetrySelectionScopeService scopeService,
            @Qualifier("processedTelemetryQueryExecutor") ExecutorService queryExecutor) {
        this.selectionMapper = selectionMapper;
        this.queryMapper = queryMapper;
        this.sessionPool = sessionPool;
        this.queryExecutor = queryExecutor;
        this.scopeService = scopeService;
        this.queryConcurrency = queryConfig.getProcessedTelemetryQueryThreadCount();
    }

    /** 从有效勾选记录取得路径，并按记录顺序汇总每个参数的最新值。 */
    public List<ProcessedTelemetryVO> latest(String taskId, Integer selectionType, Long targetId) {
        // 最新值仅接受处理后数据表格和数据可视化表格的勾选场景。
        if (selectionType != BusinessEnums.SelectionType.PROCESSED_TABLE.getCode()
                && selectionType != BusinessEnums.SelectionType.VISUALIZATION_TABLE.getCode()) {
            throw new IllegalArgumentException("最新值查询的勾选类型不正确");
        }
        return queryInBatches(selected(taskId, selectionType, targetId), this::latestOne);
    }

    /** 从有效勾选记录取得路径，并按记录顺序汇总每个参数的曲线。 */
    public List<ProcessedTelemetryCurveVO> curves(String taskId, Integer selectionType,
            Long targetId) {
        // 曲线仅接受处理后数据曲线和数据可视化曲线的勾选场景。
        if (selectionType != BusinessEnums.SelectionType.PROCESSED_CURVE.getCode()
                && selectionType != BusinessEnums.SelectionType.VISUALIZATION_CURVE.getCode()) {
            throw new IllegalArgumentException("曲线查询的勾选类型不正确");
        }
        return queryInBatches(selected(taskId, selectionType, targetId), this::curveOne);
    }

    /** 每批最多提交连接池容量内的查询，不一次创建所有参数的查询任务。 */
    private <T> List<T> queryInBatches(List<ProcessedTelemetryFilterSelection> selected,
            Function<ProcessedTelemetryFilterSelection, T> query) {
        List<T> result = new ArrayList<>(selected.size());
        for (int start = 0; start < selected.size(); start += queryConcurrency) {
            List<Callable<T>> tasks = new ArrayList<>();
            for (ProcessedTelemetryFilterSelection item : selected.subList(
                    start, Math.min(start + queryConcurrency, selected.size()))) {
                tasks.add(() -> query.apply(item));
            }
            try {
                for (Future<T> future : queryExecutor.invokeAll(tasks)) {
                    result.add(future.get());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("遥测参数查询被中断", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("查询IoTDB遥测参数失败", exception.getCause());
            }
        }
        return result;
    }

    /** 单个参数只读取最新一条，缺少历史值时仍返回参数身份。 */
    private ProcessedTelemetryVO latestOne(ProcessedTelemetryFilterSelection item) {
        ProcessedTelemetryVO result = new ProcessedTelemetryVO();
        result.setDeviceSatelliteId(item.getDeviceSatelliteId());
        result.setTelemetryCode(item.getTelemetryCode());
        result.setParameter(item.getTelemetryName());
        String sql = queryMapper.latestProcessedTelemetry(path(item));
        try (SessionDataSetWrapper dataSet = sessionPool.executeQueryStatement(sql)) {
            SessionDataSet.DataIterator iterator = dataSet.iterator();
            if (iterator.next()) {
                String name = iterator.getString("tmName");
                if (name != null && !name.trim().isEmpty()) {
                    result.setParameter(name);
                }
                result.setParameterValue(iterator.getDouble("value"));
                result.setStatus(iterator.getString("stateName"));
                if (!iterator.isNull("processedAt")) {
                    result.setProcessTime(new Timestamp(iterator.getLong("processedAt"))
                            .toLocalDateTime());
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("查询遥测参数最新值失败：" + item.getTelemetryCode(), exception);
        }
    }

    /** 单个参数读取最新一百二十条原始点，再转换为时间升序。 */
    private ProcessedTelemetryCurveVO curveOne(ProcessedTelemetryFilterSelection item) {
        ProcessedTelemetryCurveVO result = new ProcessedTelemetryCurveVO();
        result.setDeviceSatelliteId(item.getDeviceSatelliteId());
        result.setTelemetryCode(item.getTelemetryCode());
        result.setParameter(item.getTelemetryName());
        String sql = queryMapper.recentProcessedTelemetry(path(item));
        try (SessionDataSetWrapper dataSet = sessionPool.executeQueryStatement(sql)) {
            SessionDataSet.DataIterator iterator = dataSet.iterator();
            while (iterator.next()) {
                Timestamp time = iterator.getTimestamp("Time");
                if (time != null) {
                    result.getTimes().add(time.getTime());
                    result.getValues().add(iterator.getDouble("value"));
                }
            }
            Collections.reverse(result.getTimes());
            Collections.reverse(result.getValues());
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("查询遥测参数曲线失败：" + item.getTelemetryCode(), exception);
        }
    }

    /** 设备按本地主键查询，卫星按RPC身份前缀和已勾选遥测代号查询。 */
    private String path(ProcessedTelemetryFilterSelection item) {
        // 第一步：复用设备卫星类型枚举，类型从勾选所属资料读取，不由前端重复传入。
        String identityNode;
        if (DeviceSatelliteType.SATELLITE.getValue().equals(item.getDeviceSatelliteType())) {
            identityNode = "rpc_*";
        } else if (DeviceSatelliteType.DEVICE.getValue().equals(item.getDeviceSatelliteType())) {
            if (item.getDeviceSatelliteId() == null || item.getDeviceSatelliteId() <= 0) {
                throw new IllegalStateException("已勾选设备参数缺少有效设备主键");
            }
            identityNode = IoTDBPathTool.prefixedPathNode("device_",
                    String.valueOf(item.getDeviceSatelliteId()), "unknown_device");
        } else {
            throw new IllegalStateException("已勾选参数的设备卫星类型不正确");
        }
        // 第二步：始终限定当前任务和遥测代号，类型通配只在所选设备或RPC身份下生效。
        return "root.db."
                + IoTDBPathTool.prefixedPathNode("task_", item.getTaskId(), "unknown_task")
                + "." + identityNode + ".tms.*."
                + IoTDBPathTool.pathNode(item.getTelemetryCode(), "unknown_tm");
    }

    /** 查询任务下仍有效的勾选参数，任务编号不允许为空。 */
    private List<ProcessedTelemetryFilterSelection> selected(String taskId,
            Integer selectionType, Long targetId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
        long scopeId = scopeService.require(taskId.trim(), selectionType, targetId);
        return selectionMapper.findSelected(taskId.trim(), selectionType, scopeId);
    }
}
