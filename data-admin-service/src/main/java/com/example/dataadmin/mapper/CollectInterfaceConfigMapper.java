package com.example.dataadmin.mapper;

import com.example.dataadmin.dto.collection.InterfaceQueryRequest;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.vo.collection.CollectionOverviewVO;
import com.example.dataadmin.vo.processing.CollectInterfaceOptionVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采集接口配置数据访问接口。
 *
 * <p>所有 SQL 统一维护在 CollectInterfaceConfigMapper.xml 中。</p>
 */
public interface CollectInterfaceConfigMapper {
    /** 查询全量未删除采集接口及其启用数量。 */
    com.example.dataadmin.vo.collection.CollectionOverviewVO findNodeCounts();

    /** 查询当前最大任务下启用且未删除的采集接口主键。 */
    List<Long> findAllEnabledIds();

    /** 查询数据处理页面使用的已启用采集接口筛选项。 */
    List<CollectInterfaceOptionVO> findEnabledOptions();

    /** 按任务 ID 查询采集接口。 */
    List<CollectInterfaceConfig> findAllByTaskId(@Param("taskId") String taskId);

    /** 按筛选条件分页查询采集接口。 */
    List<CollectInterfaceConfig> findPage(InterfaceQueryRequest request);

    /** 分页完成后，只汇总当前页接口的历史采集量，包含已归档窗口。 */
    List<CollectInterfaceConfig> findCollectionCounts(
            @Param("taskId") String taskId,
            @Param("interfaceIds") List<Long> interfaceIds);

    /** 统计符合筛选条件的采集接口数量。 */
    long count(InterfaceQueryRequest request);

    /** 按试验任务查询采集页面顶部统计数据。 */
    CollectionOverviewVO findOverview(@Param("taskId") String taskId);

    /** 按主键查询采集接口。 */
    CollectInterfaceConfig findById(@Param("id") Long id);

    /** 查询协议配置绑定的全部未删除采集接口名称，不区分启用状态。 */
    List<String> findActiveNamesByProtocolConfigId(
            @Param("protocolConfigId") Long protocolConfigId);

    /** 统计相同监听配置的已启用接口数量。 */
    long countEnabledEndpointConflicts(
            @Param("transferType") Integer transferType,
            @Param("host") String host,
            @Param("port") Integer port,
            @Param("multicastIp") String multicastIp,
            @Param("excludeId") Long excludeId);

    /** 新增采集接口并回填主键。 */
    int insert(CollectInterfaceConfig entity);

    /** 更新采集接口。 */
    int update(CollectInterfaceConfig entity);

    /** 逻辑删除采集接口。 */
    int delete(@Param("id") Long id);

    /** 更新接口启用状态。 */
    int updateEnabled(
            @Param("id") Long id,
            @Param("enabled") Integer enabled);
}
