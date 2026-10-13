package com.example.dataprocess.processing.handler;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import com.example.dataprocess.collection.CollectInterfaceStatistics;
import com.example.dataprocess.collection.ReceivedDatagram;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.processing.processor.DataProcessor;
import com.example.dataprocess.processing.processor.LocalDataProcessor;
import com.example.dataprocess.processing.processor.RpcDataProcessor;
import com.example.dataprocess.service.CalibrationProcessingService;
import com.example.dataprocess.service.IoTDBTelemetryStorageService;
import com.example.dataprocess.service.TelemetryMessageService;
import com.example.dataprocess.service.TelemetryResultDispatcher;
import com.example.dataprocess.protocol.rpc.PdxpDataPayload;
import com.example.dataprocess.protocol.rpc.PdxpDataPayloadParser;
import com.example.dataprocess.tool.PdxpParser;
import com.example.dataprocess.tool.TcpTool;
import com.example.dataprocess.tool.UdpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BooleanSupplier;

/** PDXP协议接收和业务处理入口，RPC仍是正常PDXP帧后的处理分支。 */
@Component
public class PdxpProtocolHandler implements ProtocolHandler {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.service.DataAlarmRecorder alarmRecorder;

    /** 提交异常上下文，采集线程不直接写数据库。 */
    private void alarm(com.example.dataprocess.entity.CollectInterfaceRuntimeConfig config,
            com.example.common.enums.DataAlarmType type, String detail) {
        if (alarmRecorder != null) { alarmRecorder.record(config, type, detail); }
    }


    /** PDXP传输协议。 */
    private static final int TRANSFER_PROTOCOL_PDXP = 2;
    /** PDXP协议日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            PdxpProtocolHandler.class);
    /** 采集统计。 */
    private final CollectInterfaceStatistics statistics;
    /** 数据处理线程池。 */
    private final ThreadPoolExecutor dataProcessExecutor;
    /** 遥测入库和消息发送的独立异步调度服务。 */
    private final TelemetryResultDispatcher resultDispatcher;
    /** 本地数据处理器。 */
    private final LocalDataProcessor localProcessor;
    /** RPC数据处理器。 */
    private final RpcDataProcessor rpcProcessor;
    /** 按遥测参数校准公式执行野值检测的服务。 */
    private final CalibrationProcessingService calibrationProcessingService;
    /** 运行环境使用独立结果线程池。 */
    @org.springframework.beans.factory.annotation.Autowired
    public PdxpProtocolHandler(
            CollectInterfaceStatistics statistics,
            @Qualifier("dataProcessExecutor")
            ThreadPoolExecutor dataProcessExecutor,
            TelemetryResultDispatcher resultDispatcher,
            LocalDataProcessor localProcessor,
            RpcDataProcessor rpcProcessor,
            CalibrationProcessingService calibrationProcessingService) {
        // 处理线程只保留业务处理和任务提交职责。
        this.statistics = statistics;
        this.dataProcessExecutor = dataProcessExecutor;
        this.resultDispatcher = resultDispatcher;
        this.localProcessor = localProcessor;
        this.rpcProcessor = rpcProcessor;
        this.calibrationProcessingService = calibrationProcessingService;
    }

    /** 保留已有离线构造入口，由直接执行器确定结果任务执行时机。 */
    public PdxpProtocolHandler(CollectInterfaceStatistics statistics,
            ThreadPoolExecutor dataProcessExecutor, IoTDBTelemetryStorageService storageService,
            LocalDataProcessor localProcessor, RpcDataProcessor rpcProcessor,
            CalibrationProcessingService calibrationProcessingService,
            TelemetryMessageService messageService) {
        // 离线入口复用统一提交逻辑，运行环境始终选择上方异步构造器。
        this(statistics, dataProcessExecutor,
                new TelemetryResultDispatcher(storageService, messageService, Runnable::run, Runnable::run),
                localProcessor, rpcProcessor, calibrationProcessingService);
    }

    /** 返回PDXP接收处理器支持的传输协议。 */
    @Override
    public List<Integer> supportedTransferProtocols() {
        return Collections.singletonList(TRANSFER_PROTOCOL_PDXP);
    }

    /** 创建一个UDP PDXP协议会话。 */
    @Override
    public UdpSession createUdpSession(
            CollectInterfaceRuntimeConfig config,
            UdpTool.DatagramEndpoint endpoint) {
        return packet -> submit(config, packet.getData(), "UDP");
    }

    /** 创建一个TCP PDXP协议会话。 */
    @Override
    public TcpSession createTcpSession(CollectInterfaceRuntimeConfig config) {
        return (connection, running) -> receiveTcpPackets(
                config, connection, running);
    }

    /** 从TCP字节流持续恢复完整PDXP包。 */
    private void receiveTcpPackets(
            CollectInterfaceRuntimeConfig config,
            TcpTool.TcpConnection connection,
            BooleanSupplier running) throws IOException {
        while (running.getAsBoolean() && connection.isOpen()) {
            PdxpParser.PdxpPacket packet = connection.receivePdxpPacket();
            submit(config, packet.getRawPacket(), "TCP");
        }
    }

    /** 统计完整报文并提交PDXP业务处理线程池。 */
    private void submit(
            CollectInterfaceRuntimeConfig config,
            byte[] packetData,
            String transferName) {
        // 传输层字节用于秒级速率，采集量要等解析出完整PDXP帧后再累计。
        statistics.recordReceivedBytes(config, packetData.length);
        ReceivedDatagram datagram = new ReceivedDatagram(config, packetData);
        try {
            dataProcessExecutor.execute(() -> handle(datagram));
        } catch (RejectedExecutionException exception) {
            alarm(config, com.example.common.enums.DataAlarmType.PROCESS_QUEUE, "处理线程池已满，当前报文被拒绝");
            LOGGER.error("数据处理线程池已满，本次PDXP {}报文已拒绝，接口编号：{}",
                    transferName, config.getInterfaceId(), exception);
        }
    }

    /** 解析一个传输报文，并逐个处理其中的PDXP包。 */
    public void handle(ReceivedDatagram datagram) {
        List<PdxpParser.PdxpPacket> packets;
        try {
            // PDXP解析只在数据处理线程中执行。
            packets = PdxpParser.parseTransportUdp(datagram.getData());
        } catch (Exception exception) {
            alarm(datagram.getInterfaceConfig(), com.example.common.enums.DataAlarmType.PACKET_PARSE,
                    "报文长度：" + datagram.getData().length + "；解析失败：" + exception.getMessage());
            LOGGER.error("PDXP报文解析失败，异常帧暂未写入IoTDB，接口编号：{}",
                    datagram.getInterfaceConfig().getInterfaceId(), exception);
            return;
        }

        if (packets.isEmpty()) {
            alarm(datagram.getInterfaceConfig(), com.example.common.enums.DataAlarmType.PACKET_PARSE,
                    "报文长度：" + datagram.getData().length + "；未形成完整报文，当前数据不进入处理链路");
            LOGGER.warn("PDXP报文结构不完整，异常帧暂未写入IoTDB，接口编号：{}",
                    datagram.getInterfaceConfig().getInterfaceId());
            return;
        }

        for (PdxpParser.PdxpPacket packet : packets) {
            // 一个UDP报文可能包含多帧，必须按解析出的完整PDXP帧逐个统计。
            statistics.recordCollection(datagram.getInterfaceConfig());
            handleNormalPacket(datagram.getInterfaceConfig(), packet);
        }
    }

    /** 完成业务处理后分别提交入库和消息任务，再累计业务处理量。 */
    private void handleNormalPacket(
            CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet) {
        com.example.common.enums.DataAlarmType stage = com.example.common.enums.DataAlarmType.PACKET_PARSE;
        try {
            // 第一步：拆分自定义数据、遥测原始数据和遥测帧头。
            PdxpDataPayload payload = PdxpDataPayloadParser.parse(
                    packet.getData());
            // 第二步：rpc_enabled只决定正常PDXP帧后的业务处理分支。
            DataProcessor processor = Integer.valueOf(1).equals(
                    config.getRpcEnabled()) ? rpcProcessor : localProcessor;
            stage = Integer.valueOf(1).equals(config.getRpcEnabled())
                    ? com.example.common.enums.DataAlarmType.RPC_FAILURE : com.example.common.enums.DataAlarmType.PROCESS_CONFIG;
            Optional<TelemetryMessage> processedMessage =
                    processor.process(config, packet, payload);
            if (!processedMessage.isPresent()) {
                alarm(config, stage, "包序号：" + packet.getSequenceNumber() + "；未生成有效遥测结果，当前帧未入库");
                // 没有最终Proto时不写IoTDB，避免产生缺少设备身份的孤立帧。
                LOGGER.info("遥测消息转换失败，当前帧不写入IoTDB，接口编号：{}，包序号：{}",
                        config.getInterfaceId(), packet.getSequenceNumber());
                return;
            }

            TelemetryMessage telemetryMessage = processedMessage.get();
            // 远程处理结果已完成状态判定，仅记录接口开启范围检查后的异常参数。
            if (Integer.valueOf(1).equals(config.getRpcEnabled())
                    && Integer.valueOf(1).equals(config.getParameterRangeCheckEnabled()) && alarmRecorder != null) {
                for (SatDataCenter.DataExchange.TmTc.TelemetryMessages.Telemetry telemetry : telemetryMessage.getTelemetriesMap().values()) {
                    if (telemetry.getStateType() == SatDataCenter.DataExchange.TmTc.TelemetryMessages.StateType.STATE_TYPE_ALARM) {
                        alarmRecorder.record(config, com.example.common.enums.DataAlarmType.PARAMETER_RANGE,
                                telemetry.getTmSymbol(), telemetry.getTmName(), null, "远程服务状态判定",
                                "包序号：" + packet.getSequenceNumber() + "；遥测值：" + telemetry.getValue());
                    } else if (telemetry.getStateType() == SatDataCenter.DataExchange.TmTc.TelemetryMessages.StateType.STATE_TYPE_NORMAL) {
                        alarmRecorder.recover(config, com.example.common.enums.DataAlarmType.PARAMETER_RANGE, telemetry.getTmSymbol(), null);
                    }
                }
            }
            stage = com.example.common.enums.DataAlarmType.CALIBRATION_RULE;
            // 第三步：按每个遥测参数携带的校准公式执行野值检测。
            telemetryMessage = calibrationProcessingService.process(
                    config, telemetryMessage);
            // 第四步：校准完成后按最终保留参数统计真实野值数量。
            statistics.recordWildValue(config, telemetryMessage
                    .getTelemetriesMap().values().stream()
                    .filter(telemetry -> telemetry.getIsWildValue())
                    .count());
            // 第五步：两个结果任务独立提交，处理线程不等待外部服务响应。
            if (!resultDispatcher.submit(config, telemetryMessage, packet.getSequenceNumber())) {
                // 提交失败已有独立告警，部分被接受的任务继续执行，当前帧不累计成功处理量。
                return;
            }
            // 第六步：业务处理完成且两个任务均被接受后累计一次，不代表外部写入成功。
            statistics.recordFrameProcessing(
                    config.getTaskId(), config.getInterfaceId());
        } catch (Exception exception) {
            // 校准层已记录具体参数时不重复生成接口级告警。
            if (!(exception instanceof com.example.dataprocess.service.RecordedDataAlarmException)) {
                alarm(config, stage, "包序号：" + packet.getSequenceNumber() + "；当前帧处理失败：" + exception.getMessage());
            }
            LOGGER.error("正常PDXP帧后续处理失败，接口编号：{}，包序号：{}",
                    config.getInterfaceId(), packet.getSequenceNumber(), exception);
        }
    }

}
