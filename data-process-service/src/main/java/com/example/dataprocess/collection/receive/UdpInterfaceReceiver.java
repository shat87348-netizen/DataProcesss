package com.example.dataprocess.collection.receive;

import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.processing.handler.ProtocolHandler;
import com.example.dataprocess.processing.handler.ProtocolHandlerRegistry;
import com.example.dataprocess.tool.UdpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** 单个采集接口的UDP接收任务，只负责传输层收包。 */
public final class UdpInterfaceReceiver implements CollectInterfaceReceiver {

    /** UDP接收日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            UdpInterfaceReceiver.class);
    /** 接口配置快照。 */
    private final CollectInterfaceRuntimeConfig config;
    /** 已绑定单播监听或加入组播的UDP端点。 */
    private final UdpTool.DatagramEndpoint endpoint;
    /** 当前接口对应的协议会话。 */
    private final ProtocolHandler.UdpSession protocolSession;
    /** 接收异常复用异步告警组件，由接口管理器在提交前注入。 */
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;
    public void setAlarmRecorder(com.example.dataprocess.alarm.DataAlarmRecorder recorder) { this.alarmRecorder = recorder; }
    private void recordFailure(String detail) {
        if (alarmRecorder != null) {
            alarmRecorder.record(config, com.example.common.enums.DataAlarmType.INTERFACE_STOP, detail);
        }
    }
    /** 是否继续接收数据。 */
    private volatile boolean running = true;

    public UdpInterfaceReceiver(
            CollectInterfaceRuntimeConfig config,
            UdpTool.DatagramEndpoint endpoint,
            ProtocolHandler.UdpSession protocolSession) {
        this.config = config;
        this.endpoint = endpoint;
        this.protocolSession = protocolSession;
    }

    /** 绑定UDP端口并创建已经选定协议会话的接收任务。 */
    public static UdpInterfaceReceiver create(
            CollectInterfaceRuntimeConfig config,
            ProtocolHandlerRegistry protocolHandlerRegistry)
            throws IOException {
        // 填写组播地址时加入组播组，否则保持原有单播监听方式。
        UdpTool.DatagramEndpoint endpoint = hasText(config.getMulticastIp())
                ? UdpTool.openMulticastReceiver(
                        config.getMulticastIp(),
                        config.getPort(),
                        config.getHost(),
                        0)
                : UdpTool.openUnicast(
                        config.getHost(), config.getPort(), 0);
        try {
            ProtocolHandler handler = protocolHandlerRegistry.get(
                    config.getTransferProtocol());
            ProtocolHandler.UdpSession protocolSession =
                    handler.createUdpSession(config, endpoint);
            return new UdpInterfaceReceiver(config, endpoint, protocolSession);
        } catch (RuntimeException exception) {
            // 协议会话创建失败时释放已经绑定的UDP端口。
            endpoint.close();
            throw exception;
        }
    }

    /** 持续接收UDP报文，并交给启动时选定的协议会话。 */
    @Override
    public void run() {
        // Receiver不判断PDXP、FEP等协议，只处理UDP传输层收包。
        try {
            while (running && endpoint.isOpen()) {
                UdpTool.ReceivedPacket packet = endpoint.receivePacket();
                try {
                    protocolSession.handle(packet);
                } catch (RuntimeException exception) {
                    // 单个协议包异常只提示，不结束整个UDP采集接口。
                    if (alarmRecorder != null) {
                        alarmRecorder.record(config, com.example.common.enums.DataAlarmType.COLLECTION_RECEIVE,
                                "当前UDP报文提交失败，接口继续接收：" + exception.getMessage());
                    }
                    LOGGER.warn("UDP报文提交协议处理失败，接口编号：{}，原因：{}",
                            config.getInterfaceId(), exception.getMessage());
                }
            }
        } catch (IOException exception) {
            if (running) {
                recordFailure("UDP接收失败，接口已停止接收：" + exception.getMessage());
                LOGGER.error("UDP采集接口接收失败，接口编号：{}",
                        config.getInterfaceId(), exception);
            }
        }
    }

    /** 停止接收并关闭UDP端点以解除阻塞。 */
    @Override
    public void stop() {
        running = false;
        try {
            // 关闭套接字会解除当前采集线程的阻塞接收。
            endpoint.close();
        } catch (IOException exception) {
            LOGGER.warn("UDP采集接口关闭失败，接口编号：{}，原因：{}",
                    config.getInterfaceId(), exception.getMessage());
        }
    }

    /** 判断可选字符串是否包含有效内容。 */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
