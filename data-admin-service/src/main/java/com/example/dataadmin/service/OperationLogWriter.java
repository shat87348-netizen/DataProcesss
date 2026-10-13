package com.example.dataadmin.service;
import com.example.dataadmin.entity.SysRuntimeLog;
import com.example.dataadmin.mapper.SysRuntimeLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
/** 使用独立事务记录操作结果，业务回滚也能留下失败原因。 */
@Service
public class OperationLogWriter {
    private final SysRuntimeLogMapper mapper;
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogWriter.class);
    public OperationLogWriter(SysRuntimeLogMapper mapper) { this.mapper = mapper; }
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void write(SysRuntimeLog log) { mapper.insert(log); }
    /** 日志写入失败保留服务日志，不改变已完成业务操作的结果。 */
    public void reportFailure(Exception exception) {
        LOGGER.error("人工操作日志写入失败，业务操作结果不受影响", exception);
    }
}
