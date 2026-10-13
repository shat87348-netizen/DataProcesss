package com.example.common.alarm;
import com.example.common.enums.DataAlarmType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
/** 跨服务统一构造合并键，同一任务、接口、参数与类型生成相同键。 */
public final class DataAlarmKey {
    private DataAlarmKey() { }
    public static String of(String taskId, DataAlarmType type, Long interfaceId, String symbol, Long channelId) {
        String raw = String.valueOf(taskId) + "|" + type.name() + "|" + interfaceId + "|" + symbol + "|" + channelId;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
