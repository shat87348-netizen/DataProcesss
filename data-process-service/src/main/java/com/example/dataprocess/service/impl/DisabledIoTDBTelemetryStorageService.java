package com.example.dataprocess.service.impl;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.service.IoTDBTelemetryStorageService;
import com.example.dataprocess.tool.PdxpParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/** IoTDB禁用时使用的不落库存储实现。 */
@Service
@ConditionalOnProperty(
        prefix = "iotdb",
        name = "enabled",
        havingValue = "false")
public class DisabledIoTDBTelemetryStorageService
        implements IoTDBTelemetryStorageService {

    /** IoTDB禁用状态日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            DisabledIoTDBTelemetryStorageService.class);

    /** 应用启动时提示当前不会执行IoTDB写入。 */
    @PostConstruct
    public void showDisabledStatus() {
        // 只在启动时提示一次，避免每帧跳过存储时产生大量重复日志。
        LOGGER.warn("IoTDB存储已禁用，原始帧和处理后参数将不会入库");
    }

    /** 跳过PDXP原始帧保存。 */
    @Override
    public void savePdxpRawFrame(
            CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet) {
        // IoTDB已禁用，本方法保留为空操作以维持原有处理流程。
    }

    /** 存储禁用时跳过本地解析失败的异常整帧写入。 */
    @Override
    public void markPdxpRawFrameAbnormal(
            CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet) {
        // IoTDB已禁用，本方法保留为空操作以维持原有处理流程。
    }

    /** 跳过遥测消息原始帧保存。 */
    @Override
    public void saveRawFrame(
            Long interfaceId,
            TelemetryMessage message) {
        // IoTDB已禁用，本方法保留为空操作以维持原有处理流程。
    }

    /** 跳过处理后遥测参数保存。 */
    @Override
    public void saveProcessedParameters(
            CollectInterfaceRuntimeConfig config,
            TelemetryMessage message,
            byte[] rawFrame) {
        // IoTDB已禁用，本方法保留为空操作以维持原有处理流程。
    }
}
