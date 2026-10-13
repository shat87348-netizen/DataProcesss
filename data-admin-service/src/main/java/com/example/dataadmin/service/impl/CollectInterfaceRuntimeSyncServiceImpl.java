package com.example.dataadmin.service.impl;

import com.example.common.tool.HttpRequestTool;
import com.example.dataadmin.dto.collection.CollectInterfaceSyncRequest;
import com.example.dataadmin.mapper.CollectInterfaceConfigMapper;
import com.example.dataadmin.service.CollectInterfaceRuntimeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** 采集接口运行状态同步实现。 */
@Service
public class CollectInterfaceRuntimeSyncServiceImpl
        implements CollectInterfaceRuntimeSyncService {

    /** 内部同步异常同时进入数据采集告警，日志与告警独立表达操作与影响。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataadmin.service.CollectSyncAlarmRecorder alarmRecorder;
    /** 采集接口同步日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CollectInterfaceRuntimeSyncServiceImpl.class);

    /** 采集接口配置数据访问组件。 */
    private final CollectInterfaceConfigMapper interfaceMapper;

    /** 数据处理服务采集接口同步地址。 */
    private final String syncUrl;

    /** 内部服务调用使用的公共HTTP工具。 */
    private final HttpRequestTool httpRequestTool;

    public CollectInterfaceRuntimeSyncServiceImpl(
            CollectInterfaceConfigMapper interfaceMapper,
            @Value("${data-processing.interface-sync-url:"
                    + "http://127.0.0.1:8083/internal/collection/interfaces/sync}")
            String syncUrl,
            @Value("${data-processing.interface-sync-connect-timeout-millis:5000}")
            int connectTimeoutMillis,
            @Value("${data-processing.interface-sync-read-timeout-millis:10000}")
            int readTimeoutMillis) {
        this.interfaceMapper = interfaceMapper;
        this.syncUrl = syncUrl;
        // 使用公共工具并按管理服务配置设置内部调用超时时间。
        this.httpRequestTool = new HttpRequestTool(
                connectTimeoutMillis,
                readTimeoutMillis);
    }

    @Override
    public void syncAfterCommit() {
        // 第一步：存在活动事务时等待提交成功，避免处理服务读取到旧配置。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            synchronizeEnabledInterfacesSafely();
                        }
                    });
            return;
        }

        // 第二步：没有事务时直接同步，保证该组件也能独立复用。
        synchronizeEnabledInterfacesSafely();
    }

    /** 查询完整启用列表并通知数据处理服务，失败时保留已提交配置。 */
    private void synchronizeEnabledInterfacesSafely() {
        List<Long> enabledIds = new java.util.ArrayList<Long>();
        try {
            // 第一步：数据库启用开关决定数据处理服务最终运行哪些接口。
            enabledIds = interfaceMapper.findAllEnabledIds();
            CollectInterfaceSyncRequest request =
                    new CollectInterfaceSyncRequest(enabledIds);

            // 第二步：以JSON格式发送完整列表，列表中不存在的接口由处理服务停止。
            com.fasterxml.jackson.databind.JsonNode response = httpRequestTool.postJson(syncUrl, request,
                    com.fasterxml.jackson.databind.JsonNode.class);
            if (response == null || response.path("code").asInt() != 200 || !response.path("data").isArray()) {
                throw new IllegalStateException("处理服务未返回有效运行接口列表");
            }
            java.util.List<Long> runningIds = new java.util.ArrayList<Long>();
            for (com.fasterxml.jackson.databind.JsonNode id : response.path("data")) { runningIds.add(id.asLong()); }
            java.util.List<Long> failedIds = new java.util.ArrayList<Long>(enabledIds);
            failedIds.removeAll(runningIds);
            for (Long id : enabledIds) {
                try {
                    com.example.dataadmin.entity.CollectInterfaceConfig config = interfaceMapper.findById(id);
                    if (config != null && alarmRecorder != null) { alarmRecorder.recover(config); }
                } catch (Exception ignored) {
                    // 告警恢复上下文读取失败不改变已经成功的接口同步结果。
                }
            }
            setOperationResult("已启动接口ID：" + runningIds + "；已启用但未启动接口ID：" + failedIds, !failedIds.isEmpty());
            LOGGER.info("采集接口运行列表同步成功，启用接口数量：{}", enabledIds.size());
        } catch (Exception exception) {
            for (Long id : enabledIds) {
                try {
                    com.example.dataadmin.entity.CollectInterfaceConfig config = interfaceMapper.findById(id);
                    if (config != null && alarmRecorder != null) { alarmRecorder.report(config, exception.getMessage()); }
                } catch (Exception ignored) {
                    // 无法取得数据库上下文时保留服务日志，不虚构接口身份。
                }
            }
            setOperationResult("配置已保存，实际运行状态同步失败：" + exception.getMessage(), true);
            // 同步发生在事务提交后，失败只能记录，不能伪装成数据库回滚。
            LOGGER.error("采集接口运行列表同步失败，原因：{}", exception.getMessage(), exception);
        }
    }
    /** 结果随当前人工操作响应进入日志，后台调用没有请求时只保留服务日志。 */
    private void setOperationResult(String detail, boolean failed) {
        org.springframework.web.context.request.RequestAttributes attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(com.example.dataadmin.config.OperationLogAdvice.SYNC_RESULT, detail,
                    org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            attributes.setAttribute(com.example.dataadmin.config.OperationLogAdvice.SYNC_FAILED, failed,
                    org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
    }
}
