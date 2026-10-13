package com.example.dataprocess.processing.handler;

import com.example.dataprocess.collection.CollectInterfaceStatistics;
import com.example.dataprocess.collection.ReceivedDatagram;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import com.example.dataprocess.processing.processor.LocalDataProcessor;
import com.example.dataprocess.processing.processor.RpcDataProcessor;
import com.example.dataprocess.protocol.rpc.PdxpProtocolField;
import com.example.dataprocess.protocol.rpc.ProtocolConfigParser;
import com.example.dataprocess.protocol.rpc.TelemetryRpcClient;
import com.example.dataprocess.service.CalibrationProcessingService;
import com.example.dataprocess.service.IoTDBTelemetryStorageService;
import com.example.dataprocess.service.TelemetryMessageService;
import com.example.dataprocess.tool.PdxpParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** PDXP协议直线处理流程测试。 */
class PdxpProtocolHandlerTest {

    /** 验证关闭RPC后，从工作表解析到存储和发送的完整顺序。 */
    @Test
    void shouldPublishLocalFrameAfterStorage() throws Exception {
        // 准备协议动态配置及实际本地处理器。
        ProtocolConfigParser configParser = new ProtocolConfigParser(new ObjectMapper());
        IoTDBTelemetryStorageService storage = mock(IoTDBTelemetryStorageService.class);
        TelemetryMessageService sender = mock(TelemetryMessageService.class);
        CalibrationProcessingService calibration = calibrationService();
        TelemetryRpcClient rpc = mock(TelemetryRpcClient.class);
        CollectInterfaceStatistics statistics =
                mock(CollectInterfaceStatistics.class);
        PdxpProtocolHandler handler = new PdxpProtocolHandler(
                statistics, mock(ThreadPoolExecutor.class), storage,
                new LocalDataProcessor(statistics),
                new RpcDataProcessor(rpc, configParser), calibration, sender);
        CollectInterfaceRuntimeConfig config = interfaceConfig(0);
        config.setTaskId("TASK-7");
        String fields = "{\"fields\":[{\"id\":81,\"tableIndex\":\"10\","
                + "\"bitWidth\":\"16\",\"telemetryName\":\"电压\","
                + "\"telemetryCode\":\"TM010\"}]}";
        // 字段在接口同步时已经加载，逐帧处理不再解析协议配置中的fields。
        config.setProtocolConfigParams("{\"deviceSatelliteId\":12}");
        config.setPdxpFields(configParser.parseFields(fields, PdxpProtocolField.class));

        // 报文包含两字节传输头、固定包头和十一字节数据域。
        byte[] datagram = Arrays.copyOf(validPdxpDatagram(), 45);
        datagram[32] = 11;
        datagram[39] = 0x34;
        datagram[40] = 0x12;
        handler.handle(new ReceivedDatagram(config, datagram));

        // 核对校准、整帧和参数入库、消息发送的业务顺序。
        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> storedFrame = ArgumentCaptor.forClass(byte[].class);
        InOrder order = inOrder(calibration, storage, sender);
        order.verify(calibration).process(any(), any());
        order.verify(storage).saveProcessedParameters(any(), any(), storedFrame.capture());
        order.verify(sender).send(sent.capture());
        TelemetryMessage message = TelemetryMessage.parseFrom(sent.getValue());
        assertEquals(4660, message.getTelemetriesOrThrow("TM010").getValue());
        assertEquals("电压", message.getTelemetriesOrThrow("TM010").getTmName());
        assertEquals("设备通道", message.getChannelName());
        assertEquals("业务001", message.getBussinessId());
        assertEquals("", message.getSatCode());
        // 入库存完整采集帧，发送的Proto继续保存去头数据域，二者来源必须独立。
        assertArrayEquals(datagram, storedFrame.getValue());
        assertArrayEquals(new byte[]{0x34, 0x12}, message.getFrameRawData().toByteArray());
        verify(rpc, never()).process(any());
    }

    /** 验证缺少数据域时不写IoTDB也不发布处理结果。 */
    @Test
    void shouldFollowNormalFrameOrder() throws Exception {
        IoTDBTelemetryStorageService storageService =
                mock(IoTDBTelemetryStorageService.class);
        TelemetryMessageService messageService =
                mock(TelemetryMessageService.class);
        TelemetryRpcClient rpcClient = mock(TelemetryRpcClient.class);
        PdxpProtocolHandler handler = handler(
                storageService, messageService, rpcClient);

        handler.handle(new ReceivedDatagram(
                interfaceConfig(0), validPdxpDatagram()));

        verify(storageService, never()).savePdxpRawFrame(any(), any());
        verify(storageService, never()).saveProcessedParameters(any(), any(), any());
        verify(messageService, never()).send(any());
        verify(rpcClient, never()).process(any());
    }

    /** 验证异常报文不会进入正常帧存储和发布流程。 */
    @Test
    void shouldStopWithoutSavingAbnormalFrame() throws Exception {
        byte[] invalidData = new byte[]{(byte) 0x80, 0x01};
        IoTDBTelemetryStorageService storageService =
                mock(IoTDBTelemetryStorageService.class);
        TelemetryMessageService messageService =
                mock(TelemetryMessageService.class);
        TelemetryRpcClient rpcClient = mock(TelemetryRpcClient.class);
        PdxpProtocolHandler handler = handler(
                storageService, messageService, rpcClient);

        handler.handle(new ReceivedDatagram(
                interfaceConfig(1), invalidData));

        verify(storageService, never()).savePdxpRawFrame(any(), any());
        verify(storageService, never()).saveProcessedParameters(any(), any(), any());
        verify(messageService, never()).send(any());
        verify(rpcClient, never()).process(any());
    }

    /** 组装测试使用的PDXP协议处理器。 */
    private PdxpProtocolHandler handler(
            IoTDBTelemetryStorageService storageService,
            TelemetryMessageService messageService,
            TelemetryRpcClient rpcClient) {
        RpcDataProcessor rpcProcessor = new RpcDataProcessor(
                rpcClient,
                new ProtocolConfigParser(new ObjectMapper()));
        CollectInterfaceStatistics statistics =
                mock(CollectInterfaceStatistics.class);
        return new PdxpProtocolHandler(
                statistics,
                mock(ThreadPoolExecutor.class),
                storageService,
                new LocalDataProcessor(statistics),
                rpcProcessor,
                calibrationService(),
                messageService);
    }

    /** 创建直接返回原消息的校准服务，便于验证处理流程顺序。 */
    private CalibrationProcessingService calibrationService() {
        CalibrationProcessingService service = mock(CalibrationProcessingService.class);
        when(service.process(any(), any())).thenAnswer(
                invocation -> invocation.getArgument(1));
        return service;
    }

    /** 创建测试使用的接口配置快照。 */
    private CollectInterfaceRuntimeConfig interfaceConfig(int rpcEnabled) {
        CollectInterfaceRuntimeConfig config =
                new CollectInterfaceRuntimeConfig();
        config.setInterfaceId(11L);
        config.setHost("127.0.0.1");
        config.setPort(19001);
        config.setRpcEnabled(rpcEnabled);
        config.setProtocolConfigParams("{}");
        config.setDeviceSatelliteId(12L);
        config.setDeviceSatelliteName("设备通道_业务001");
        return config;
    }

    /** 创建版本、积日和长度均合法的最小PDXP报文。 */
    private byte[] validPdxpDatagram() {
        byte[] data = new byte[PdxpParser.TRANSPORT_HEADER_LENGTH + PdxpParser.HEADER_LENGTH];
        data[2] = (byte) 0x80;
        data[26] = 0x01;
        return data;
    }
}
