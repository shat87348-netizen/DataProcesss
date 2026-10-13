package com.example.dataprocess.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 数据采集和数据处理线程池配置。 */
@Configuration
public class DataProcessingThreadPoolConfig {

    /** 创建固定线程数且不缓存长期接收任务的UDP采集线程池。 */
    @Bean(name = "dataCollectExecutor", destroyMethod = "shutdownNow")
    public ThreadPoolExecutor dataCollectExecutor(
            @Value("${data-processing.collect-thread-count}")
            int threadCount) {
        // 核心线程数和最大线程数使用同一个明确配置值。
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<Runnable>(),
                namedThreadFactory("数据采集线程-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 创建固定线程数的数据处理线程池。 */
    @Bean(name = "dataProcessExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor dataProcessExecutor(
            @Value("${data-processing.process-thread-count}")
            int threadCount,
            @Value("${data-processing.process-queue-capacity}")
            int queueCapacity) {
        // 处理线程数完全使用配置值，不再根据CPU或接口数量动态计算。
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(queueCapacity),
                namedThreadFactory("数据处理线程-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 创建固定线程数的FEP文件结果处理线程池。 */
    @Bean(name = "fepResultExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor fepResultExecutor(FepProperties properties) {
        // MinIO上传和消息发送使用独立线程池，不阻塞采集与协议处理线程。
        return new ThreadPoolExecutor(
                properties.getResultThreadCount(),
                properties.getResultThreadCount(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(
                        properties.getResultQueueCapacity()),
                namedThreadFactory("文件结果线程-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 创建带中文名称和自增编号的守护线程工厂。 */
    public static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger(1);
        return task -> {
            // 所有线程都由统一线程池创建，不为接口手工维护Thread对象。
            Thread thread = new Thread(task, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
