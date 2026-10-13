package com.example.dataadmin.service;
import com.example.common.auth.LoginUser;
import com.example.dataadmin.entity.SysRuntimeLog;
import com.example.dataadmin.enums.SystemLogLevel;
import com.example.dataadmin.enums.SystemLogSource;
import com.example.security.context.LoginUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
/** 统一构造人工操作日志，延后到业务事务提交后写入。 */
@Service
public class OperationLogService {
    private final OperationLogWriter writer;
    public OperationLogService(OperationLogWriter writer) { this.writer = writer; }
    public void record(String taskId, SystemLogSource source, SystemLogLevel level, String content, String detail) {
        record(taskId, source, level, content, detail, LoginUserContext.get());
    }
    public void record(String taskId, SystemLogSource source, SystemLogLevel level, String content, String detail, LoginUser user) {
        SysRuntimeLog log = new SysRuntimeLog();
        log.setTaskId(taskId);
        log.setLogTime(LocalDateTime.now());
        // 来源由调用方传枚举，数据库只写入英文编码，禁止自由填写中文来源。
        log.setLogSource(source.getValue());
        log.setLogLevel(level.getCode());
        log.setLogContent(content.length() <= 500 ? content : content.substring(0, 500));
        log.setOperatorId(user == null || user.getUserId() == null ? null : user.getUserId().toString());
        log.setLogDetail("操作人：" + (user == null ? "未取得登录身份" : user.getUsername())
                + "；操作人ID：" + log.getOperatorId() + "\n" + detail);
        Runnable save = () -> {
            try { writer.write(log); } catch (Exception exception) { writer.reportFailure(exception); }
        };
        // 在业务事务提交前不生成成功日志，操作人快照在当前线程先固定。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { save.run(); }
            });
        } else {
            save.run();
        }
    }
}
