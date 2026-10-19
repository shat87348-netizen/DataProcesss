package com.example.dataprocess.collection.receive;

import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.processing.handler.ProtocolHandler;
import com.example.dataprocess.processing.handler.ProtocolHandlerRegistry;
import com.example.dataprocess.tool.TcpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** 单个采集接口的TCP接收任务，只负责连接管理。 */
public final class TcpInterfaceReceiver implements CollectInterfaceReceiver {

    /** TCP接收日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            TcpInterfaceReceiver.class);
    /** 接口配置快照。 */
    private final CollectInterfaceRuntimeConfig config;
    /** 已绑定本机地址和端口的TCP服务端。 */
    private final TcpTool.TcpServer server;
    /** 当前接口对应的协议会话。 */
    private final ProtocolHandler.TcpSession protocolSession;
    /** 接收异常复用异步告警组件，由接口管理器在提交前注入。 */
    private com.example.dataprocess.alarm.DataAlarmRecorder alarmRecorder;
    public void setAlarmRecorder(com.example.dataprocess.alarm.DataAlarmRecorder recorder) { this.alarmRecorder = recorder; }
    private void recordFailure(String detail) {
        if (alarmRecorder != null) {
            alarmRecorder.record(config, com.example.common.enums.DataAlarmType.COLLECTION_RECEIVE, detail);
        }
    }
    /** 是否继续接收连接和数据。 */
    private volatile boolean running = true;
    /** 当前正在接收的客户端连接。 */
    private volatile TcpTool.TcpConnection currentConnection;

    public TcpInterfaceReceiver(
            CollectInterfaceRuntimeConfig config,
            TcpTool.TcpServer server,
            ProtocolHandler.TcpSession protocolSession) {
        this.config = config;
        this.server = server;
        this.protocolSession = protocolSession;
    }

    /** 绑定TCP端口并创建已经选定协议会话的接收任务。 */
    public static TcpInterfaceReceiver create(
            CollectInterfaceRuntimeConfig config,
            ProtocolHandlerRegistry protocolHandlerRegistry)
            throws IOException {
        TcpTool.TcpServer server = TcpTool.listen(
                config.getHost(), config.getPort(), 0, 0);
        try {
            ProtocolHandler handler = protocolHandlerRegistry.get(
                    config.getTransferProtocol());
            ProtocolHandler.TcpSession protocolSession =
                    handler.createTcpSession(config);
            return new TcpInterfaceReceiver(config, server, protocolSession);
        } catch (RuntimeException exception) {
            // 协议会话创建失败时释放已经绑定的TCP端口。
            try {
                server.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    /** 持续接受TCP连接，并交给启动时选定的协议会话拆包。 */
    @Override
    public void run() {
        // Receiver不判断PDXP、FEP等协议，只负责TCP连接生命周期。
        while (running && server.isOpen()) {
            try (TcpTool.TcpConnection connection = server.accept()) {
                currentConnection = connection;
                LOGGER.info("TCP采集接口已接入发送方连接，接口编号：{}",
                        config.getInterfaceId());
                protocolSession.receive(connection, () -> running);
            } catch (IOException exception) {
                if (running) {
                    recordFailure("TCP发送方连接异常结束，监听继续等待连接：" + exception.getMessage());
                    LOGGER.warn("TCP采集连接已结束，接口编号：{}，原因：{}",
                            config.getInterfaceId(), exception.getMessage());
                }
            } catch (RuntimeException exception) {
                if (running) {
                    // 当前连接的协议内容异常时关闭连接，监听服务继续等待下一连接。
                    recordFailure("TCP协议流失败，当前连接关闭：" + exception.getMessage());
                    LOGGER.warn("TCP协议流处理失败，接口编号：{}，原因：{}",
                            config.getInterfaceId(), exception.getMessage());
                }
            } finally {
                currentConnection = null;
            }
        }
    }

    /** 停止接收并关闭当前连接和监听端口。 */
    @Override
    public void stop() {
        running = false;
        closeCurrentConnection();
        closeServer();
    }

    /** 安全关闭当前发送方连接。 */
    private void closeCurrentConnection() {
        TcpTool.TcpConnection connection = currentConnection;
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (IOException exception) {
            LOGGER.warn("TCP采集接口关闭发送方连接失败，接口编号：{}",
                    config.getInterfaceId(), exception);
        }
    }

    /** 安全关闭TCP监听服务端。 */
    private void closeServer() {
        try {
            server.close();
        } catch (IOException exception) {
            LOGGER.warn("TCP采集接口关闭监听端口失败，接口编号：{}",
                    config.getInterfaceId(), exception);
        }
    }
}
