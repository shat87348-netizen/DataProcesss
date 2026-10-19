package com.example.dataadmin.mapper;

import com.example.dataadmin.entity.CollectProtocolConfig;
import com.example.dataadmin.dto.collection.ProtocolQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 协议配置数据访问接口。
 *
 * <p>所有 SQL 统一维护在 CollectProtocolConfigMapper.xml 中。</p>
 */
public interface CollectProtocolConfigMapper {

    /** 按任务 ID 查询协议配置。 */
    List<CollectProtocolConfig> findAllByTaskId(@Param("taskId") String taskId);

    /** 统计符合任务、协议类型和名称筛选条件的记录数。 */
    long count(ProtocolQueryRequest request);

    /** 按相同筛选条件读取当前页协议配置。 */
    List<CollectProtocolConfig> findPage(ProtocolQueryRequest request);

    /** 按主键查询协议配置。 */
    CollectProtocolConfig findById(@Param("id") Long id);

    /** 事务内锁定协议记录，串行化HTTP绑定与协议类型、任务、启用状态的修改。 */
    CollectProtocolConfig findByIdForUpdate(@Param("id") Long id);

    /** 新增协议配置并回填主键。 */
    int insert(CollectProtocolConfig entity);

    /** 更新协议配置。 */
    int update(CollectProtocolConfig entity);

    /** 逻辑删除协议配置。 */
    int delete(@Param("id") Long id);

    /** 更新协议配置启用状态。 */
    int updateEnabled(
            @Param("id") Long id,
            @Param("enabled") Integer enabled);
}
