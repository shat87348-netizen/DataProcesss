package com.example.dataprocess.controller;

import com.example.common.response.ApiResponse;
import com.example.dataprocess.collection.receive.UdpInterfaceReceiver;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.processing.handler.ProtocolHandlerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/** PDXP协议本地联调采集接口。 */
@RestController
@RequestMapping("/internal/collection/test/pdxp")
public class PdxpTestInterfaceController {

    /** PDXP测试采集接口使用的固定内存主键。 */
    private static final Long TEST_INTERFACE_ID = 999_999L;
    /** PDXP测试采集接口监听的本机地址。 */
    private static final String TEST_INTERFACE_HOST = "127.0.0.1";
    /** PDXP测试采集接口监听的UDP端口。 */
    private static final Integer TEST_INTERFACE_PORT = 9_001;

    /** 网络数据采集线程池。 */
    private final ThreadPoolExecutor collectExecutor;
    /** 协议处理器注册表。 */
    private final ProtocolHandlerRegistry protocolHandlerRegistry;
    /** 当前PDXP测试采集接收任务。 */
    private UdpInterfaceReceiver testReceiver;
    /** 当前PDXP测试采集线程任务句柄。 */
    private Future<?> testFuture;

    public PdxpTestInterfaceController(
            @Qualifier("dataCollectExecutor")
            ThreadPoolExecutor collectExecutor,
            ProtocolHandlerRegistry protocolHandlerRegistry) {
        this.collectExecutor = collectExecutor;
        this.protocolHandlerRegistry = protocolHandlerRegistry;
    }

    /** 启动UDP传输的PDXP测试采集接口。 */
    @PostMapping("/start")
    public synchronized ApiResponse<Void> start() {
        // 第一步：重复启动前释放原有PDXP测试接收任务。
        stopResources();

        // 第二步：构造只用于本地联调的UDP和PDXP运行配置。
        CollectInterfaceRuntimeConfig config = buildRuntimeConfig();
        try {
            // 第三步：创建PDXP协议的UDP接收任务并提交采集线程池。
            testReceiver = UdpInterfaceReceiver.create(
                    config, protocolHandlerRegistry);
            testFuture = collectExecutor.submit(testReceiver);
            return ApiResponse.success();
        } catch (Exception exception) {
            // 第四步：启动失败时释放已经创建的端口和任务。
            stopResources();
            throw new IllegalStateException("PDXP测试采集接口启动失败", exception);
        }
    }

    /** 停止PDXP测试采集接口。 */
    @PostMapping("/stop")
    public synchronized ApiResponse<Void> stop() {
        // 第一步：停止接收并取消采集线程池任务。
        stopResources();
        return ApiResponse.success();
    }

    /** 构造PDXP测试接口运行配置。 */
    private CollectInterfaceRuntimeConfig buildRuntimeConfig() {
        // 第一步：填写测试接口身份和UDP监听信息。
        CollectInterfaceRuntimeConfig config = new CollectInterfaceRuntimeConfig();
        config.setInterfaceId(TEST_INTERFACE_ID);
        config.setInterfaceName("本地PDXP测试采集接口");
        config.setTaskId("728101");
        config.setHost(TEST_INTERFACE_HOST);
        config.setPort(TEST_INTERFACE_PORT);
        config.setTransferType(1);
        config.setTransferProtocol(2);

        // 第二步：开启RPC分支并填写FrameRequest所需的联调固定字段。
        config.setRpcEnabled(1);
        config.setProtocolConfigId(1L);
        config.setProtocolConfigParams(
                "{\"fields\":["
                        + "{\"field\":\"sat_code\","
                        + "\"fieldName\":\"卫星代号\","
                        + "\"value\":\"74\"},"
                        + "{\"field\":\"channel\","
                        + "\"fieldName\":\"通道代号\","
                        + "\"value\":\"12\"},"
                        + "{\"field\":\"data_source\","
                        + "\"fieldName\":\"数据源\","
                        + "\"value\":\"SIMULATOR\"}"
                        + "]}");
        return config;
    }

    /** 释放当前PDXP测试接收资源。 */
    private void stopResources() {
        // 第一步：先关闭接收器，使阻塞的UDP接收操作立即退出。
        if (testReceiver != null) {
            testReceiver.stop();
            testReceiver = null;
        }

        // 第二步：取消对应的采集线程池任务并清空任务句柄。
        if (testFuture != null) {
            testFuture.cancel(true);
            testFuture = null;
        }
    }
}
