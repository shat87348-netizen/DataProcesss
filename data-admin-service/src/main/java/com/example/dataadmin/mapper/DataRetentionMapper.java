package com.example.dataadmin.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/** 数据过期清理的固定表映射，所有删除语句维护在XML中。 */
public interface DataRetentionMapper {
    /** 分批物理删除指定分类的过期记录。 */
    int deleteExpiredBatch(@Param("category") String category,
            @Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);
}
