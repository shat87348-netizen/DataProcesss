package com.example.simulator.service;

import com.example.common.response.PageResult;
import com.example.simulator.controller.SaveRequest;
import com.example.simulator.model.RunRecord;
import com.example.simulator.model.SourceConfig;

/** 模拟源配置管理及运行历史查询接口。 */
public interface SourceService {
    /** 新建基础配置及对应协议参数，返回模拟源编号。 */
    long create(SaveRequest request);

    /** 完整修改未运行的模拟源配置。 */
    void update(SaveRequest request);

    /** 逻辑删除未运行的模拟源，保留历史。 */
    void delete(long id);

    /** 调用方必须已开启事务；锁定配置并确认不存在活动执行。 */
    SourceConfig requireEditable(long id);

    /** 查询包含协议参数的完整详情。 */
    SourceConfig detail(long id);

    /** 按名称和状态分页查询配置。 */
    PageResult<SourceConfig> page(String name, Integer status, int page, int size);

    /** 分页查询指定模拟源的历次运行。 */
    PageResult<RunRecord> history(long id, int page, int size);
}
