package com.example.dataadmin.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.dataadmin.dto.IdRequest;
import com.example.dataadmin.dto.collection.EnabledUpdateRequest;
import com.example.dataadmin.dto.collection.InterfaceQueryRequest;
import com.example.dataadmin.dto.collection.InterfaceSaveRequest;
import com.example.dataadmin.dto.collection.ProtocolSaveRequest;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.enums.ControllerEnumData;
import com.example.dataadmin.service.DataCollectionService;
import com.example.dataadmin.vo.EnumOptionVO;
import com.example.dataadmin.vo.collection.CollectionOverviewVO;
import com.example.dataadmin.vo.collection.ProtocolConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 数据采集管理接口。
 *
 * 提供采集接口、协议配置及接口事件监控相关功能。
 */
@RestController
@RequestMapping("/collection")
public class DataCollectionController {

    private final DataCollectionService dataCollectionService;

    public DataCollectionController(DataCollectionService dataCollectionService) {
        this.dataCollectionService = dataCollectionService;
    }

    /** 查询数据采集页面涉及的全部枚举选项。 */
    @GetMapping("/findData")
    public ApiResponse<Map<String, List<EnumOptionVO>>> findData() {
        return ApiResponse.success(ControllerEnumData.collection());
    }

    /** 查询数据采集页面顶部统计数据。 */
    @GetMapping("/overview")
    public ApiResponse<CollectionOverviewVO> getOverview(
            @RequestParam String taskId) {
        // 采集总览必须限定试验任务，避免跨任务汇总接口和采集量。
        return ApiResponse.success(dataCollectionService.getOverview(taskId));
    }

    /** 分页查询采集接口。 */
    @GetMapping("/interfaces")
    public ApiResponse<PageResult<CollectInterfaceConfig>> pageInterfaces(
            @Valid @ModelAttribute InterfaceQueryRequest request) {
        return ApiResponse.success(
                dataCollectionService.pageInterfaceConfigs(request));
    }

    /** 查询采集接口详情。 */
    @GetMapping("/interfaces/{id}")
    public ApiResponse<CollectInterfaceConfig> getInterface(
            @PathVariable Long id) {
        return ApiResponse.success(
                dataCollectionService.getInterfaceConfig(id));
    }

    /** 新增采集接口。 */
    @PostMapping("/interfaces/create")
    public ApiResponse<Long> createInterface(
            @Valid @RequestBody InterfaceSaveRequest request) {
        return ApiResponse.success(
                dataCollectionService.createInterfaceConfig(request));
    }

    /** 修改采集接口。 */
    @PostMapping("/interfaces/update")
    public ApiResponse<Void> updateInterface(
            @Valid @RequestBody InterfaceSaveRequest request) {
        requireId(request.getId());
        dataCollectionService.updateInterfaceConfig(request.getId(), request);
        return ApiResponse.success();
    }

    /** 删除采集接口。 */
    @PostMapping("/interfaces/delete")
    public ApiResponse<Void> deleteInterface(
            @Valid @RequestBody IdRequest request) {
        dataCollectionService.deleteInterfaceConfig(request.getId());
        return ApiResponse.success();
    }

    /** 修改采集接口启用状态。 */
    @PostMapping("/interfaces/enabled")
    public ApiResponse<Void> updateInterfaceEnabled(
            @Valid @RequestBody EnabledUpdateRequest request) {
        dataCollectionService.updateInterfaceEnabled(
                request.getId(),
                request.getEnabled());
        return ApiResponse.success();
    }

    /** 查询协议配置列表。 */
    @GetMapping("/protocols")
    public ApiResponse<List<ProtocolConfigVO>> listProtocols(
            @RequestParam(required = false) String taskId) {
        return ApiResponse.success(
                dataCollectionService.listProtocolConfigs(taskId));
    }

    /** 查询协议配置详情。 */
    @GetMapping("/protocols/{id}")
    public ApiResponse<ProtocolConfigVO> getProtocol(@PathVariable Long id) {
        return ApiResponse.success(
                dataCollectionService.getProtocolConfig(id));
    }

    /** 新增协议配置。 */
    @PostMapping("/protocols/create")
    public ApiResponse<Long> createProtocol(
            @Valid @RequestBody ProtocolSaveRequest request) {
        return ApiResponse.success(
                dataCollectionService.createProtocolConfig(request));
    }

    /** 修改协议配置。 */
    @PostMapping("/protocols/update")
    public ApiResponse<Void> updateProtocol(
            @Valid @RequestBody ProtocolSaveRequest request) {
        requireId(request.getId());
        dataCollectionService.updateProtocolConfig(request.getId(), request);
        return ApiResponse.success();
    }

    /** 删除协议配置。 */
    @PostMapping("/protocols/delete")
    public ApiResponse<Void> deleteProtocol(
            @Valid @RequestBody IdRequest request) {
        dataCollectionService.deleteProtocolConfig(request.getId());
        return ApiResponse.success();
    }

    /** 修改协议配置启用状态。 */
    @PostMapping("/protocols/enabled")
    public ApiResponse<Void> updateProtocolEnabled(
            @Valid @RequestBody EnabledUpdateRequest request) {
        dataCollectionService.updateProtocolEnabled(
                request.getId(),
                request.getEnabled());
        return ApiResponse.success();
    }

    /** 校验修改请求中的业务主键。 */
    private void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("业务主键不能为空");
        }
    }
}
