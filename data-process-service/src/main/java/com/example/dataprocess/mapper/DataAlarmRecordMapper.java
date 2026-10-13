package com.example.dataprocess.mapper;
import com.example.common.alarm.DataAlarmRecord;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
/** 自动数据告警统一通过XML持久化，不依赖管理服务HTTP调用。 */
public interface DataAlarmRecordMapper {
    /** 跨实例串行化同一告警合并键，锁随事务结束释放。 */
    void lockKey(@Param("key") String key);
    DataAlarmRecord findRecent(@Param("key") String key, @Param("since") LocalDateTime since);
    int insert(DataAlarmRecord record);
    int merge(DataAlarmRecord record);
    int recover(DataAlarmRecord record);
    /** 后台重试时重新读取接口身份，不虚构接口名称。 */
    CollectInterfaceRuntimeConfig findInterface(@Param("id") Long id);
}
