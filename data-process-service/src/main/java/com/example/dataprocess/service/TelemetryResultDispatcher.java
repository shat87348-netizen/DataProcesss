package com.example.dataprocess.service;

import SatDataCenter.DataExchange.TmTc.TelemetryMessages.TelemetryMessage;
import com.example.common.enums.DataAlarmType;
import com.example.dataprocess.config.DataProcessingThreadPoolConfig;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 使用独立有界线程池提交遥测存储与消息任务，故障互不阻塞。 */
@Service
public class TelemetryResultDispatcher {
    /** 异步结果处理日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryResultDispatcher.class);
    /** 遥测存储服务及其专用执行器。 */
    private final IoTDBTelemetryStorageService storageService;
    private final Executor storageExecutor;
    /** 消息发送服务及其专用执行器。 */
    private final TelemetryMessageService messageService;
    private final Executor messageExecutor;
    /** 复用现有异步告警记录组件。 */
    private final DataAlarmRecorder alarmRecorder;
    /** 两个结果线程池共享的关闭等待上限。 */
    private final int shutdownWaitSeconds;

    /** 运行环境创建专用线程池，默认值兼容尚未补充新配置的部署。 */
    @Autowired
    public TelemetryResultDispatcher(
            IoTDBTelemetryStorageService storageService,
            TelemetryMessageService messageService,
            DataAlarmRecorder alarmRecorder,
            @Value("${data-processing.result.storage-thread-count:4}") int storageThreads,
            @Value("${data-processing.result.storage-queue-capacity:2048}") int storageQueue,
            @Value("${data-processing.result.message-thread-count:4}") int messageThreads,
            @Value("${data-processing.result.message-queue-capacity:2048}") int messageQueue,
            @Value("${data-processing.result.shutdown-wait-seconds:30}") int shutdownWaitSeconds) {
        // 第一步：先校验全部配置，避免部分线程池创建后配置校验失败。
        if (storageThreads <= 0 || storageQueue <= 0 || messageThreads <= 0
                || messageQueue <= 0 || shutdownWaitSeconds <= 0) {
            throw new IllegalArgumentException("遥测结果线程数、队列容量和关闭等待时间必须大于零");
        }
        // 第二步：复用中文线程工厂，两类任务分别排队并明确拒绝超量任务。
        this.storageService = storageService;
        this.messageService = messageService;
        this.alarmRecorder = alarmRecorder;
        this.storageExecutor = createExecutor(storageThreads, storageQueue, "遥测入库线程-");
        this.messageExecutor = createExecutor(messageThreads, messageQueue, "遥测消息线程-");
        this.shutdownWaitSeconds = shutdownWaitSeconds;
    }

    /** 保留离线构造能力，由调用方提供执行策略，不创建后台线程。 */
    public TelemetryResultDispatcher(IoTDBTelemetryStorageService storageService,
            TelemetryMessageService messageService, Executor storageExecutor,
            Executor messageExecutor) {
        // 离线入口复用同一提交逻辑，执行器生命周期由调用方管理。
        this.storageService = storageService;
        this.messageService = messageService;
        this.storageExecutor = storageExecutor;
        this.messageExecutor = messageExecutor;
        this.alarmRecorder = null;
        this.shutdownWaitSeconds = 0;
    }

    /** 两个任务均被接受后返回成功，不等待入库或发送完成。 */
    public boolean submit(CollectInterfaceRuntimeConfig config,
            TelemetryMessage message, long sequenceNumber) {
        // 第一步：复制接口上下文，避免排队期间接口配置更新改变任务和设备身份。
        CollectInterfaceRuntimeConfig snapshot = new CollectInterfaceRuntimeConfig();
        BeanUtils.copyProperties(config, snapshot);
        // 第二步：不可变遥测消息由两个任务共享，互相独立提交，即使一方拒绝也尝试另一方。
        boolean storageAccepted = execute(storageExecutor, snapshot, sequenceNumber,
                DataAlarmType.STORAGE_FAILURE, "遥测入库", () ->
                        storageService.saveProcessedParameters(snapshot, message));
        boolean messageAccepted = execute(messageExecutor, snapshot, sequenceNumber,
                DataAlarmType.MESSAGE_FAILURE, "遥测消息发送", () ->
                        messageService.send(message.toByteArray()));
        return storageAccepted && messageAccepted;
    }

    /** 将执行失败与提交失败分别记录，拒绝策略不会回退到处理线程。 */
    private boolean execute(Executor executor, CollectInterfaceRuntimeConfig config,
            long sequenceNumber, DataAlarmType type, String operation, ResultAction action) {
        try {
            // 工作线程仅执行本类结果任务，业务异常单独记录并保留原始堆栈。
            executor.execute(() -> {
                try {
                    action.run();
                } catch (Exception exception) {
                    failure(config, sequenceNumber, type, operation + "异步执行失败", exception);
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            // 队列已满或服务关闭时立即返回失败，不等待队列、不在调用线程执行。
            failure(config, sequenceNumber, type, operation + "任务提交失败，队列已满或服务正在关闭", exception);
            return false;
        }
    }

    /** 复用现有中文告警类别及日志上下文。 */
    private void failure(CollectInterfaceRuntimeConfig config, long sequenceNumber,
            DataAlarmType type, String detail, Exception exception) {
        // 先记录执行错误，告警记录异常也不能影响另一个结果任务提交。
        LOGGER.error("{}，接口编号：{}，包序号：{}", detail,
                config.getInterfaceId(), sequenceNumber, exception);
        if (alarmRecorder != null) {
            try {
                alarmRecorder.record(config, type, "包序号：" + sequenceNumber
                        + "；" + detail + "；原因：" + exception.getMessage());
            } catch (RuntimeException alarmException) {
                LOGGER.error("异步结果告警记录失败，接口编号：{}，包序号：{}",
                        config.getInterfaceId(), sequenceNumber, alarmException);
            }
        }
    }

    /** 创建固定线程数、有界队列和立即拒绝策略的结果执行器。 */
    private ThreadPoolExecutor createExecutor(int threads, int capacity, String prefix) {
        // 复用现有线程命名方式，不采用调用线程执行策略。
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(capacity),
                DataProcessingThreadPoolConfig.namedThreadFactory(prefix),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 在存储与消息服务销毁前等待结果任务，超过总等待上限后取消剩余任务。 */
    @PreDestroy
    public void shutdown() {
        // 第一步：离线执行器由调用方管理；运行环境先同时停止接受两类新任务。
        if (shutdownWaitSeconds == 0) {
            return;
        }
        ThreadPoolExecutor storagePool = (ThreadPoolExecutor) storageExecutor;
        ThreadPoolExecutor messagePool = (ThreadPoolExecutor) messageExecutor;
        storagePool.shutdown();
        messagePool.shutdown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(shutdownWaitSeconds);
        // 第二步：共享等待期限，超时或中断时记录被取消的排队任务数量。
        awaitShutdown(storagePool, deadline, "遥测入库");
        awaitShutdown(messagePool, deadline, "遥测消息发送");
    }

    /** 等待一个执行器结束，并保留当前线程的中断状态。 */
    private void awaitShutdown(ThreadPoolExecutor executor, long deadline, String operation) {
        try {
            // 使用单调时钟计算剩余等待时间，避免系统时间调整导致无限等待。
            if (executor.awaitTermination(Math.max(0L, deadline - System.nanoTime()),
                    TimeUnit.NANOSECONDS)) {
                return;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("{}线程池关闭等待被中断", operation);
        }
        // 到达等待上限后中断执行中的任务；外部客户端是否立即退出取决于其超时设置。
        LOGGER.warn("{}线程池关闭等待结束，取消排队任务数量：{}",
                operation, executor.shutdownNow().size());
    }

    /** 支持存储接口声明的受检异常。 */
    @FunctionalInterface
    private interface ResultAction {
        void run() throws Exception;
    }
}
