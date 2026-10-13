package com.example.dataprocess.controller;

import com.example.common.response.ApiResponse;
import com.example.dataprocess.collection.receive.CollectInterfaceReceiver;
import com.example.dataprocess.collection.receive.TcpInterfaceReceiver;
import com.example.dataprocess.collection.receive.UdpInterfaceReceiver;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.processing.handler.ProtocolHandlerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/** FEP协议本地联调采集接口。 */
@RestController
@RequestMapping("/internal/collection/test/fep")
public class FepTestInterfaceController {

    /** UDP传输方式的枚举值。 */
    private static final int TRANSFER_TYPE_UDP = 1;
    /** TCP传输方式的枚举值。 */
    private static final int TRANSFER_TYPE_TCP = 2;
    /** FEP传输协议的枚举值。 */
    private static final int TRANSFER_PROTOCOL_FEP = 3;
    /** FEP测试采集接口监听的本机地址。 */
    private static final String TEST_INTERFACE_HOST = "127.0.0.1";
    /** FEP UDP测试接口监听端口。 */
    private static final int UDP_TEST_INTERFACE_PORT = 19_002;
    /** FEP TCP测试接口监听端口。 */
    private static final int TCP_TEST_INTERFACE_PORT = 19_003;

    /** 网络数据采集线程池。 */
    private final ThreadPoolExecutor collectExecutor;
    /** 协议处理器注册表。 */
    private final ProtocolHandlerRegistry protocolHandlerRegistry;
    /** 当前FEP测试采集接收任务。 */
    private CollectInterfaceReceiver testReceiver;
    /** 当前FEP测试采集线程任务句柄。 */
    private Future<?> testFuture;

    public FepTestInterfaceController(
            @Qualifier("dataCollectExecutor")
            ThreadPoolExecutor collectExecutor,
            ProtocolHandlerRegistry protocolHandlerRegistry) {
        this.collectExecutor = collectExecutor;
        this.protocolHandlerRegistry = protocolHandlerRegistry;
    }

    /** 启动UDP传输的FEP测试采集接口。 */
    @PostMapping("/udp/start")
    public ApiResponse<Void> startUdp() {
        // 第一步：按UDP传输方式启动独立的FEP测试接口。
        start(TRANSFER_TYPE_UDP, UDP_TEST_INTERFACE_PORT);
        return ApiResponse.success();
    }

    /** 启动TCP传输的FEP测试采集接口。 */
    @PostMapping("/tcp/start")
    public ApiResponse<Void> startTcp() {
        // 第一步：按TCP传输方式启动独立的FEP测试接口。
        start(TRANSFER_TYPE_TCP, TCP_TEST_INTERFACE_PORT);
        return ApiResponse.success();
    }

    /** 停止当前FEP测试采集接口。 */
    @PostMapping("/stop")
    public synchronized ApiResponse<Void> stop() {
        // 第一步：停止当前接收器并取消对应采集线程池任务。
        stopResources();
        return ApiResponse.success();
    }

    /** 根据指定传输方式创建并启动FEP测试接收任务。 */
    private synchronized void start(int transferType, int port) {
        // 第一步：重复启动或切换传输方式前释放原有测试接收任务。
        stopResources();

        // 第二步：构造当前传输方式对应的FEP测试运行配置。
        CollectInterfaceRuntimeConfig config = buildRuntimeConfig(
                transferType, port);
        try {
            // 第三步：这里只区分网络传输方式，FEP协议由接收器内部注册表选择。
            switch (transferType) {
                case TRANSFER_TYPE_UDP:
                    testReceiver = UdpInterfaceReceiver.create(
                            config, protocolHandlerRegistry);
                    break;
                case TRANSFER_TYPE_TCP:
                    testReceiver = TcpInterfaceReceiver.create(
                            config, protocolHandlerRegistry);
                    break;
                default:
                    throw new IllegalArgumentException("FEP测试不支持该传输方式");
            }

            // 第四步：把已创建的接收任务提交到统一采集线程池。
            testFuture = collectExecutor.submit(testReceiver);
        } catch (Exception exception) {
            // 第五步：启动失败时释放可能已经绑定的网络端口。
            stopResources();
            throw new IllegalStateException("FEP测试采集接口启动失败", exception);
        }
    }

    /** 构造FEP测试接口运行配置。 */
    private CollectInterfaceRuntimeConfig buildRuntimeConfig(
            int transferType,
            int port) {
        // 第一步：使用不同主键区分UDP和TCP测试接口。
        CollectInterfaceRuntimeConfig config = new CollectInterfaceRuntimeConfig();
        config.setInterfaceId(
                transferType == TRANSFER_TYPE_UDP ? 999_998L : 999_997L);
        config.setInterfaceName(
                transferType == TRANSFER_TYPE_UDP
                        ? "本地FEP UDP测试采集接口"
                        : "本地FEP TCP测试采集接口");

        // 第二步：填写本机监听信息和明确的FEP传输协议。
        config.setTaskId("TEST");
        config.setHost(TEST_INTERFACE_HOST);
        config.setPort(port);
        config.setTransferType(transferType);
        config.setTransferProtocol(TRANSFER_PROTOCOL_FEP);

        // 第三步：FEP文件接收不使用RPC和协议配置表。
        config.setRpcEnabled(0);
        config.setProtocolConfigId(null);
        config.setProtocolConfigParams(null);
        return config;
    }

    /** 释放当前FEP测试接收资源。 */
    private void stopResources() {
        // 第一步：先关闭当前UDP端点或TCP监听服务，解除阻塞接收。
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
