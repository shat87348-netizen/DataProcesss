package com.example.dataprocess.tool;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * PDXP 协议解析工具。
 *
 * <p>支持原始二进制、空白分隔的十六进制文本，以及带定界和转义的 TCP 数据。</p>
 */
public final class PdxpParser {

    /** PDXP 固定包头长度。 */
    public static final int HEADER_LENGTH = 32;
    /** PDXP包头之前的默认传输头长度，运行入口可按配置覆盖。 */
    public static final int TRANSPORT_HEADER_LENGTH = 2;

    /** TCP 数据包定界符。 */
    private static final int TCP_DELIMITER = 0x7E;

    /** TCP 数据包转义符。 */
    private static final int TCP_ESCAPE = 0x7D;

    /** UDP 单个报文允许接收的最大数据长度。 */
    private static final int MAX_UDP_DATAGRAM_LENGTH = 65_507;

    /** TCP 单帧在全部特殊字节转义后的最大长度。 */
    private static final int MAX_TCP_FRAME_LENGTH = (HEADER_LENGTH + 65_535) * 2 + 2;

    /** 协议日期的起算日期。 */
    private static final LocalDate DATE_EPOCH = LocalDate.of(2000, 1, 1);

    /** 工具类不允许实例化。 */
    private PdxpParser() {
    }

    /**
     * 自动识别文件格式并解析其中的全部 PDXP 数据包。
     *
     * @param filePath 文件路径
     * @return 解析后的数据包列表
     * @throws IOException 文件读取或协议解析失败
     */
    public static List<PdxpPacket> parse(Path filePath) throws IOException {
        // 一次读取文件内容，以便自动判断文件保存格式。
        byte[] fileData = Files.readAllBytes(filePath);

        // 自动识别文件内容，再转到对应的明确解析入口。
        InputFormat inputFormat = detectFormat(fileData);
        if (inputFormat == InputFormat.HEX_TEXT) {
            return parseBinary(decodeHexText(fileData));
        }
        if (inputFormat == InputFormat.TCP) {
            return parseTcpFrames(fileData);
        }
        return parseBinary(fileData);
    }

    /**
     * 【UDP 解析入口】解析 UDP 报文中的原始 PDXP 数据。
     *
     * <p>UDP 自带报文边界，不包含 7E 定界符，也不需要执行 7D 反转义。</p>
     *
     * @param udpData UDP 报文的数据部分
     * @return 解析后的数据包列表
     * @throws IOException 协议解析失败
     */
    public static List<PdxpPacket> parseUdp(byte[] udpData) throws IOException {
        // 校验 UDP 报文数据，避免产生含义不明确的空指针异常。
        if (udpData == null) {
            throw new IllegalArgumentException("UDP 数据不能为空");
        }

        // UDP 使用容错流程，跳过残缺或字段非法的数据并继续查找后续完整包。
        return parseUdpLenient(udpData);
    }

    /** 保留默认两字节传输头的调用入口。 */
    public static List<PdxpPacket> parseTransportUdp(byte[] udpData)
            throws IOException {
        return parseTransportUdp(udpData, TRANSPORT_HEADER_LENGTH);
    }

    /** 按配置长度拆分传输头，零长度直接从PDXP固定包头开始解析。 */
    public static List<PdxpPacket> parseTransportUdp(byte[] udpData,
            int transportHeaderLength) throws IOException {
        // 第一步：校验配置长度和报文实际长度，使用长整数避免相加溢出。
        if (transportHeaderLength < 0) {
            throw new IllegalArgumentException("传输头字节数不能为负数");
        }
        if (udpData == null
                || udpData.length < (long) transportHeaderLength + HEADER_LENGTH) {
            throw new IllegalArgumentException("PDXP报文长度不足，传输头配置字节数："
                    + transportHeaderLength + "，PDXP包头字节数：" + HEADER_LENGTH
                    + "，实际报文字节数：" + (udpData == null ? 0 : udpData.length));
        }

        // 第二步：按实际配置保留传输头；长度为零时得到空数组，不跳过任何字节。
        byte[] transportHeader = Arrays.copyOfRange(
                udpData, 0, transportHeaderLength);
        byte[] pdxpData = Arrays.copyOfRange(
                udpData, transportHeaderLength, udpData.length);
        List<PdxpPacket> parsedPackets = parseUdp(pdxpData);

        // 第三步：把传输头写入每个解析结果，保证该字段不会在解析过程中丢失。
        List<PdxpPacket> transportedPackets =
                new ArrayList<PdxpPacket>(parsedPackets.size());
        for (PdxpPacket parsedPacket : parsedPackets) {
            transportedPackets.add(parsedPacket.withTransportHeader(
                    transportHeader));
        }
        return Collections.unmodifiableList(transportedPackets);
    }

    /**
     * 【UDP 单播接收入口】监听本机端口并接收一个 UDP 单播报文。
     *
     * <p>未指定本机地址时监听所有本机网卡。</p>
     *
     * @param port 本机监听端口
     * @param timeoutMillis 接收超时时间，0 表示一直等待
     * @return 当前 UDP 报文中解析出的数据包列表
     * @throws IOException 绑定端口、接收数据或协议解析失败
     */
    public static List<PdxpPacket> receiveUdpUnicast(
            int port,
            int timeoutMillis) throws IOException {
        // 未指定本机地址时复用完整入口，并监听所有本机网卡。
        return receiveUdpUnicast(null, port, timeoutMillis);
    }

    /**
     * 【UDP 单播接收入口】监听指定本机地址和端口并接收一个 UDP 单播报文。
     *
     * @param localAddress 本机监听地址，例如 192.168.1.20；为空时监听所有网卡
     * @param port 本机监听端口
     * @param timeoutMillis 接收超时时间，0 表示一直等待
     * @return 当前 UDP 报文中解析出的数据包列表
     * @throws IOException 绑定地址、接收数据或协议解析失败
     */
    public static List<PdxpPacket> receiveUdpUnicast(
            String localAddress,
            int port,
            int timeoutMillis) throws IOException {
        // 先校验端口和超时时间，避免打开套接字后才发现配置错误。
        validatePortAndTimeout(port, timeoutMillis);

        // 空地址监听全部网卡，指定地址则只监听对应的本机网卡。
        InetSocketAddress bindAddress;
        if (localAddress == null || localAddress.trim().isEmpty()) {
            bindAddress = new InetSocketAddress(port);
        } else {
            InetAddress address = InetAddress.getByName(localAddress.trim());
            if (NetworkInterface.getByInetAddress(address) == null) {
                throw new IOException("UDP 单播监听地址不是本机地址：" + localAddress);
            }
            bindAddress = new InetSocketAddress(address, port);
        }

        // 绑定本机地址和端口，接收一个完整 UDP 报文后自动关闭套接字。
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(bindAddress);
            socket.setSoTimeout(timeoutMillis);

            // UDP 保留消息边界，一次接收即可取得当前完整报文。
            byte[] receiveBuffer = new byte[MAX_UDP_DATAGRAM_LENGTH];
            DatagramPacket datagramPacket = new DatagramPacket(
                    receiveBuffer,
                    receiveBuffer.length);
            socket.receive(datagramPacket);

            // 只复制实际收到的字节，再交给统一 UDP 解析入口。
            byte[] udpData = Arrays.copyOfRange(
                    datagramPacket.getData(),
                    datagramPacket.getOffset(),
                    datagramPacket.getOffset() + datagramPacket.getLength());
            return parseUdp(udpData);
        }
    }

    /**
     * 【UDP 组播接收入口】加入指定组播并接收一个 UDP 报文。
     *
     * <p>未指定本机网卡地址时，由操作系统选择用于接收组播的网卡。</p>
     *
     * @param multicastAddress 组播地址，例如 239.1.1.1
     * @param port 监听端口
     * @param timeoutMillis 接收超时时间，0 表示一直等待
     * @return 当前 UDP 报文中解析出的数据包列表
     * @throws IOException 加入组播、接收数据或协议解析失败
     */
    public static List<PdxpPacket> receiveUdpMulticast(
            String multicastAddress,
            int port,
            int timeoutMillis) throws IOException {
        // 未指定网卡时复用完整入口，并交由操作系统选择默认网卡。
        return receiveUdpMulticast(multicastAddress, port, null, timeoutMillis);
    }

    /**
     * 【UDP 组播接收入口】通过指定本机网卡加入组播并接收一个 UDP 报文。
     *
     * @param multicastAddress 组播地址，例如 239.1.1.1
     * @param port 监听端口
     * @param localAddress 本机网卡地址，例如 192.168.1.20；为空时使用默认网卡
     * @param timeoutMillis 接收超时时间，0 表示一直等待
     * @return 当前 UDP 报文中解析出的数据包列表
     * @throws IOException 加入组播、接收数据或协议解析失败
     */
    public static List<PdxpPacket> receiveUdpMulticast(
            String multicastAddress,
            int port,
            String localAddress,
            int timeoutMillis) throws IOException {
        // 先校验网络参数，避免在打开套接字后才发现配置错误。
        validateNetworkArguments(multicastAddress, port, timeoutMillis, "组播地址");

        // 解析并确认目标地址确实属于组播地址范围。
        InetAddress groupAddress = InetAddress.getByName(multicastAddress.trim());
        if (!groupAddress.isMulticastAddress()) {
            throw new IllegalArgumentException("UDP 组播地址不合法：" + multicastAddress);
        }

        // 配置端口复用，允许同一台机器上的多个接收端加入同一个组播。
        try (MulticastSocket socket = new MulticastSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            socket.setSoTimeout(timeoutMillis);

            // 根据配置选择默认网卡或指定网卡加入组播。
            NetworkInterface networkInterface = resolveNetworkInterface(localAddress);
            joinMulticastGroup(socket, groupAddress, port, networkInterface);
            try {
                // UDP 保留报文边界，因此一次接收只读取一个完整报文。
                byte[] receiveBuffer = new byte[MAX_UDP_DATAGRAM_LENGTH];
                DatagramPacket datagramPacket = new DatagramPacket(
                        receiveBuffer,
                        receiveBuffer.length);
                socket.receive(datagramPacket);

                // 只复制本次实际收到的字节，避免把缓冲区空闲部分交给解析器。
                byte[] udpData = Arrays.copyOfRange(
                        datagramPacket.getData(),
                        datagramPacket.getOffset(),
                        datagramPacket.getOffset() + datagramPacket.getLength());
                return parseUdp(udpData);
            } finally {
                // 接收完成或发生异常时都主动退出组播，及时释放网卡成员关系。
                leaveMulticastGroup(socket, groupAddress, port, networkInterface);
            }
        }
    }

    /** 容错解析 UDP 数据，跳过异常片段并保留其中的完整数据包。 */
    private static List<PdxpPacket> parseUdpLenient(byte[] udpData) {
        // 按解析成功的先后顺序保存有效数据包。
        List<PdxpPacket> packets = new ArrayList<PdxpPacket>();
        int offset = 0;

        // 剩余数据不足固定包头时无法组成新包，直接忽略末尾残片。
        while (offset + HEADER_LENGTH <= udpData.length) {
            // 当前字节必须符合协议版本和预留字段规则，否则向后移动一个字节。
            if (!isPossibleHeader(udpData, offset)) {
                offset++;
                continue;
            }

            // 从候选包头读取数据域长度，并计算整个数据包占用的字节数。
            int dataLength = readUnsignedShort(udpData, offset + 30);
            int packetLength = HEADER_LENGTH + dataLength;

            // 数据域不足表示当前候选包残缺，继续向后查找可能存在的完整包。
            if (offset + packetLength > udpData.length) {
                offset++;
                continue;
            }

            // 分离固定包头和数据域，准备执行完整字段解析。
            byte[] header = Arrays.copyOfRange(udpData, offset, offset + HEADER_LENGTH);
            byte[] data = Arrays.copyOfRange(
                    udpData,
                    offset + HEADER_LENGTH,
                    offset + packetLength);

            try {
                // 只有通过全部协议字段校验的数据包才加入返回结果。
                packets.add(parsePacket(header, data));
                offset += packetLength;
            } catch (IOException exception) {
                // 当前候选包无效时跳过一个字节，尝试恢复后续合法数据包。
                offset++;
            }
        }

        // 返回只读列表，防止调用方意外修改解析结果集合。
        return Collections.unmodifiableList(packets);
    }

    /** 快速判断指定位置是否可能是一个 PDXP 固定包头。 */
    private static boolean isPossibleHeader(byte[] sourceData, int offset) {
        // 版本字段最高两位必须为协议规定的二进制 10。
        int version = (sourceData[offset] >>> 6) & 0x03;
        if (version != 2) {
            return false;
        }

        // 包头中的四字节预留字段必须全部为零。
        for (int index = offset + 20; index <= offset + 23; index++) {
            if (sourceData[index] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 【TCP 解析入口】解析包含 7E 定界符和 7D 转义的 TCP 字节流。
     *
     * @param tcpData TCP 接收到的字节流
     * @return 解析后的数据包列表
     * @throws IOException 定界、转义或协议解析失败
     */
    public static List<PdxpPacket> parseTcp(byte[] tcpData) throws IOException {
        // 校验 TCP 字节流，避免产生含义不明确的空指针异常。
        if (tcpData == null) {
            throw new IllegalArgumentException("TCP 数据不能为空");
        }

        // TCP 数据先按定界符拆帧并反转义，再解析内部 PDXP 数据。
        return parseTcpFrames(tcpData);
    }

    /**
     * 【TCP 接收入口】以客户端方式连接远端服务并接收一个完整 PDXP 定界帧。
     *
     * @param host 远端主机地址
     * @param port 远端端口
     * @param connectTimeoutMillis 连接超时时间，0 表示使用系统默认值
     * @param readTimeoutMillis 接收超时时间，0 表示一直等待
     * @return 当前 TCP 定界帧解析出的数据包列表
     * @throws IOException 建立连接、接收数据或协议解析失败
     */
    public static List<PdxpPacket> receiveTcp(
            String host,
            int port,
            int connectTimeoutMillis,
            int readTimeoutMillis) throws IOException {
        // 分别校验连接和读取超时时间，确保套接字参数有效。
        validateNetworkArguments(host, port, connectTimeoutMillis, "远端主机地址");
        if (readTimeoutMillis < 0) {
            throw new IllegalArgumentException("TCP 接收超时时间不能小于0");
        }

        // 建立短连接，并在读取完一个完整定界帧后自动关闭连接。
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            return receiveTcp(socket.getInputStream());
        }
    }

    /**
     * 【TCP 流接收入口】从已有连接中读取一个完整 PDXP 定界帧。
     *
     * <p>该方法不会关闭输入流，连续接收时可以对同一输入流重复调用。</p>
     *
     * @param inputStream TCP 连接输入流
     * @return 当前 TCP 定界帧解析出的数据包列表
     * @throws IOException 接收数据或协议解析失败
     */
    public static List<PdxpPacket> receiveTcp(InputStream inputStream) throws IOException {
        // 校验输入流，避免将连接配置错误表现为空指针异常。
        if (inputStream == null) {
            throw new IllegalArgumentException("TCP 输入流不能为空");
        }

        // TCP 没有消息边界，必须读取到首尾两个 7E 后才交给解析器。
        return parseTcp(readTcpFrame(inputStream));
    }

    /**
     * 【十六进制样例解析入口】解析空白分隔的十六进制 PDXP 文本。
     *
     * @param hexText 十六进制文本
     * @return 解析后的数据包列表
     * @throws IOException 文本或协议解析失败
     */
    public static List<PdxpPacket> parseHexText(String hexText) throws IOException {
        // 将文本转换为字符字节，再复用十六进制文本解析流程。
        if (hexText == null) {
            throw new IllegalArgumentException("十六进制文本不能为空");
        }
        return parseBinary(decodeHexText(hexText.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * 从输入流连续解析原始二进制 PDXP 数据包。
     *
     * @param inputStream 输入流
     * @return 解析后的数据包列表
     * @throws IOException 协议解析失败
     */
    public static List<PdxpPacket> parseBinary(InputStream inputStream) throws IOException {
        // 校验输入流，避免产生含义不明确的空指针异常。
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }

        // 按数据包顺序保存解析结果。
        List<PdxpPacket> packets = new ArrayList<PdxpPacket>();
        int packetIndex = 1;

        // 持续读取包头，直到输入流正常结束。
        while (true) {
            byte[] header = readHeader(inputStream, packetIndex);
            if (header == null) {
                break;
            }

            // 数据域长度位于包头最后两个字节，采用小端字节序。
            int dataLength = readUnsignedShort(header, 30);

            // 严格读取数据域，防止把截断文件当成有效数据。
            byte[] data = new byte[dataLength];
            readFully(inputStream, data, packetIndex, "数据域");

            // 将固定包头字段与数据域组装为不可变结果对象。
            packets.add(parsePacket(header, data));
            packetIndex++;
        }

        // 返回只读列表，防止调用方意外修改解析结果集合。
        return Collections.unmodifiableList(packets);
    }

    /** 按连续二进制格式解析字节数组。 */
    public static List<PdxpPacket> parseBinary(byte[] binaryData) throws IOException {
        // 校验原始 PDXP 数据，避免空值进入流式解析流程。
        if (binaryData == null) {
            throw new IllegalArgumentException("PDXP 原始数据不能为空");
        }

        // 使用内存输入流复用流式解析逻辑。
        return parseBinary(new java.io.ByteArrayInputStream(binaryData));
    }

    /** 校验网络地址、端口和超时时间。 */
    private static void validateNetworkArguments(
            String address,
            int port,
            int timeoutMillis,
            String addressName) {
        // 地址不能为空或只包含空白字符。
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException(addressName + "不能为空");
        }

        // 复用统一校验，保证三种网络接收方式的规则一致。
        validatePortAndTimeout(port, timeoutMillis);
    }

    /** 校验网络端口和接收超时时间。 */
    private static void validatePortAndTimeout(int port, int timeoutMillis) {
        // 网络端口必须处于有效范围内。
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("端口号必须在1到65535之间");
        }

        // 套接字使用0表示不设置超时，负数没有合法含义。
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("超时时间不能小于0");
        }
    }

    /** 根据本机地址查找用于接收组播的网卡。 */
    private static NetworkInterface resolveNetworkInterface(String localAddress) throws IOException {
        // 空地址表示使用操作系统选择的默认组播网卡。
        if (localAddress == null || localAddress.trim().isEmpty()) {
            return null;
        }

        // 指定地址必须属于本机已经启用的某个网络接口。
        InetAddress interfaceAddress = InetAddress.getByName(localAddress.trim());
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(interfaceAddress);
        if (networkInterface == null) {
            throw new IOException("未找到本机网卡地址：" + localAddress);
        }
        return networkInterface;
    }

    /** 使用默认网卡或指定网卡加入组播。 */
    private static void joinMulticastGroup(
            MulticastSocket socket,
            InetAddress groupAddress,
            int port,
            NetworkInterface networkInterface) throws IOException {
        // 未指定网卡时使用兼容方式，让操作系统决定实际接收网卡。
        if (networkInterface == null) {
            socket.joinGroup(groupAddress);
            return;
        }

        // 指定网卡时明确绑定组播成员关系，适用于机器存在多块网卡的情况。
        socket.joinGroup(new InetSocketAddress(groupAddress, port), networkInterface);
    }

    /** 使用与加入组播一致的方式退出组播。 */
    private static void leaveMulticastGroup(
            MulticastSocket socket,
            InetAddress groupAddress,
            int port,
            NetworkInterface networkInterface) throws IOException {
        // 默认网卡加入的组播使用对应的兼容方式退出。
        if (networkInterface == null) {
            socket.leaveGroup(groupAddress);
            return;
        }

        // 指定网卡加入的组播必须从同一网卡退出。
        socket.leaveGroup(new InetSocketAddress(groupAddress, port), networkInterface);
    }

    /** 从连续 TCP 字节流读取一个包含首尾定界符的完整帧。 */
    private static byte[] readTcpFrame(InputStream inputStream) throws IOException {
        // 动态缓冲区保存首尾定界符以及中间的转义数据。
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        boolean frameStarted = false;

        // 持续读取，直到遇到第二个定界符或连接提前关闭。
        while (true) {
            int current = inputStream.read();
            if (current == -1) {
                if (frameStarted) {
                    throw new EOFException("TCP 连接已关闭，当前 PDXP 帧缺少结束定界符");
                }
                throw new EOFException("TCP 连接已关闭，未收到 PDXP 定界帧");
            }

            // 起始定界符之前的数据不属于完整 PDXP 帧，直接跳过。
            if (!frameStarted) {
                if (current == TCP_DELIMITER) {
                    frameStarted = true;
                    frame.write(current);
                }
                continue;
            }

            // 保存帧内字节，结束定界符也要保留给原有 TCP 解析流程。
            frame.write(current);
            if (current == TCP_DELIMITER && frame.size() > 2) {
                return frame.toByteArray();
            }

            // 限制异常连接持续发送无边界数据造成的内存占用。
            if (frame.size() > MAX_TCP_FRAME_LENGTH) {
                throw new IOException("TCP PDXP 帧长度超过协议允许的最大值");
            }
        }
    }

    /** 解析带定界符和转义的 TCP 数据。 */
    private static List<PdxpPacket> parseTcpFrames(byte[] tcpData) throws IOException {
        // 保存从 TCP 字节流恢复出的所有应用层数据包。
        List<PdxpPacket> packets = new ArrayList<PdxpPacket>();
        ByteArrayOutputStream frame = null;

        // 逐字节搜索首尾定界符，并收集两个定界符之间的数据。
        for (int index = 0; index < tcpData.length; index++) {
            int current = tcpData[index] & 0xFF;
            if (current == TCP_DELIMITER) {
                // 遇到结束定界符时，解析已经收集到的完整帧。
                if (frame != null && frame.size() > 0) {
                    List<PdxpPacket> framePackets = parseBinary(unescapeTcp(frame.toByteArray()));
                    if (framePackets.size() != 1) {
                        throw new IOException("一个 TCP 定界帧必须且只能包含一个 PDXP 数据包");
                    }
                    packets.add(framePackets.get(0));
                }

                // 当前定界符同时作为下一帧的起始位置。
                frame = new ByteArrayOutputStream();
            } else if (frame != null) {
                // 只接收起始定界符之后的帧内容。
                frame.write(current);
            }
        }

        // 非空的末尾缓存表示最后一个 TCP 帧缺少结束定界符。
        if (frame != null && frame.size() > 0) {
            throw new EOFException("TCP 数据末尾缺少 7E 定界符");
        }
        if (packets.isEmpty()) {
            throw new IOException("未找到完整的 TCP PDXP 数据包");
        }

        // 返回只读列表，保持解析结果稳定。
        return Collections.unmodifiableList(packets);
    }

    /** 将 TCP 帧中的转义序列恢复为原始字节。 */
    private static byte[] unescapeTcp(byte[] escapedData) throws IOException {
        // 使用动态缓冲区保存反转义后的数据。
        ByteArrayOutputStream output = new ByteArrayOutputStream(escapedData.length);

        // 逐字节识别 7D5E 和 7D5D 两种合法转义序列。
        for (int index = 0; index < escapedData.length; index++) {
            int current = escapedData[index] & 0xFF;
            if (current != TCP_ESCAPE) {
                output.write(current);
                continue;
            }

            // 转义符后必须还有一个用于描述原始值的字节。
            if (++index >= escapedData.length) {
                throw new EOFException("TCP 帧末尾存在不完整的 7D 转义序列");
            }

            // 只接受协议明确规定的两种转义值。
            int escaped = escapedData[index] & 0xFF;
            if (escaped == 0x5E) {
                output.write(TCP_DELIMITER);
            } else if (escaped == 0x5D) {
                output.write(TCP_ESCAPE);
            } else {
                throw new IOException(String.format("TCP 帧包含非法转义序列：7D%02X", escaped));
            }
        }

        // 输出已经恢复的应用层字节。
        return output.toByteArray();
    }

    /** 自动判断输入数据的保存格式。 */
    private static InputFormat detectFormat(byte[] sourceData) throws IOException {
        // 空文件没有足够信息用于格式判断。
        if (sourceData.length == 0) {
            throw new EOFException("PDXP 数据为空");
        }

        // 能完整解码为十六进制令牌时，将其视为文本样例。
        if (looksLikeHexText(sourceData)) {
            return InputFormat.HEX_TEXT;
        }

        // 第一个非空字节为 7E 时，按 TCP 定界格式处理。
        if ((sourceData[0] & 0xFF) == TCP_DELIMITER) {
            return InputFormat.TCP;
        }

        // 其余内容默认视为原始连续二进制数据。
        return InputFormat.BINARY;
    }

    /** 判断数据是否为两个十六进制字符组成的文本令牌。 */
    private static boolean looksLikeHexText(byte[] sourceData) {
        // 按空白切分文本，避免依赖固定的换行或列数。
        String text = new String(sourceData, StandardCharsets.US_ASCII).trim();
        if (text.isEmpty()) {
            return false;
        }

        // 每个令牌必须恰好表示一个字节。
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (token.length() != 2 || !isHexDigit(token.charAt(0)) || !isHexDigit(token.charAt(1))) {
                return false;
            }
        }
        return true;
    }

    /** 判断字符是否为十六进制数字。 */
    private static boolean isHexDigit(char value) {
        // 同时接受数字、大写字母和小写字母。
        return value >= '0' && value <= '9'
                || value >= 'A' && value <= 'F'
                || value >= 'a' && value <= 'f';
    }

    /** 将空白分隔的十六进制文本转换为字节数组。 */
    private static byte[] decodeHexText(byte[] sourceData) throws IOException {
        // 使用与格式判断一致的令牌规则读取文本。
        String text = new String(sourceData, StandardCharsets.US_ASCII).trim();
        if (text.isEmpty()) {
            throw new EOFException("十六进制文本为空");
        }
        String[] tokens = text.split("\\s+");
        byte[] binaryData = new byte[tokens.length];

        // 将每个两位十六进制令牌还原为一个字节。
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.length() != 2 || !isHexDigit(token.charAt(0)) || !isHexDigit(token.charAt(1))) {
                throw new IOException("第 " + (index + 1) + " 个十六进制令牌无效：" + token);
            }
            binaryData[index] = (byte) Integer.parseInt(token, 16);
        }
        return binaryData;
    }

    /** 读取一个完整的固定包头。 */
    private static byte[] readHeader(InputStream inputStream, int packetIndex) throws IOException {
        // 先读取一个字节，用于区分正常文件结束和残缺包头。
        int firstByte = inputStream.read();
        if (firstByte == -1) {
            return null;
        }

        // 放回已经读取的首字节，再补齐包头剩余部分。
        byte[] header = new byte[HEADER_LENGTH];
        header[0] = (byte) firstByte;
        readFully(inputStream, header, 1, HEADER_LENGTH - 1, packetIndex, "包头");
        return header;
    }

    /** 完整读取一个字节数组。 */
    private static void readFully(
            InputStream inputStream,
            byte[] target,
            int packetIndex,
            String partName) throws IOException {
        // 从目标数组起始位置读取全部所需数据。
        readFully(inputStream, target, 0, target.length, packetIndex, partName);
    }

    /** 完整读取指定范围的数据。 */
    private static void readFully(
            InputStream inputStream,
            byte[] target,
            int offset,
            int length,
            int packetIndex,
            String partName) throws IOException {
        // 输入流单次读取可能不足，因此循环直到填满目标范围。
        int totalRead = 0;
        while (totalRead < length) {
            int readCount = inputStream.read(target, offset + totalRead, length - totalRead);
            if (readCount == -1) {
                throw new EOFException("第 " + packetIndex + " 个 PDXP 数据包的" + partName + "不完整");
            }
            totalRead += readCount;
        }
    }

    /** 将包头和数据域转换为结构化数据包。 */
    private static PdxpPacket parsePacket(byte[] header, byte[] data) throws IOException {
        // 协议版本由版本字节最高两位表示，规范值为二进制 10。
        int version = (header[0] >>> 6) & 0x03;
        if (version != 2) {
            throw new IOException("不支持的 PDXP 协议版本编码：" + version);
        }

        // 预留字段必须固定为四个零字节。
        for (int index = 20; index <= 23; index++) {
            if (header[index] != 0) {
                throw new IOException("PDXP 包头预留字段必须全部为 0");
            }
        }

        // 按协议规定的小端字节序读取所有数值字段。
        int missionId = readUnsignedShort(header, 1);
        long sourceId = readUnsignedInt(header, 3);
        long destinationId = readUnsignedInt(header, 7);
        long dataId = readUnsignedInt(header, 11);
        long sequenceNumber = readUnsignedInt(header, 15);
        int flagValue = header[19] & 0xFF;
        int dateDays = readUnsignedShort(header, 24);
        long timeUnits = readUnsignedInt(header, 26);

        // 协议规定 2000 年 1 月 1 日计为第 1 天，因此积日不能为 0。
        if (dateDays == 0) {
            throw new IOException("PDXP 发送日期积日不能为0");
        }

        // 一天最多包含 864000000 个 0.1 毫秒计数，达到该值表示时标越界。
        if (timeUnits >= 864_000_000L) {
            throw new IOException("PDXP 发送时标超出当日有效范围：" + timeUnits);
        }

        // 构造包含原始值和便捷解释方法的不可变数据包。
        return new PdxpPacket(
                header[0] & 0xFF,
                missionId,
                sourceId,
                destinationId,
                dataId,
                sequenceNumber,
                new ProcessingFlag(flagValue),
                dateDays,
                timeUnits,
                header,
                data);
    }

    /** 按小端字节序读取无符号两字节整数。 */
    private static int readUnsignedShort(byte[] data, int offset) {
        // Java 没有无符号短整数，因此提升为整数保存。
        return ByteBuffer.wrap(data, offset, Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort() & 0xFFFF;
    }

    /** 按小端字节序读取无符号四字节整数。 */
    private static long readUnsignedInt(byte[] data, int offset) {
        // Java 没有无符号整数，因此提升为长整数保存。
        return ByteBuffer.wrap(data, offset, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt() & 0xFFFFFFFFL;
    }

    /** 将字节数组转换为大写十六进制文本。 */
    public static String toHex(byte[] data) {
        // 使用预分配缓冲区减少多次字符串拼接。
        StringBuilder builder = new StringBuilder(data.length * 3);
        for (int index = 0; index < data.length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", data[index] & 0xFF));
        }
        return builder.toString();
    }

    /** 将解析结果输出到控制台。 */
    private static void printPackets(List<PdxpPacket> packets) {
        // 先输出本次成功解析的数据包总数。
        System.out.println("解析完成，共 " + packets.size() + " 个 PDXP 数据包");

        // 再逐包输出版本、地址、标志、时间和数据域等字段。
        for (int index = 0; index < packets.size(); index++) {
            PdxpPacket packet = packets.get(index);
            System.out.println("第 " + (index + 1) + " 个数据包：" + packet);
        }
    }

    /**
     * 命令行入口，打印文件内每个数据包的主要字段。
     *
     * @param args 第一个参数为待解析文件路径
     * @throws IOException 文件读取或协议解析失败
     */
    public static void main(String[] args) throws IOException {
        // 手动测试其他文件时，只需要修改下面这一行文件路径。
        String filePath = "D:\\工作文档\\数据处理软件\\code\\data-processing\\docs\\pdxp-generated-samples.dat";

        // 自动识别文件格式并解析全部数据包。
        List<PdxpPacket> packets = parse(Paths.get(filePath));

        // 将解析结果统一输出到控制台。
        printPackets(packets);
    }

    /** 文件自动识别时使用的内部数据格式。 */
    private enum InputFormat {
        /** 原始连续二进制。 */
        BINARY,
        /** 空白分隔的十六进制文本。 */
        HEX_TEXT,
        /** 带 7E 定界和 7D 转义的 TCP 数据。 */
        TCP
    }

    /** PDXP 数据处理标志。 */
    public static final class ProcessingFlag {

        /** 原始标志字节。 */
        private final int rawValue;

        /** 保存数据处理标志原始值。 */
        private ProcessingFlag(int rawValue) {
            this.rawValue = rawValue;
        }

        /** @return 原始标志值 */
        public int getRawValue() {
            return rawValue;
        }

        /** @return 保存策略编码 */
        public int getSaveFlag() {
            return (rawValue >>> 6) & 0x03;
        }

        /** @return 加解密状态编码 */
        public int getEncryptionFlag() {
            return (rawValue >>> 3) & 0x07;
        }

        /** @return 是否强制转发 */
        public boolean isForceTransfer() {
            return (rawValue & 0x04) != 0;
        }

        /** @return 是否需要应答 */
        public boolean isAnswerRequired() {
            return (rawValue & 0x02) != 0;
        }

        /** @return 是否指向仿真目标 */
        public boolean isSimulationTarget() {
            return (rawValue & 0x01) != 0;
        }

        /** @return 便于展示的标志说明 */
        @Override
        public String toString() {
            return "原始值=" + String.format("0x%02X", rawValue)
                    + "，保存策略=" + getSaveFlag()
                    + "，加解密状态=" + getEncryptionFlag()
                    + "，强制转发=" + isForceTransfer()
                    + "，需要应答=" + isAnswerRequired()
                    + "，仿真目标=" + isSimulationTarget();
        }
    }

    /** 解析后的不可变 PDXP 数据包。 */
    public static final class PdxpPacket {

        /** 版本字节原始值。 */
        private final int versionByte;
        /** 任务标志。 */
        private final int missionId;
        /** 信源地址。 */
        private final long sourceId;
        /** 信宿地址。 */
        private final long destinationId;
        /** 数据标志。 */
        private final long dataId;
        /** 包序号。 */
        private final long sequenceNumber;
        /** 数据处理标志。 */
        private final ProcessingFlag processingFlag;
        /** 相对 2000 年元旦的积日。 */
        private final int dateDays;
        /** 相对北京时零时的 0.1 毫秒计数。 */
        private final long timeUnits;
        /** PDXP包头之前按配置读取的传输头，零长度时为空数组。 */
        private final byte[] transportHeader;
        /** 数据域。 */
        private final byte[] data;
        /** 包头和数据域组成的完整PDXP原始包。 */
        private final byte[] rawPacket;

        /** 保存所有已经校验的协议字段。 */
        private PdxpPacket(
                int versionByte,
                int missionId,
                long sourceId,
                long destinationId,
                long dataId,
                long sequenceNumber,
                ProcessingFlag processingFlag,
                int dateDays,
                long timeUnits,
                byte[] header,
                byte[] data) {
            this(versionByte, missionId, sourceId, destinationId, dataId,
                    sequenceNumber, processingFlag, dateDays, timeUnits,
                    new byte[0], header, data);
        }

        /** 保存传输头以及所有已经校验的PDXP协议字段。 */
        private PdxpPacket(
                int versionByte,
                int missionId,
                long sourceId,
                long destinationId,
                long dataId,
                long sequenceNumber,
                ProcessingFlag processingFlag,
                int dateDays,
                long timeUnits,
                byte[] transportHeader,
                byte[] header,
                byte[] data) {
            this.versionByte = versionByte;
            this.missionId = missionId;
            this.sourceId = sourceId;
            this.destinationId = destinationId;
            this.dataId = dataId;
            this.sequenceNumber = sequenceNumber;
            this.processingFlag = processingFlag;
            this.dateDays = dateDays;
            this.timeUnits = timeUnits;
            this.transportHeader = Arrays.copyOf(
                    transportHeader, transportHeader.length);
            this.data = Arrays.copyOf(data, data.length);
            this.rawPacket = new byte[transportHeader.length
                    + header.length + data.length];
            // 依次复制传输头、PDXP包头和数据域，保留完整UDP协议边界。
            System.arraycopy(transportHeader, 0, rawPacket,
                    0, transportHeader.length);
            System.arraycopy(header, 0, rawPacket,
                    transportHeader.length, header.length);
            System.arraycopy(data, 0, rawPacket,
                    transportHeader.length + header.length, data.length);
        }

        /** 为已经解析的PDXP包补充UDP传输头。 */
        private PdxpPacket withTransportHeader(byte[] header) {
            // 当前对象尚未包含传输头，原始包前32字节就是PDXP固定包头。
            byte[] pdxpHeader = Arrays.copyOfRange(
                    rawPacket, 0, HEADER_LENGTH);
            return new PdxpPacket(
                    versionByte,
                    missionId,
                    sourceId,
                    destinationId,
                    dataId,
                    sequenceNumber,
                    processingFlag,
                    dateDays,
                    timeUnits,
                    header,
                    pdxpHeader,
                    data);
        }

        /** @return 完整版本字节 */
        public int getVersionByte() {
            return versionByte;
        }

        /** @return 最高两位表示的协议版本编码 */
        public int getVersion() {
            return (versionByte >>> 6) & 0x03;
        }

        /** @return 任务标志 */
        public int getMissionId() {
            return missionId;
        }

        /** @return 信源地址 */
        public long getSourceId() {
            return sourceId;
        }

        /** @return 信宿地址 */
        public long getDestinationId() {
            return destinationId;
        }

        /** @return 数据标志 */
        public long getDataId() {
            return dataId;
        }

        /** @return 包序号 */
        public long getSequenceNumber() {
            return sequenceNumber;
        }

        /** @return 数据处理标志 */
        public ProcessingFlag getProcessingFlag() {
            return processingFlag;
        }

        /** @return 原始积日 */
        public int getDateDays() {
            return dateDays;
        }

        /** @return 按协议换算的发送日期 */
        public LocalDate getSendDate() {
            // 协议规定 2000 年 1 月 1 日计为第 1 天。
            return DATE_EPOCH.plusDays(dateDays - 1L);
        }

        /** @return 原始 0.1 毫秒计数 */
        public long getTimeUnits() {
            return timeUnits;
        }

        /** @return 实际长度的传输头副本，配置为零时为空数组 */
        public byte[] getTransportHeader() {
            return Arrays.copyOf(transportHeader, transportHeader.length);
        }

        /** @return 换算后的当日时间间隔 */
        public Duration getTimeSinceMidnight() {
            // 每个计数单位为 0.1 毫秒，即十万纳秒。
            return Duration.ofNanos(timeUnits * 100_000L);
        }

        /** @return 数据域长度 */
        public int getDataLength() {
            return data.length;
        }

        /** @return 数据域副本 */
        public byte[] getData() {
            // 返回副本以维持对象不可变性。
            return Arrays.copyOf(data, data.length);
        }

        /** @return 包头和数据域组成的完整PDXP原始包副本 */
        public byte[] getRawPacket() {
            return Arrays.copyOf(rawPacket, rawPacket.length);
        }

        /** @return 便于展示的主要协议字段 */
        @Override
        public String toString() {
            return "版本=" + getVersion()
                    + "，任务标志=" + missionId
                    + "，信源地址=" + sourceId
                    + "，信宿地址=" + destinationId
                    + "，数据标志=" + dataId
                    + "，包序号=" + sequenceNumber
                    + "，处理标志={" + processingFlag + "}"
                    + "，发送日期=" + getSendDate()
                    + "，时标计数=" + timeUnits
                    + "，数据域长度=" + data.length
                    + "，数据域=" + toHex(data);
        }
    }
}
