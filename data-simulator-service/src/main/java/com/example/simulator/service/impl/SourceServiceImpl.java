package com.example.simulator.service.impl;

import com.example.simulator.enums.ProtocolProfile;
import com.example.simulator.controller.SaveRequest;
import com.example.simulator.service.SourceService;
import com.example.simulator.model.*;
import com.example.simulator.mapper.SimulatorMapper;
import com.example.simulator.protocol.PdxpEncoder;
import com.example.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 配置事务与查询服务，不在数据库事务中等待网络。 */
@Service
public class SourceServiceImpl implements SourceService {
    private final SimulatorMapper mapper;

    public SourceServiceImpl(SimulatorMapper mapper) {
        // 注入配置与运行记录的统一数据访问组件。
        this.mapper = mapper;
    }

    /** 创建基础配置及可选PDXP参数，任何失败都回滚。 */
    @Override
    @Transactional
    public long create(SaveRequest request) {
        // 校验新建标识及协议参数，再在当前事务中保存关联配置。
        if (request.id != null) {
            throw new IllegalArgumentException("新建时不能指定主键");
        }
        SourceConfig config = request.toConfig();
        validateProtocol(config);
        mapper.insertSource(config);
        if (config.pdxp != null) {
            config.pdxp.sourceId = config.id;
            mapper.insertPdxp(config.pdxp);
        }
        return config.id;
    }

    /** 锁定配置行，与启动事务串行，禁止运行中编辑。 */
    @Override
    @Transactional
    public void update(SaveRequest request) {
        // 锁定可编辑配置并确认协议未切换，再更新基础字段及协议参数。
        if (request.id == null) {
            throw new IllegalArgumentException("修改时必须指定主键");
        }
        SourceConfig previous = requireEditable(request.id);
        SourceConfig config = request.toConfig();
        if (!previous.transferProtocol.equals(config.transferProtocol)) {
            throw new IllegalArgumentException("创建后不能切换协议，请新建模拟源");
        }
        validateProtocol(config);
        mapper.updateSource(config);
        if (config.pdxp != null) {
            config.pdxp.sourceId = config.id;
            mapper.updatePdxp(config.pdxp);
        }
    }

    /** 逻辑删除保留全部历史执行记录。 */
    @Override
    @Transactional
    public void delete(long id) {
        // 在同一事务中检查活动任务并标记删除。
        requireEditable(id);
        mapper.deleteSource(id);
    }

    /** 启动事务中锁定配置，确保快照对应同一次有效配置。 */
    @Override
    public SourceConfig requireEditable(long id) {
        // 沿用调用方事务取得行锁，防止启动与编辑同时通过检查。
        SourceConfig source = mapper.lockSource(id);
        if (source == null) {
            throw new IllegalArgumentException("模拟源不存在或已删除");
        }
        if (mapper.activeCount(id) > 0) {
            throw new IllegalStateException("模拟源正在执行，请先停止");
        }
        return source;
    }

    /** 读取完整协议详情。 */
    @Override
    public SourceConfig detail(long id) {
        // 先读取有效基础配置，仅对PDXP追加专用参数。
        SourceConfig source = mapper.findSource(id);
        if (source == null) {
            throw new IllegalArgumentException("模拟源不存在或已删除");
        }
        if (ProtocolProfile.of(source.transferProtocol) == ProtocolProfile.PDXP) {
            source.pdxp = mapper.findPdxp(id);
        }
        return source;
    }

    /** 分页筛选，状态零代表未启动。 */
    @Override
    public PageResult<SourceConfig> page(String name, Integer status, int page, int size) {
        // 校验分页和状态条件，使用相同条件查询数量与记录。
        validatePage(page, size);
        if (status != null && (status < 0 || status > 6)) {
            throw new IllegalArgumentException("运行状态筛选值无效");
        }
        return new PageResult<>(page, size, mapper.countSources(name, status),
                mapper.pageSources(name, status, (long) (page - 1) * size, size));
    }

    /** 删除后的模拟源仍可按主键查询历史。 */
    @Override
    public PageResult<RunRecord> history(long id, int page, int size) {
        // 只校验分页范围，保留查询已删除模拟源历史的能力。
        validatePage(page, size);
        return new PageResult<>(page, size, mapper.countRuns(id),
                mapper.pageRuns(id, (long) (page - 1) * size, size));
    }

    /** 控制分页范围，避免无限制扫描历史。 */
    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("页码必须大于零，每页数量必须在1到100之间");
        }
    }

    /** PDXP参数在落库前完成校验。 */
    private void validateProtocol(SourceConfig config) {
        if (config.pdxp != null) {
            PdxpEncoder.validate(config.pdxp);
        }
    }
}

