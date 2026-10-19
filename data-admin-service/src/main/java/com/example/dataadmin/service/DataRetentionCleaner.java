package com.example.dataadmin.service;

import com.example.dataadmin.enums.DataRetentionCategory;
import com.example.dataadmin.mapper.DataRetentionMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 每批清理使用独立事务，避免把全部历史数据放入一个长事务。 */
@Service
public class DataRetentionCleaner {
    private final DataRetentionMapper mapper;

    public DataRetentionCleaner(DataRetentionMapper mapper) { this.mapper = mapper; }

    /** 固定分类经枚举校验后交给XML选择对应表。 */
    @Transactional
    public int cleanBatch(DataRetentionCategory category, LocalDateTime beforeTime, int limit) {
        return mapper.deleteExpiredBatch(category.getValue(), beforeTime, limit);
    }

}
