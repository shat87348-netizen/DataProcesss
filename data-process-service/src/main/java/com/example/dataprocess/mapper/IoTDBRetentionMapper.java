package com.example.dataprocess.mapper;

import java.util.Collections;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

/** 复用查询模块的XML语句渲染方式，IoTDB删除语句不写在业务代码中。 */
@Component
public class IoTDBRetentionMapper {
    private final SqlSessionFactory factory;

    public IoTDBRetentionMapper(SqlSessionFactory factory) { this.factory = factory; }

    /** 只接受正数毫秒时间戳，删除路径固定限定为本项目数据库。 */
    public String deleteBefore(long beforeTime) {
        if (beforeTime <= 0) {
            throw new IllegalArgumentException("遥测清理截止时间必须晚于时间戳起点");
        }
        return factory.getConfiguration()
                .getMappedStatement(IoTDBRetentionMapper.class.getName() + ".deleteBefore")
                .getBoundSql(Collections.singletonMap("beforeTime", beforeTime)).getSql();
    }
}
