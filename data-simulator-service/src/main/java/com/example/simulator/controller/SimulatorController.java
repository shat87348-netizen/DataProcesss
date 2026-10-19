package com.example.simulator.controller;

import com.example.simulator.enums.ProtocolProfile;
import com.example.simulator.enums.RunStatus;
import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.simulator.model.*;
import com.example.simulator.runtime.SimulatorManager;
import com.example.simulator.service.SourceService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.*;

/** 独立模拟源配置、执行和历史查询入口。 */
@RestController
@RequestMapping("/simulator")
public class SimulatorController {
    private final SourceService sources;
    private final SimulatorManager manager;

    public SimulatorController(SourceService sources, SimulatorManager manager) {
        this.sources = sources;
        this.manager = manager;
    }

    /** 查询分页列表，状态零代表从未启动。 */
    @GetMapping("/sources")
    public ApiResponse<PageResult<SourceConfig>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(sources.page(name, status, pageNum, pageSize));
    }

    /** 查询包括协议参数的完整详情。 */
    @GetMapping("/sources/{id}")
    public ApiResponse<SourceConfig> detail(@PathVariable long id) {
        return ApiResponse.success(sources.detail(id));
    }

    /** 创建时只保存配置，不启动发送。 */
    @PostMapping("/sources/create")
    public ApiResponse<Long> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.success(sources.create(request));
    }

    /** 修改只允许作用于没有活动执行的配置。 */
    @PostMapping("/sources/update")
    public ApiResponse<Void> update(@Valid @RequestBody SaveRequest request) {
        sources.update(request);
        return ApiResponse.success();
    }

    /** 删除保留历史记录。 */
    @PostMapping("/sources/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        sources.delete(request.id);
        return ApiResponse.success();
    }

    /** 启动返回本次运行编号。 */
    @PostMapping("/sources/start")
    public ApiResponse<Long> start(@Valid @RequestBody IdRequest request) throws Exception {
        return ApiResponse.success(manager.start(request.id));
    }

    /** 停止请求必须同时指定源和本次运行编号。 */
    @PostMapping("/sources/stop")
    public ApiResponse<Void> stop(@Valid @RequestBody StopRequest request) {
        manager.stop(request.id, request.runId);
        return ApiResponse.success();
    }

    /** 查询运行历史，允许查看逻辑删除源的历史。 */
    @GetMapping("/sources/{id}/runs")
    public ApiResponse<PageResult<RunRecord>> history(
            @PathVariable long id, @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(sources.history(id, pageNum, pageSize));
    }

    /** 查询受支持协议组合和中文状态选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, Object>> findData() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> protocols = new ArrayList<>();
        for (ProtocolProfile profile : ProtocolProfile.values()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", profile.protocol);
            option.put("label", profile.label);
            option.put("transferType", profile.transport);
            option.put("transferTypeName", profile.transportLabel);
            option.put("defaultPort", profile.port);
            option.put("defaultIntervalMillis", profile.interval);
            option.put("defaultLoopEnabled", profile.loop);
            protocols.add(option);
        }
        List<Map<String, Object>> statuses = new ArrayList<>();
        statuses.add(option(0, "未启动"));
        for (RunStatus status : RunStatus.values()) {
            statuses.add(option(status.code, status.label));
        }
        result.put("protocols", protocols);
        result.put("statuses", statuses);
        result.put("yesNo", Arrays.asList(option(0, "否"), option(1, "是")));
        return ApiResponse.success(result);
    }

    /** 统一枚举选项结构。 */
    private Map<String, Object> option(int value, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("label", label);
        return result;
    }

    /** 主键操作请求。 */
    public static class IdRequest {
        @NotNull(message = "模拟源编号不能为空") @Positive
        public Long id;
    }

    /** 停止时附带运行编号，防止旧页面误停新任务。 */
    public static class StopRequest extends IdRequest {
        @NotNull(message = "运行编号不能为空") @Positive
        public Long runId;
    }
}

