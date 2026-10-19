
package com.example.dataprocess.processing.processor;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.StateType;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.Telemetry;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryType;
import SatDataCenter.DataExchange.TmTc.Version.ExchangeTopicType;
import SatDataCenter.DataExchange.TmTc.Version.ProtoHeadInfo;
import SatDataCenter.DataExchange.TmTc.Version.SubTopicName;
import com.example.dataprocess.collection.CollectInterfaceStatistics;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.enums.LocalFormulaType;
import com.example.dataprocess.mapper.TelemetryCodeMappingMapper;
import com.example.dataprocess.protocol.rpc.PdxpDataPayload;
import com.example.dataprocess.protocol.rpc.PdxpProtocolField;
import com.example.dataprocess.protocol.rpc.ProtocolConfigParser;
import com.example.dataprocess.tool.LittleEndianBitReader;
import com.example.dataprocess.tool.PdxpParser;
import com.example.dataprocess.tool.TelemetryFormulaEvaluator;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用采集接口关联协议配置完成本地解析。 */
@Component
public class LocalDataProcessor implements DataProcessor {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;

    /** 提交异常上下文，采集线程不直接写数据库。 */
    private void alarm(com.example.dataprocess.entity.CollectInterfaceRuntimeConfig config,
            com.example.common.enums.DataAlarmType type, String detail) {
        if (alarmRecorder != null) { alarmRecorder.record(config, type, detail); }
    }

    /** 时间参数的计数起点，直接使用协议约定的日历时间，不作时区偏移。 */
    private static final LocalDateTime PARAMETER_TIME_EPOCH =
            LocalDateTime.of(2000, 1, 1, 12, 0);
    /** 四位秒小数完整表达十分之一毫秒精度。 */
    private static final DateTimeFormatter PARAMETER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSS");
    /** 本地数据处理日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            LocalDataProcessor.class);
    /** 状态范围格式。 */
    private static final Pattern RANGE = Pattern.compile(
            "^\\s*([^:：/]+)\\s*[:：]\\s*\\[\\s*([^,，]+)\\s*[,，]\\s*([^\\]]+)\\s*\\]\\s*$");
    /** 本地处理去重数量统计组件。 */
    private final CollectInterfaceStatistics statistics;

    /** 运行时字段和设备名称由采集接口快照提供。 */
    @Autowired
    public LocalDataProcessor(CollectInterfaceStatistics statistics) {
        this.statistics = statistics;
    }

    /** 保留既有离线构造入口，解析器和查询组件已不参与逐帧处理。 */
    public LocalDataProcessor(
            ProtocolConfigParser parser,
            TelemetryCodeMappingMapper mappingMapper,
            CollectInterfaceStatistics statistics) {
        this(statistics);
    }

    /** 按字段位宽解码并组装遥测消息。 */
    @Override
    public Optional<TelemetryMessage> process(
            CollectInterfaceRuntimeConfig config,
            PdxpParser.PdxpPacket packet,
            PdxpDataPayload payload) {
        // 记录本帧处理起点，用于输出完整处理耗时。
        long startNanos = System.nanoTime();
        // 第一步：解析并校验协议配置字段。
        List<PdxpProtocolField> fields = fields(config.getPdxpFields());
        byte[] data = payload.getData();
        LittleEndianBitReader reader = new LittleEndianBitReader(data);
        Instant time = packet.getSendDate().atStartOfDay()
                .plus(packet.getTimeSinceMidnight()).toInstant(ZoneOffset.ofHours(8));
        FrameIdentity identity = frameIdentity(config);
        // 当前处理时间用于标识消息实际完成组装的时间点。
        Instant messageTime = Instant.now();
        ProtoHeadInfo protoHead = ProtoHeadInfo.newBuilder()
                .setTopicType(ExchangeTopicType.TEST_DATA_TYPE)
                .setBussiness(resolveBusiness(identity.businessId))
                .setMsgTime(Timestamp.newBuilder()
                        .setSeconds(messageTime.getEpochSecond())
                        .setNanos(messageTime.getNano()))
                .setTaskId(config.getTaskId())
                .setMsgSource("数据处理")
                .build();
        // 本地处理不填写卫星编码，帧标识只保留通道名称和业务标识。
        TelemetryMessage.Builder message = TelemetryMessage.newBuilder()
                .setProtoHead(protoHead)
                .setTime(Timestamp.newBuilder().setSeconds(time.getEpochSecond()).setNanos(time.getNano()))
                .setFrameRawData(ByteString.copyFrom(data))
                .setChannelName(identity.channelName)
                .setTmType(TelemetryType.TM_TYPE_DEVICE_TM)
                .setBussinessId(identity.businessId)
                .setFrameCheckStatus(true);
        // 第二步：依次取位并构造每个遥测对象，相同遥测代号只保留最后一项。
        Set<String> decodedSymbols = new HashSet<String>();
        long duplicateCount = 0L;
        for (PdxpProtocolField item : fields) {
            byte[] raw = reader.read(item.getBitWidth());
            BigInteger value = unsigned(raw);
            String tmSymbol = item.getTelemetryCode().trim();
            boolean duplicate = !decodedSymbols.add(tmSymbol);
            Telemetry.Builder telemetry = Telemetry.newBuilder()
                    .setTableIndex(item.getTableIndex())
                    .setTmSymbol(tmSymbol)
                    .setRawData(ByteString.copyFrom(raw));
            // 按公式类型计算物理量，同时填写消息字段类型和展示文本。
            fillValue(telemetry, item, raw, value);
            if (duplicate) {
                // 后出现的同代号参数覆盖前值，并在最终Proto中标记发生过去重。
//                telemetry.setIsDepValue(true);
                duplicateCount++;
            }
            // 每个遥测参数保存自己对应的校准公式，不再作为帧级通道编号使用。
            if (text(item.getCalibrationFormula())) {
                telemetry.setCalibrationFormula(
                        item.getCalibrationFormula().trim());
            }
            if (text(item.getTelemetryName())) {
                telemetry.setTmName(item.getTelemetryName().trim());
            }
            if (text(item.getSystemName())) {
                telemetry.setSystemName(item.getSystemName().trim());
            }
            state(telemetry, item, value);
            recordState(config, telemetry, item, value);
            message.putTelemetries(
                    telemetry.getTmSymbol(), telemetry.build());
        }
        // 第三步：每个被覆盖参数计入处理统计，最终只保留后出现的参数。
        statistics.recordDuplicate(config, duplicateCount);
        // 第四步：构造结果并记录本帧本地处理完成信息。
        TelemetryMessage result = message.build();
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        LOGGER.info(
                "本地处理完成，接口编号：{}，包序号：{}，参数数量：{}，"
                        + "原始数据长度：{}，处理耗时：{}毫秒",
                config.getInterfaceId(),
                packet.getSequenceNumber(),
                result.getTelemetriesCount(),
                data.length,
                elapsedMillis);
        return Optional.of(result);
    }

    /** 根据消息业务标识确定本地处理子主题。 */
    private SubTopicName resolveBusiness(String businessId) {
        // 05开头的业务标识使用测试测量物理量子主题。
        if (trim(businessId).startsWith("05")) {
            return SubTopicName.DATA_SUBSYSTEM_CSCL_PHYVALUE;
        }
        // 其他或空业务标识按约定使用同一默认子主题。
        return SubTopicName.DATA_SUBSYSTEM_CSCL_PHYVALUE;
    }

    /** 将原码转换为配置指定的物理量、字符串或时间。 */
    private void fillValue(
            Telemetry.Builder telemetry,
            PdxpProtocolField field,
            byte[] raw,
            BigInteger decodedValue) {
        LocalFormulaType type = LocalFormulaType.fromCode(field.getFormulaType());
        telemetry.setValueType(type.getValueType());
        try {
            // 字符串保持原始字节顺序，不将文本当作整数或代入公式。
            if (type == LocalFormulaType.TEXT) {
                if (field.getBitWidth() % 8 != 0) {
                    throw new IllegalArgumentException("字符串参数位宽必须是8的整数倍");
                }
                telemetry.setValueText(new String(raw, StandardCharsets.UTF_8));
                return;
            }
            // 未约定的旧类型保持已有原码语义，避免改变存量协议行为。
            if (type == LocalFormulaType.RAW) {
                telemetry.setValue(decodedValue.doubleValue())
                        .setValueText(decodedValue.toString());
                return;
            }
            BigDecimal physicalValue = TelemetryFormulaEvaluator.evaluate(
                    field.getFormulaDesc(), field.getProcessParam(), new BigDecimal(decodedValue));
            if (type == LocalFormulaType.TIME) {
                // 时间值必须是完整计数，禁止舍入掉十分之一毫秒信息。
                long ticks = physicalValue.longValueExact();
                if (ticks < -9007199254740992L || ticks > 9007199254740992L) {
                    throw new IllegalArgumentException("时间计数超出消息数值字段的精确表示范围");
                }
                LocalDateTime time = PARAMETER_TIME_EPOCH
                        .plusSeconds(Math.floorDiv(ticks, 10000L))
                        .plusNanos(Math.floorMod(ticks, 10000L) * 100000L);
                telemetry.setValue(ticks).setValueText(time.format(PARAMETER_TIME_FORMAT));
                return;
            }
            // 数值类型按配置的小数位数四舍五入，文本保留末尾零。
            if (configuredRange(field.getDecimalPlaces())) {
                int places = Integer.parseInt(field.getDecimalPlaces().trim());
                if (places < 0 || places > 340) {
                    throw new IllegalArgumentException("小数位数必须在0到340之间");
                }
                physicalValue = physicalValue.setScale(places, RoundingMode.HALF_UP);
            }
            double value = type == LocalFormulaType.FLOAT
                    ? (double) physicalValue.floatValue() : physicalValue.doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("物理量超出配置数据类型的有效范围");
            }
            telemetry.setValue(value).setValueText(physicalValue.toPlainString());
        } catch (RuntimeException exception) {
            // 错误配置不能静默生成错误物理量，保留参数身份供上层日志定位。
            throw new IllegalArgumentException("本地参数转换失败，遥测代号："
                    + field.getTelemetryCode() + "，原因：" + exception.getMessage(), exception);
        }
    }

    /** 转换、校验并排序配置字段。 */
    private List<PdxpProtocolField> fields(List<PdxpProtocolField> parsedFields) {
        if (parsedFields.isEmpty()) {
            throw new IllegalArgumentException("当前接口未加载有效的参数解析配置");
        }
        // 创建可排序副本，缓存中的协议字段列表保持只读和原始顺序。
        List<PdxpProtocolField> result = new ArrayList<PdxpProtocolField>(
                parsedFields);
        Set<Integer> indexes = new HashSet<Integer>();
        // 每项必须具有唯一序号、正位宽和遥测代号。
        for (PdxpProtocolField item : result) {
            Integer index = item.getTableIndex();
            Integer width = item.getBitWidth();
            if (item.getId() == null || index == null || index < 0
                    || !indexes.add(index) || width == null || width <= 0
                    || !text(item.getTelemetryCode())) {
                throw new IllegalArgumentException("协议字段配置无效，序号：" + item.getTableIndex());
            }
        }
        result.sort(Comparator.comparingInt(
                PdxpProtocolField::getTableIndex));
        return result;
    }

    /** 设置报警状态；报警时完全不写状态索引。 */
    private void state(Telemetry.Builder telemetry, PdxpProtocolField field, BigInteger value) {
        if (!"1".equals(trim(field.getAlarmFlag()))) {
            return;
        }
        // 预警范围未配置或使用横线占位时，继续回退到正常值范围。
        String ranges = configuredRange(field.getWarningValue())
                ? field.getWarningValue() : field.getNormalValue();
        StateMatch match = stateMatch(ranges, new BigDecimal(value));
        if (match == null) {
            telemetry.setStateType(StateType.STATE_TYPE_ALARM);
        } else {
            telemetry.setStateType(StateType.STATE_TYPE_NORMAL)
                    .setStateIndex(match.index)
                    .setStateName(match.name);
        }
    }

    /** 范围检查关闭时不生成数据告警，缺失规则作为配置异常记录。 */
    private void recordState(CollectInterfaceRuntimeConfig config, Telemetry.Builder telemetry,
            PdxpProtocolField field, BigInteger value) {
        if (alarmRecorder == null || !Integer.valueOf(1).equals(config.getParameterRangeCheckEnabled())
                || !"1".equals(trim(field.getAlarmFlag()))) { return; }
        String ranges = configuredRange(field.getWarningValue()) ? field.getWarningValue() : field.getNormalValue();
        if (!configuredRange(ranges)) {
            alarmRecorder.record(config, com.example.common.enums.DataAlarmType.PROCESS_CONFIG,
                    field.getTelemetryCode(), field.getTelemetryName(), null, null,
                    "参数已开启报警，但未配置有效范围；停止该参数的告警判定");
            // 无范围不能把所有值都误判为越限。
            telemetry.clearStateType();
        } else if (telemetry.getStateType() == StateType.STATE_TYPE_ALARM) {
            alarmRecorder.record(config, com.example.common.enums.DataAlarmType.PARAMETER_RANGE,
                    field.getTelemetryCode(), field.getTelemetryName(), null, ranges,
                    "判定原码值：" + value + "；未命中允许范围，当前任务继续处理");
        } else {
            alarmRecorder.recover(config, com.example.common.enums.DataAlarmType.PARAMETER_RANGE,
                    field.getTelemetryCode(), null);
        }
    }

    /** 查找包含当前值的首个闭区间及其中文状态名称。 */
    private StateMatch stateMatch(String ranges, BigDecimal value) {
        // 空值和常用横线占位符都表示没有配置状态范围。
        if (!configuredRange(ranges)) {
            return null;
        }
        String[] states = ranges.split("/", -1);
        // 状态索引按照配置顺序从零开始。
        for (int index = 0; index < states.length; index++) {
            Matcher matcher = RANGE.matcher(states[index]);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("状态范围格式不正确：" + states[index]);
            }
            BigDecimal lower = decimal(matcher.group(2), states[index]);
            BigDecimal upper = decimal(matcher.group(3), states[index]);
            if (lower.compareTo(upper) > 0) {
                throw new IllegalArgumentException("状态范围下限不能大于上限：" + states[index]);
            }
            if (value.compareTo(lower) >= 0 && value.compareTo(upper) <= 0) {
                return new StateMatch(index, matcher.group(1).trim());
            }
        }
        return null;
    }

    /** 将小端原码转换为无符号整数。 */
    private BigInteger unsigned(byte[] raw) {
        byte[] reversed = new byte[raw.length];
        // 反转副本供大整数读取，消息中的原码保持原顺序。
        for (int index = 0; index < raw.length; index++) {
            reversed[raw.length - index - 1] = raw[index];
        }
        return new BigInteger(1, reversed);
    }

    /** 查询并拆分当前帧对应的设备工作表名称。 */
    private FrameIdentity frameIdentity(CollectInterfaceRuntimeConfig config) {
        // 设备名称在接口同步时加载，处理线程只读取当前快照。
        String deviceName = trim(config.getDeviceSatelliteName());
        return splitFrameIdentity(deviceName);
    }

    /** 将设备名称拆成通道名称和业务标识。 */
    private FrameIdentity splitFrameIdentity(String deviceName) {
        int separator = deviceName.indexOf('_');
        if (separator <= 0 || separator == deviceName.length() - 1) {
            throw new IllegalArgumentException(
                    "设备名称必须使用“通道名称_业务标识”格式：" + deviceName);
        }
        return new FrameIdentity(
                deviceName.substring(0, separator).trim(),
                deviceName.substring(separator + 1).trim());
    }

    /** 解析状态边界。 */
    private BigDecimal decimal(String value, String range) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("状态范围数值不正确：" + range);
        }
    }

    /** 判断字符串具有内容。 */
    private boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 判断状态范围不是空值或横线占位符。 */
    private boolean configuredRange(String value) {
        if (!text(value)) {
            return false;
        }
        String normalized = value.trim();
        // 只判断整个字段，范围内的负数符号仍按正常数值解析。
        return !"-".equals(normalized)
                && !"—".equals(normalized)
                && !"–".equals(normalized);
    }

    /** 清理可空字符串。 */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 保存设备名称拆分得到的通道名称和业务标识。 */
    private static final class FrameIdentity {
        /** 通道名称。 */
        private final String channelName;
        /** 业务标识。 */
        private final String businessId;

        /** 保存已经校验通过的名称组成部分。 */
        private FrameIdentity(
                String channelName,
                String businessId) {
            if (channelName.isEmpty()
                    || businessId.isEmpty()) {
                throw new IllegalArgumentException("设备名称中的通道名称和业务标识不能为空");
            }
            this.channelName = channelName;
            this.businessId = businessId;
        }
    }

    /** 保存命中的状态序号和中文名称。 */
    private static final class StateMatch {
        /** 状态序号。 */
        private final int index;
        /** 状态中文名称。 */
        private final String name;

        /** 保存命中的状态信息。 */
        private StateMatch(int index, String name) {
            this.index = index;
            this.name = name;
        }
    }
}
