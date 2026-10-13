package com.example.dataadmin.service;

import com.example.common.response.PageResult;
import com.example.dataadmin.dto.collection.InterfaceQueryRequest;
import com.example.dataadmin.dto.collection.InterfaceSaveRequest;
import com.example.dataadmin.dto.collection.ProtocolSaveRequest;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.vo.collection.CollectionOverviewVO;
import com.example.dataadmin.vo.collection.ProtocolConfigVO;

import java.util.List;

/**
 * 数据采集业务接口。
 */
public interface DataCollectionService {

    /** 查询采集页面顶部统计数据。 */
    CollectionOverviewVO getOverview(String taskId);

    /** 分页查询采集接口。 */
    PageResult<CollectInterfaceConfig> pageInterfaceConfigs(
            InterfaceQueryRequest request);

    /** 查询采集接口详情。 */
    CollectInterfaceConfig getInterfaceConfig(Long id);

    /** 新增采集接口。 */
    Long createInterfaceConfig(InterfaceSaveRequest request);

    /** 修改采集接口。 */
    void updateInterfaceConfig(Long id, InterfaceSaveRequest request);

    /** 删除采集接口。 */
    void deleteInterfaceConfig(Long id);

    /** 修改采集接口启用状态。 */
    void updateInterfaceEnabled(Long id, Integer enabled);

    /** 查询协议配置列表。 */
    List<ProtocolConfigVO> listProtocolConfigs(String taskId);

    /** 查询协议配置详情。 */
    ProtocolConfigVO getProtocolConfig(Long id);

    /** 新增协议配置。 */
    Long createProtocolConfig(ProtocolSaveRequest request);

    /** 修改协议配置。 */
    void updateProtocolConfig(Long id, ProtocolSaveRequest request);

    /** 删除协议配置。 */
    void deleteProtocolConfig(Long id);

    /** 修改协议配置启用状态。 */
    void updateProtocolEnabled(Long id, Integer enabled);


}
