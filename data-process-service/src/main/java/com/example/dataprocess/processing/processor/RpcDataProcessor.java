package com.example.dataprocess.processing.processor;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import SatDataCenter.DataExchange.TmTc.Version.ExchangeTopicType;
import SatDataCenter.DataExchange.TmTc.Version.ProtoHeadInfo;
import SatDataCenter.DataExchange.TmTc.Version.SubTopicName;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.entity.BaselineSatelliteInfo;
import com.example.dataprocess.protocol.rpc.ProtocolConfigParser;
import com.example.dataprocess.protocol.rpc.RpcProtocolField;
import com.example.dataprocess.protocol.rpc.TelemetryRpcClient;
import com.example.dataprocess.protocol.rpc.PdxpDataPayload;
import com.example.dataprocess.tool.PdxpParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tm.processing.v1.Tm;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** RPC开启时使用的远程数据处理器。 */
@Component
public class RpcDataProcessor implements DataProcessor {

    /** RPC数据处理日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            RpcDataProcessor.class);
    /** 数据处理RPC客户端边界。 */
    private final TelemetryRpcClient rpcClient;
    /** RPC协议配置解析器。 */
    private final ProtocolConfigParser protocolConfigParser;

    public RpcDataProcessor(
            TelemetryRpcClient rpcClient,
            ProtocolConfigParser protocolConfigParser) {
        this.rpcClient = rpcClient;
        this.protocolConfigParser = protocolConfigParser;
    }

    /** 构造508字节原始数据请求，并解析RPC返回的遥测Proto消息体。 */
    @Override
    public Optional<TelemetryMessage> process(
            CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet,
            PdxpDataPayload payload) {
        // 记录本帧处理起点，用于统计远程调用和结果解析总耗时。
        long startNanos = System.nanoTime();
        // 第一步：将PDXP发送时间转换成RPC请求使用的协议缓冲区时间戳。
        Instant sendTime = pdxpSendTime(packet);
        Timestamp requestTime = Timestamp.newBuilder()
                .setSeconds(sendTime.getEpochSecond())
                .setNanos(sendTime.getNano())
                .build();

        // 第二步：从当前采集接口关联的协议配置中读取RPC固定字段。
        List<RpcProtocolField> protocolFields =
                protocolConfigParser.parseFields(
                        config.getProtocolConfigParams(),
                        RpcProtocolField.class);

        // 第三步：复用遥测帧还原逻辑，帧头为零时RPC只接收遥测数据。
        byte[] combined = payload.restoreTelemetryFrame();
        // 返回Proto仍使用中间遥测数据，不把RPC请求中还原的帧头带回去。
        byte[] data = payload.getData();
        Tm.FrameRequest.Builder requestBuilder = Tm.FrameRequest.newBuilder()
                .setSatCode("")
                .setChannel("")
                .setDataSource("")
                .setData(ByteString.copyFrom(combined))
                .setSequence(packet.getSequenceNumber())
                .setSendTime(requestTime)
                .setLength(combined.length);

        // 第四步：遍历协议配置，把已配置字段写入FrameRequest。
        for (RpcProtocolField protocolField : protocolFields) {
            // 字段名为空时无法确定目标字段，忽略当前配置项。
            if (protocolField.getField() == null) {
                continue;
            }
            // 配置值统一转换为FrameRequest需要的字符串，空值使用空字符串。
            String fieldValue = protocolField.getValue() == null
                    ? "" : String.valueOf(protocolField.getValue());
            switch (protocolField.getField()) {
                case "sat_code":
                    requestBuilder.setSatCode(fieldValue);
                    break;
                case "channel":
                    requestBuilder.setChannel(fieldValue);
                    break;
                case "data_source":
                    requestBuilder.setDataSource(fieldValue);
                    break;
                default:
                    // 与FrameRequest无关的协议字段暂不处理。
                    break;
            }
        }

        // 第五步：调用RPC，客户端将FrameResponse中的遥测消息序列化为字节。
        byte[] responseData = rpcClient.process(requestBuilder.build());
        try {
            // 第六步：解析完整遥测消息并记录本帧远程处理完成信息。
            TelemetryMessage result = TelemetryMessage.parseFrom(responseData);
            // 使用本次处理时间直接组装RPC返回消息的协议头。

            Instant messageTime = Instant.now();
            ProtoHeadInfo protoHead = ProtoHeadInfo.newBuilder()
                    .setTopicType(ExchangeTopicType.TEST_DATA_TYPE)
                    .setBussiness(SubTopicName.DATA_SAT_PHYVALUE)
                    .setMsgTime(Timestamp.newBuilder()
                            .setSeconds(messageTime.getEpochSecond())
                            .setNanos(messageTime.getNano()))
                    .setTaskId(config.getTaskId())
                    .setMsgSource("数据处理")
                    .build();
            // 返回Proto原码统一使用拆出的中间遥测数据，不依赖RPC返回格式。
            result = result.toBuilder()
                    .setProtoHead(protoHead)
                    .setFrameRawData(ByteString.copyFrom(data))
                    .build();
            // 按返回消息的编码匹配采集启动前加载的卫星和通道名称。
            result = fillNames(config, result);
            long elapsedMillis =
                    (System.nanoTime() - startNanos) / 1_000_000L;
            LOGGER.info(
                    "远程处理完成，接口编号：{}，包序号：{}，参数数量：{}，"
                            + "返回数据长度：{}，处理耗时：{}毫秒",
                    config.getInterfaceId(),
                    packet.getSequenceNumber(),
                    result.getTelemetriesCount(),
                    responseData.length,
                    elapsedMillis);
            return Optional.of(result);
        } catch (InvalidProtocolBufferException exception) {
            // 返回内容不合法时只提示并结束当前帧，不构造虚假遥测消息。
            LOGGER.error("RPC返回数据不是有效的遥测Proto消息，接口编号：{}，包序号：{}",
                    config.getInterfaceId(), packet.getSequenceNumber(), exception);
            return Optional.empty();
        }
    }

    /** 将PDXP积日和当日时标换算为北京时间对应的时间点。 */
    private Instant pdxpSendTime(PdxpParser.PdxpPacket packet) {
        // PDXP日期与当日时长组合后，按东八区转换为时间点。
        LocalDateTime sendTime = LocalDateTime.of(
                packet.getSendDate(), LocalTime.MIDNIGHT)
                .plus(packet.getTimeSinceMidnight());
        return sendTime.toInstant(ZoneOffset.ofHours(8));
    }

    /** 按返回消息编码匹配卫星和通道，缺失的名称保留原值。 */
    private TelemetryMessage fillNames(
            CollectInterfaceRuntimeConfig config, TelemetryMessage message) {
        // 第一步：只按返回的卫星编码定位目录，不使用请求编码替代返回编码。
        BaselineSatelliteInfo satellite = config.getBaselineSatellites()
                .get(message.getSatCode().trim());
        if (satellite == null) {
            LOGGER.warn("远程结果未匹配基线卫星，接口编号：{}，卫星编码：{}",
                    config.getInterfaceId(), message.getSatCode());
            return message;
        }
        TelemetryMessage.Builder builder = message.toBuilder();
        if (!satellite.getSatelliteName().trim().isEmpty()) {
            builder.setSatName(satellite.getSatelliteName());
        } else {
            LOGGER.warn("基线卫星名称为空，接口编号：{}，卫星编码：{}",
                    config.getInterfaceId(), message.getSatCode());
        }
        // 第二步：只在命中卫星自己的通道列表中查找，不跨卫星匹配通道。
        String channelName = satellite.getChannelNames().get(message.getChannelCode().trim());
        if (channelName != null && !channelName.trim().isEmpty()) {
            builder.setChannelName(channelName);
        } else {
            LOGGER.warn("远程结果未匹配基线通道名称，接口编号：{}，卫星编码：{}，通道编码：{}",
                    config.getInterfaceId(), message.getSatCode(), message.getChannelCode());
        }
        return builder.build();
    }
}
