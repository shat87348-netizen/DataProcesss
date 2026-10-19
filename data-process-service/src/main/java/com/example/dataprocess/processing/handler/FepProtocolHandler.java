package com.example.dataprocess.processing.handler;

import com.example.dataprocess.collection.CollectInterfaceStatistics;
import com.example.common.protocol.fep.FepPacketType;
import com.example.common.protocol.fep.FepProtocolTool;
import com.example.dataprocess.config.FepProperties;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.protocol.fep.FepFileReceiver;
import com.example.dataprocess.service.FepFileResultService;
import com.example.dataprocess.tool.TcpTool;
import com.example.dataprocess.tool.UdpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BooleanSupplier;

/** FEP协议处理器，负责UDP和TCP下的文件接收协议。 */
@Component
public class FepProtocolHandler implements ProtocolHandler {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;

    /** 提交异常上下文，采集线程不直接写数据库。 */
    private void alarm(com.example.dataprocess.entity.CollectInterfaceRuntimeConfig config,
            com.example.common.enums.DataAlarmType type, String detail) {
        if (alarmRecorder != null) { alarmRecorder.record(config, type, detail); }
    }


    /** FEP传输协议。 */
    private static final int TRANSFER_PROTOCOL_FEP = 3;
    /** 01发送请求包除类型字节外的长度。 */
    private static final int SEND_REQUEST_BODY_LENGTH =
            FepProtocolTool.FILE_NAME_LENGTH
                    + FepProtocolTool.FILE_LENGTH_FIELD_LENGTH;
    /** 04数据包除类型和数据内容外的固定长度。 */
    private static final int DATA_HEADER_BODY_LENGTH =
            FepProtocolTool.UNIT_NUMBER_FIELD_LENGTH
                    + FepProtocolTool.FILE_ID_FIELD_LENGTH;
    /** FEP协议日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            FepProtocolHandler.class);
    /** 采集统计。 */
    private final CollectInterfaceStatistics statistics;
    /** 数据处理线程池。 */
    private final ThreadPoolExecutor dataProcessExecutor;
    /** 文件交换接收配置。 */
    private final FepProperties fepProperties;
    /** 完成文件的对象存储和消息发布服务。 */
    private final FepFileResultService fileResultService;

    public FepProtocolHandler(
            CollectInterfaceStatistics statistics,
            @Qualifier("dataProcessExecutor")
            ThreadPoolExecutor dataProcessExecutor,
            FepProperties fepProperties,
            FepFileResultService fileResultService) {
        this.statistics = statistics;
        this.dataProcessExecutor = dataProcessExecutor;
        this.fepProperties = fepProperties;
        this.fileResultService = fileResultService;
    }

    @Override
    public List<Integer> supportedTransferProtocols() {
        return Collections.singletonList(TRANSFER_PROTOCOL_FEP);
    }

    @Override
    public UdpSession createUdpSession(
            CollectInterfaceRuntimeConfig config,
            UdpTool.DatagramEndpoint endpoint) {
        return new FepUdpSession(config, endpoint, createSessionConfig(config));
    }

    @Override
    public TcpSession createTcpSession(CollectInterfaceRuntimeConfig config) {
        return new FepTcpSession(config, createSessionConfig(config));
    }

    /** 根据系统配置为单个接口创建文件接收器。 */
    private SessionConfig createSessionConfig(
            CollectInterfaceRuntimeConfig config) {
        // 第一步：临时目录和数据单元长度只读取配置中心，不读取协议配置表。
        String tempDirectory = fepProperties.getTempDirectory();
        if (tempDirectory == null || tempDirectory.trim().isEmpty()) {
            throw new IllegalStateException("文件交换临时目录不能为空");
        }
        int dataUnitLength = fepProperties.getDataUnitLength();

        // 第二步：每个采集接口使用独立子目录，避免同名文件相互影响。
        Path directory = Paths.get(tempDirectory.trim())
                .resolve("interface-" + config.getInterfaceId());
        return new SessionConfig(
                new FepFileReceiver(directory, dataUnitLength),
                dataUnitLength);
    }

    /** UDP下单个FEP采集接口的协议会话。 */
    private final class FepUdpSession implements UdpSession {

        private final CollectInterfaceRuntimeConfig config;
        private final UdpTool.DatagramEndpoint endpoint;
        private final FepFileReceiver fileReceiver;
        private final Object submitLock = new Object();
        private CompletableFuture<Void> processingTail =
                CompletableFuture.completedFuture(null);

        private FepUdpSession(
                CollectInterfaceRuntimeConfig config,
                UdpTool.DatagramEndpoint endpoint,
                SessionConfig sessionConfig) {
            this.config = config;
            this.endpoint = endpoint;
            this.fileReceiver = sessionConfig.fileReceiver;
        }

        @Override
        public void handle(UdpTool.ReceivedPacket packet) {
            // FEP速率按实际协议流量统计，采集量只在完整文件形成时增加。
            statistics.recordReceivedBytes(config, packet.getData().length);
            synchronized (submitLock) {
                try {
                    processingTail = processingTail
                            .handle((ignored, exception) -> null)
                            .thenRunAsync(
                                    () -> process(packet),
                                    dataProcessExecutor).whenComplete((ignored, failure) -> {
                                        if (failure != null) {
                                            alarm(config, com.example.common.enums.DataAlarmType.PROCESS_QUEUE,
                                                    "文件报文异步提交失败，当前报文未处理：" + failure.getMessage());
                                        }
                                    });
                } catch (RejectedExecutionException exception) {
                    alarm(config, com.example.common.enums.DataAlarmType.PROCESS_QUEUE, "文件报文提交失败，处理线程池已满");
                    LOGGER.error("数据处理线程池已满，本次FEP UDP报文已拒绝，接口编号：{}",
                            config.getInterfaceId(), exception);
                }
            }
        }

        /** 处理一条完整FEP UDP报文。 */
        private void process(UdpTool.ReceivedPacket packet) {
            String fileContext = "报文长度：" + packet.getData().length;
            try {
                FepPacketType packetType =
                        FepProtocolTool.readPacketType(packet.getData());
                switch (packetType) {
                    case SEND_REQUEST:
                        FepProtocolTool.SendRequest request =
                                FepProtocolTool.parseSendRequest(
                                        packet.getData());
                        fileContext = "文件名：" + request.getFileName() + "；声明长度：" + request.getFileLength();
                        LOGGER.info(
                                "FEP UDP第一步：收到01发送请求包，接口编号：{}，文件名：{}，文件长度：{}",
                                config.getInterfaceId(),
                                request.getFileName(),
                                request.getFileLength());
                        // 第一步：数据库记录优先决定文件是否已经可靠接收。
                        boolean recorded = fileResultService
                                .isReceivedAndTriggerRetry(
                                        config,
                                        request.getFileName(),
                                        request.getFileLength());
                        FepFileReceiver.RequestAcceptance acceptance =
                                fileReceiver.acceptRequest(
                                        packet.getData(), recorded);

                        // 第二步：兼容升级前遗留的本地完整文件，补登记后续处理记录。
                        registerLegacyCompletedFile(
                                config, acceptance, recorded);
                        endpoint.send(
                                packet.getSourceAddress(),
                                packet.getSourcePort(),
                                acceptance.getResponsePacket());
                        LOGGER.info(
                                "FEP UDP第二步：发送02请求应答包，接口编号：{}，文件名：{}，续传单元号：{}，文件标识：{}",
                                config.getInterfaceId(),
                                acceptance.getFileName(),
                                acceptance.getResumeUnitNumber(),
                                acceptance.getFileId());
                        break;
                    case DATA:
                        FepFileReceiver.DataReceiveResult result =
                                fileReceiver.receiveData(packet.getData());
                        fileContext = "文件名：" + result.getFileName() + "；单元号：" + result.getUnitNumber() + "；文件标识：" + result.getFileId();
                        LOGGER.info(
                                "FEP UDP第三步：收到04文件数据包，接口编号：{}，文件名：{}，单元号：{}，文件标识：{}",
                                config.getInterfaceId(),
                                result.getFileName(),
                                result.getUnitNumber(),
                                result.getFileId());
                        if (result.isCompleted()) {
                            // 第一步：先登记可靠处理记录，确保确认后仍可自动恢复。
                            LocalDateTime completedTime = LocalDateTime.now();
                            fileResultService.recordAndSubmit(
                                    config,
                                    result.getFilePath(),
                                    completedTime);
                            statistics.recordCollection(config);

                            // 第二步：登记成功后立即确认，不等待MinIO和消息发送。
                            endpoint.send(
                                    packet.getSourceAddress(),
                                    packet.getSourcePort(),
                                    result.getConfirmationPacket());
                            LOGGER.info(
                                    "FEP UDP第四步：发送03结束确认包，接口编号：{}，文件标识：{}，文件：{}",
                                    config.getInterfaceId(),
                                    result.getFileId(),
                                    result.getFilePath());
                        }
                        break;
                    default:
                        LOGGER.warn("FEP UDP接收端收到无需处理的包，接口编号：{}，包类型：{}",
                                config.getInterfaceId(), packetType.getName());
                        break;
                }
            } catch (Exception exception) {
                alarm(config, com.example.common.enums.DataAlarmType.FILE_RECEIVE, fileContext + "；文件协议报文失败：" + exception.getMessage());
                LOGGER.warn("FEP UDP协议包处理失败，接口编号：{}，原因：{}",
                        config.getInterfaceId(), exception.getMessage());
            }
        }
    }

    /** TCP下单个FEP采集接口的协议会话。 */
    private final class FepTcpSession implements TcpSession {

        private final CollectInterfaceRuntimeConfig config;
        private final FepFileReceiver fileReceiver;
        private final int dataUnitLength;
        private final Map<Integer, Integer> fileLengths =
                new ConcurrentHashMap<Integer, Integer>();
        private final Object submitLock = new Object();
        private CompletableFuture<Void> processingTail =
                CompletableFuture.completedFuture(null);

        private FepTcpSession(
                CollectInterfaceRuntimeConfig config,
                SessionConfig sessionConfig) {
            this.config = config;
            this.fileReceiver = sessionConfig.fileReceiver;
            this.dataUnitLength = sessionConfig.dataUnitLength;
        }

        @Override
        public void receive(
                TcpTool.TcpConnection connection,
                BooleanSupplier running) throws IOException {
            while (running.getAsBoolean() && connection.isOpen()) {
                byte[] typeData = connection.receive(1);
                FepPacketType packetType =
                        FepProtocolTool.readPacketType(typeData);
                byte[] packetData;
                switch (packetType) {
                    case SEND_REQUEST:
                        packetData = join(
                                typeData,
                                connection.receive(SEND_REQUEST_BODY_LENGTH));
                        break;
                    case DATA:
                        packetData = receiveDataPacket(connection, typeData);
                        break;
                    default:
                        throw new IOException(
                                "FEP TCP接收端不接受该包类型："
                                        + packetType.getName());
                }
                // TCP协议包字节计入接口速率，不能把文件分片当成采集文件数。
                statistics.recordReceivedBytes(config, packetData.length);
                submit(connection, packetData);
            }
        }

        /** 根据文件长度读取一个完整04数据包。 */
        private byte[] receiveDataPacket(
                TcpTool.TcpConnection connection,
                byte[] typeData) throws IOException {
            byte[] headerBody = connection.receive(DATA_HEADER_BODY_LENGTH);
            ByteBuffer headerBuffer = ByteBuffer.wrap(headerBody)
                    .order(ByteOrder.LITTLE_ENDIAN);
            int unitNumber = headerBuffer.getInt();
            int fileId = Short.toUnsignedInt(headerBuffer.getShort());
            Integer fileLength = fileLengths.get(fileId);
            if (fileLength == null) {
                throw new IOException("FEP数据包使用了未知文件标识：" + fileId);
            }
            long remainingLength = (long) fileLength
                    - (long) unitNumber * dataUnitLength;
            if (remainingLength < 0) {
                throw new IOException("FEP数据单元位置超过声明的文件长度");
            }
            int contentLength = (int) Math.min(
                    remainingLength, dataUnitLength);
            byte[] fixedHeader = join(typeData, headerBody);
            return contentLength == 0
                    ? fixedHeader
                    : join(fixedHeader, connection.receive(contentLength));
        }

        /** 按当前接口的接收顺序提交FEP协议处理。 */
        private void submit(
                TcpTool.TcpConnection connection,
                byte[] packetData) {
            byte[] packetCopy = Arrays.copyOf(packetData, packetData.length);
            synchronized (submitLock) {
                try {
                    processingTail = processingTail
                            .handle((ignored, exception) -> null)
                            .thenRunAsync(
                                    () -> process(connection, packetCopy),
                                    dataProcessExecutor).whenComplete((ignored, failure) -> {
                                        if (failure != null) {
                                            alarm(config, com.example.common.enums.DataAlarmType.PROCESS_QUEUE,
                                                    "文件报文异步提交失败，当前报文未处理：" + failure.getMessage());
                                        }
                                    });
                } catch (RejectedExecutionException exception) {
                    alarm(config, com.example.common.enums.DataAlarmType.PROCESS_QUEUE, "文件报文提交失败，处理线程池已满");
                    LOGGER.error("数据处理线程池已满，本次FEP TCP协议包已拒绝，接口编号：{}",
                            config.getInterfaceId(), exception);
                }
            }
        }

        /** 处理一条完整FEP TCP协议包。 */
        private void process(
                TcpTool.TcpConnection connection,
                byte[] packetData) {
            String fileContext = "报文长度：" + packetData.length;
            try {
                FepPacketType packetType =
                        FepProtocolTool.readPacketType(packetData);
                switch (packetType) {
                    case SEND_REQUEST:
                        FepProtocolTool.SendRequest request =
                                FepProtocolTool.parseSendRequest(packetData);
                        fileContext = "文件名：" + request.getFileName() + "；声明长度：" + request.getFileLength();
                        LOGGER.info(
                                "FEP TCP第一步：收到01发送请求包，接口编号：{}，文件名：{}，文件长度：{}",
                                config.getInterfaceId(),
                                request.getFileName(),
                                request.getFileLength());
                        // 第一步：数据库记录优先决定文件是否已经可靠接收。
                        boolean recorded = fileResultService
                                .isReceivedAndTriggerRetry(
                                        config,
                                        request.getFileName(),
                                        request.getFileLength());
                        FepFileReceiver.RequestAcceptance acceptance =
                                fileReceiver.acceptRequest(
                                        packetData, recorded);

                        // 第二步：兼容升级前遗留的本地完整文件，补登记后续处理记录。
                        registerLegacyCompletedFile(
                                config, acceptance, recorded);
                        if (acceptance.getResumeUnitNumber() >= 0) {
                            fileLengths.put(
                                    acceptance.getFileId(),
                                    request.getFileLength());
                        }
                        connection.send(acceptance.getResponsePacket());
                        LOGGER.info(
                                "FEP TCP第二步：发送02请求应答包，接口编号：{}，文件名：{}，续传单元号：{}，文件标识：{}",
                                config.getInterfaceId(),
                                acceptance.getFileName(),
                                acceptance.getResumeUnitNumber(),
                                acceptance.getFileId());
                        break;
                    case DATA:
                        FepFileReceiver.DataReceiveResult result =
                                fileReceiver.receiveData(packetData);
                        fileContext = "文件名：" + result.getFileName() + "；单元号：" + result.getUnitNumber() + "；文件标识：" + result.getFileId();
                        LOGGER.info(
                                "FEP TCP第三步：收到04文件数据包，接口编号：{}，文件名：{}，单元号：{}，文件标识：{}",
                                config.getInterfaceId(),
                                result.getFileName(),
                                result.getUnitNumber(),
                                result.getFileId());
                        if (result.isCompleted()) {
                            // 第一步：先登记可靠处理记录，确保确认后仍可自动恢复。
                            LocalDateTime completedTime = LocalDateTime.now();
                            fileLengths.remove(result.getFileId());
                            fileResultService.recordAndSubmit(
                                    config,
                                    result.getFilePath(),
                                    completedTime);
                            statistics.recordCollection(config);

                            // 第二步：登记成功后立即确认，不等待MinIO和消息发送。
                            connection.send(result.getConfirmationPacket());
                            LOGGER.info(
                                    "FEP TCP第四步：发送03结束确认包，接口编号：{}，文件标识：{}，文件：{}",
                                    config.getInterfaceId(),
                                    result.getFileId(),
                                    result.getFilePath());
                        }
                        break;
                    default:
                        LOGGER.warn("FEP TCP接收端收到无需处理的包，接口编号：{}，包类型：{}",
                                config.getInterfaceId(), packetType.getName());
                        break;
                }
            } catch (Exception exception) {
                alarm(config, com.example.common.enums.DataAlarmType.FILE_RECEIVE, fileContext + "；文件协议报文失败：" + exception.getMessage());
                LOGGER.warn("FEP TCP协议包处理失败，接口编号：{}，原因：{}",
                        config.getInterfaceId(), exception.getMessage());
            }
        }
    }

    /** 将升级前已经存在的本地完整文件补登记到可靠处理记录。 */
    private void registerLegacyCompletedFile(
            CollectInterfaceRuntimeConfig config,
            FepFileReceiver.RequestAcceptance acceptance,
            boolean recorded) throws Exception {
        // 数据库已有记录、仍需传输或本地文件不存在时均无需补登记。
        if (recorded
                || acceptance.getResumeUnitNumber()
                != FepProtocolTool.FILE_ALREADY_COMPLETE
                || !Files.isRegularFile(acceptance.getFilePath())) {
            return;
        }

        // 遗留完整文件登记成功后会立即进入独立结果线程池。
        fileResultService.recordAndSubmit(
                config,
                acceptance.getFilePath(),
                LocalDateTime.now());

        // 补登记成功后累计完整文件采集量，已有记录由上方判断跳过，避免重复累计。
        statistics.recordCollection(config);
        LOGGER.info("FEP遗留完整文件已经补登记，接口编号：{}，文件：{}",
                config.getInterfaceId(), acceptance.getFilePath());
    }

    /** 单个FEP接口创建时解析出的固定会话配置。 */
    private static final class SessionConfig {

        private final FepFileReceiver fileReceiver;
        private final int dataUnitLength;

        private SessionConfig(
                FepFileReceiver fileReceiver,
                int dataUnitLength) {
            this.fileReceiver = fileReceiver;
            this.dataUnitLength = dataUnitLength;
        }
    }

    /** 拼接一个协议包的相邻字节段。 */
    private static byte[] join(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
