package com.example.dataprocess.controller;

import com.example.common.response.ApiResponse;
import com.example.dataprocess.collection.CollectInterfaceManager;
import com.example.dataprocess.dto.CollectInterfaceSyncRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 接收Admin采集接口运行列表通知的内部接口。 */
@RestController
@RequestMapping("/internal/collection/interfaces")
public class CollectInterfaceRuntimeController {

    /** 采集接口运行管理组件。 */
    private final CollectInterfaceManager interfaceManager;

    public CollectInterfaceRuntimeController(
            CollectInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    /** 按Admin传入的完整接口主键列表同步UDP采集任务。 */
    @PostMapping("/sync")
    public ApiResponse<java.util.List<Long>> syncInterfaces(
            @Valid @RequestBody CollectInterfaceSyncRequest request) {
        // Controller只负责接收通知，具体查询和启停统一交给接口管理组件。
        return ApiResponse.success(interfaceManager.syncInterfaces(request.getInterfaceIds()));
    }
}
