package com.example.dataprocess.service.impl;

import com.example.dataprocess.collection.CollectInterfaceStatistics;
import com.example.dataprocess.config.FepMqProperties;
import com.example.dataprocess.config.FepProperties;
import com.example.dataprocess.config.MinioStorageProperties;
import com.example.dataprocess.dto.FepFileMessage;
import com.example.dataprocess.entity.CollectInterfaceRuntimeConfig;
import com.example.dataprocess.entity.FepFileTransferRecord;
import com.example.dataprocess.enums.FepFileProcessStatus;
import com.example.dataprocess.mapper.FepFileTransferRecordMapper;
import com.example.dataprocess.service.FepFileResultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** 可靠执行FEP文件对象上传和消息发布。 */
@Service
public class FepFileResultServiceImpl implements FepFileResultService {
    /** 告警组件采用独立注入，复用已有离线构造入口。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataprocess.service.DataAlarmRecorder alarmRecorder;



    /** 文件结果处理日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            FepFileResultServiceImpl.class);
    /** 消息时间格式，精确到毫秒。 */
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** 单次失败后的最大等待时间。 */
    private static final long MAX_RETRY_DELAY_MILLIS = 3600000L;

    /** FEP文件处理记录访问接口。 */
    private final FepFileTransferRecordMapper recordMapper;
    /** 对象存储连接配置。 */
    private final MinioStorageProperties storageProperties;
    /** 文件消息发送配置。 */
    private final FepMqProperties mqProperties;
    /** 文件结果重试配置。 */
    private final FepProperties fepProperties;
    /** 消息发送模板。 */
    private final RocketMQTemplate rocketMQTemplate;
    /** 统一的对象转换工具。 */
    private final ObjectMapper objectMapper;
    /** 文件后续处理专用线程池。 */
    private final ThreadPoolExecutor resultExecutor;
    /** 当前已经进入线程池的记录编号。 */
    private final Set<Long> submittedRecordIds =
            ConcurrentHashMap.newKeySet();
    /** FEP文件成功处理数量统计组件。 */
    private final CollectInterfaceStatistics statistics;
    /** 延迟创建并复用的对象存储客户端。 */
    private volatile MinioClient storageClient;

    public FepFileResultServiceImpl(
            FepFileTransferRecordMapper recordMapper,
            MinioStorageProperties storageProperties,
            FepMqProperties mqProperties,
            FepProperties fepProperties,
            RocketMQTemplate rocketMQTemplate,
            ObjectMapper objectMapper,
            @Qualifier("fepResultExecutor")
            ThreadPoolExecutor resultExecutor,
            CollectInterfaceStatistics statistics) {
        this.recordMapper = recordMapper;
        this.storageProperties = storageProperties;
        this.mqProperties = mqProperties;
        this.fepProperties = fepProperties;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.resultExecutor = resultExecutor;
        this.statistics = statistics;
    }

    /** 查询重复文件，并立即唤醒尚未完成的处理。 */
    @Override
    public boolean isReceivedAndTriggerRetry(
            CollectInterfaceRuntimeConfig config,
            String fileName,
            long fileLength) {
        // 第一步：使用协议能够提供的四项信息定位文件记录。
        FepFileTransferRecord record = recordMapper.findByIdentity(
                normalizeTaskId(config.getTaskId()),
                config.getInterfaceId(),
                fileName,
                fileLength);
        if (record == null) {
            return false;
        }

        // 第二步：已上传或已发布记录不再要求发送方重复传输。
        if (record.getProcessStatus() != null
                && record.getProcessStatus()
                >= FepFileProcessStatus.STORED.getCode()) {
            submitRecord(record);
            return true;
        }

        // 第三步：已接收状态只有在本地完整文件仍存在时才可以拒绝重传。
        Path localFile = Paths.get(record.getLocalPath());
        if (Files.isRegularFile(localFile)) {
            submitRecord(record);
            return true;
        }

        // 第四步：本地文件已经丢失时允许发送方重新发送并覆盖旧处理状态。
        LOGGER.warn("FEP已接收记录缺少本地文件，将允许重新传输，记录编号：{}，文件：{}",
                record.getId(), record.getLocalPath());
        return false;
    }

    /** 登记完整文件后异步继续处理。 */
    @Override
    public synchronized void recordAndSubmit(
            CollectInterfaceRuntimeConfig config,
            Path completedFile,
            LocalDateTime completedTime) throws Exception {
        // 第一步：文件必须真实存在，数据库才能记录为已接收。
        validateCompletedFile(completedFile);
        Path normalizedFile = completedFile.toAbsolutePath().normalize();
        long fileLength = Files.size(normalizedFile);

        // 第二步：提取小写文件后缀，并提前固定对象名称，重试时不产生多个对象。
        String fileName = normalizedFile.getFileName().toString();
        String fileType = extractFileType(fileName);
        String objectName = buildObjectName(fileType, completedTime);
        FepFileTransferRecord record = new FepFileTransferRecord();
        record.setTaskId(normalizeTaskId(config.getTaskId()));
        record.setInterfaceId(config.getInterfaceId());
        record.setFileName(fileName);
        record.setFileType(fileType);
        record.setFileLength(fileLength);
        record.setLocalPath(normalizedFile.toString());
        record.setObjectName(objectName);
        record.setFileUrl("");
        record.setProcessStatus(FepFileProcessStatus.RECEIVED.getCode());
        record.setCompletedTime(completedTime);

        // 第三步：新文件插入记录，缺失本地文件后的重传则重置原记录。
        FepFileTransferRecord existing = recordMapper.findByIdentity(
                record.getTaskId(),
                record.getInterfaceId(),
                record.getFileName(),
                record.getFileLength());
        if (existing == null) {
            recordMapper.insertReceived(record);
        } else {
            record.setId(existing.getId());
            recordMapper.resetReceived(record);
        }

        // 第四步：数据库已经形成可靠重试入口，再提交后续异步任务。
        submitRecord(record);
        LOGGER.info("FEP完整文件已经登记，记录编号：{}，文件：{}",
                record.getId(), normalizedFile);
    }

    /** 定时扫描所有到达重试时间的文件。 */
    @Scheduled(
            fixedDelayString = "${data-processing.fep.retry-interval-millis}",
            initialDelayString = "${data-processing.fep.retry-interval-millis}")
    public void retryPendingFiles() {
        // 第一步：从数据库分批恢复程序重启前和运行中失败的任务。
        List<FepFileTransferRecord> records = recordMapper.findRetryable(
                LocalDateTime.now(), fepProperties.getRetryBatchSize());

        // 第二步：逐条提交，内存集合会过滤仍在执行的同一记录。
        for (FepFileTransferRecord record : records) {
            submitRecord(record);
        }
    }

    /** 将一条记录提交到独立文件结果线程池。 */
    private void submitRecord(FepFileTransferRecord record) {
        // 已发布记录无需再次进入线程池。
        if (record == null
                || record.getId() == null
                || Integer.valueOf(FepFileProcessStatus.PUBLISHED.getCode())
                .equals(record.getProcessStatus())) {
            return;
        }

        // 同一记录已经排队或执行时，不再重复提交。
        if (!submittedRecordIds.add(record.getId())) {
            return;
        }

        try {
            // 独立线程完成网络调用，最终一定释放内存占用标识。
            resultExecutor.execute(() -> {
                try {
                    processRecord(record);
                } finally {
                    submittedRecordIds.remove(record.getId());
                }
            });
        } catch (RejectedExecutionException exception) {
            // 队列已满时交给下次数据库扫描继续处理。
            submittedRecordIds.remove(record.getId());
            LOGGER.warn("FEP文件结果线程池已满，记录将在稍后重试，记录编号：{}",
                    record.getId());
        }
    }

    /** 根据数据库状态从未完成的步骤继续执行。 */
    private void processRecord(FepFileTransferRecord record) {
        try {
            // 第一步：已接收文件先上传对象存储，并持久化上传完成状态。
            if (Integer.valueOf(FepFileProcessStatus.RECEIVED.getCode())
                    .equals(record.getProcessStatus())) {
                Path completedFile = Paths.get(record.getLocalPath());
                validateCompletedFile(completedFile);
                validateStorageConfiguration();
                uploadFile(completedFile, record.getObjectName());
                String fileUrl = buildFileUrl(record.getObjectName());
                recordMapper.markStored(record.getId(), fileUrl);
                record.setFileUrl(fileUrl);
                record.setProcessStatus(FepFileProcessStatus.STORED.getCode());
            }

            // 第二步：已上传文件发送完成消息，并持久化发布完成状态。
            if (Integer.valueOf(FepFileProcessStatus.STORED.getCode())
                    .equals(record.getProcessStatus())) {
                validateMessageConfiguration();
                publishMessage(record);
                recordMapper.markPublished(record.getId());
                record.setProcessStatus(FepFileProcessStatus.PUBLISHED.getCode());
                // 文件上传和消息发布全部成功后才统计一次处理完成数量。
                statistics.recordProcessing(
                        record.getTaskId(), record.getInterfaceId());
            }

            // 第三步：两项外部操作都完成后，清理本地完整文件。
            deleteLocalFile(record);
        } catch (Exception | LinkageError exception) {
            // 业务异常或依赖加载异常都保留当前状态，并记录退避重试时间。
            long retryDelay = calculateRetryDelay(record.getRetryCount());
            String failureMessage = abbreviateError(exception.getMessage());
            recordMapper.markRetryFailure(
                    record.getId(),
                    failureMessage,
                    LocalDateTime.now().plusNanos(retryDelay * 1000000L));
            if (alarmRecorder != null) {
                alarmRecorder.recordForInterface(record.getInterfaceId(), com.example.common.enums.DataAlarmType.FILE_RESULT,
                        "文件：" + record.getFileName() + "；记录ID：" + record.getId()
                        + "；阶段：" + FepFileProcessStatus.getNameByCode(record.getProcessStatus())
                        + "；重试次数：" + record.getRetryCount() + "；本次失败将自动重试；原因：" + failureMessage);
            }
            LOGGER.warn("FEP文件后续处理失败，记录编号：{}，状态：{}，原因：{}",
                    record.getId(),
                    FepFileProcessStatus.getNameByCode(
                            record.getProcessStatus()),
                    failureMessage);
        }
    }

    /** 将本地完整文件上传到对象存储。 */
    private void uploadFile(Path completedFile, String objectName)
            throws Exception {
        // 第一步：取得复用客户端并确保目标存储桶存在。
        MinioClient client = getStorageClient();
        ensureBucketExists(client);

        // 第二步：无法识别内容类型时使用通用二进制类型。
        String contentType = Files.probeContentType(completedFile);
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = "application/octet-stream";
        }

        // 第三步：按文件实际长度以流方式上传，避免整体载入内存。
        try (InputStream inputStream = Files.newInputStream(completedFile)) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(objectName)
                            .stream(inputStream, Files.size(completedFile), -1)
                            .contentType(contentType)
                            .build());
        }
        LOGGER.info("FEP文件上传对象存储成功，文件：{}，对象名称：{}",
                completedFile.getFileName(), objectName);
    }

    /** 发布一条可以按记录编号识别的文件完成消息。 */
    private void publishMessage(FepFileTransferRecord record) throws Exception {
        // 第一步：根据已持久化的文件地址和接收时间构造消息正文。
        FepFileMessage fileMessage = buildMessage(
                record.getFileUrl(),
                record.getFileType(),
                record.getCompletedTime());
        byte[] messageData = objectMapper.writeValueAsBytes(fileMessage);

        // 第二步：记录编号作为消息键，便于消费方过滤极端情况下的重复消息。
        Message<byte[]> rocketMessage = MessageBuilder
                .withPayload(messageData)
                .setHeader(RocketMQHeaders.KEYS, "fep-file-" + record.getId())
                .build();
        SendResult sendResult = rocketMQTemplate.syncSend(
                buildDestination(), rocketMessage);
        LOGGER.info("FEP文件完成消息发送成功，记录编号：{}，消息标识：{}，文件地址：{}",
                record.getId(), sendResult.getMsgId(), record.getFileUrl());
    }

    /** 删除已经完成全部后续处理的本地文件。 */
    private void deleteLocalFile(FepFileTransferRecord record) {
        try {
            // 文件不存在也视为清理完成，避免无意义地重复报警。
            Files.deleteIfExists(Paths.get(record.getLocalPath()));
        } catch (Exception exception) {
            LOGGER.warn("FEP本地完整文件清理失败，文件：{}，原因：{}",
                    record.getLocalPath(), exception.getMessage());
        }
    }

    /** 延迟创建对象存储客户端。 */
    private MinioClient getStorageClient() {
        // 客户端已经创建时直接复用同一连接配置。
        MinioClient currentClient = storageClient;
        if (currentClient != null) {
            return currentClient;
        }

        // 首次使用时加锁，防止多个任务同时重复创建。
        synchronized (this) {
            if (storageClient == null) {
                storageClient = MinioClient.builder()
                        .endpoint(storageProperties.getEndpoint())
                        .credentials(
                                storageProperties.getAccessKey(),
                                storageProperties.getSecretKey())
                        .build();
            }
            return storageClient;
        }
    }

    /** 确保文件保存桶存在。 */
    private void ensureBucketExists(MinioClient client) throws Exception {
        // 存储桶已经存在时不再执行创建操作。
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(storageProperties.getBucketName())
                        .build());
        if (exists) {
            return;
        }

        // 加锁后再次检查，避免多个线程重复创建同一个桶。
        synchronized (this) {
            boolean existsAfterLock = client.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .build());
            if (!existsAfterLock) {
                client.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(storageProperties.getBucketName())
                                .build());
            }
        }
    }

    /** 构造按日期分层且不会因重试变化的对象名称。 */
    private String buildObjectName(
            String fileType,
            LocalDateTime completedTime) {
        // 第一步：对象名称复用已经校验的小写后缀。
        String extension = fileType == null ? "" : "." + fileType;
        // 第二步：对象名称在数据库登记前只生成一次。
        String dateDirectory = completedTime.format(
                DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectFileName = UUID.randomUUID()
                .toString()
                .replace("-", "")
                + extension;
        return dateDirectory + "/" + objectFileName;
    }

    /** 从原始文件名提取小写后缀。 */
    private String extractFileType(String fileName) {
        // 第一步：文件名为空、没有点号或以点号结尾时视为没有后缀。
        if (fileName == null) {
            return null;
        }
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return null;
        }
        // 第二步：只保存长度不超过字段限制的字母数字后缀。
        String fileType = fileName.substring(extensionIndex + 1);
        if (!fileType.matches("[A-Za-z0-9]{1,50}")) {
            return null;
        }
        return fileType.toLowerCase(Locale.ROOT);
    }

    /** 构造文件对外访问地址。 */
    private String buildFileUrl(String objectName) {
        // 去掉地址末尾斜线，避免生成重复分隔符。
        String publicUrl = storageProperties.getPublicUrl().trim();
        while (publicUrl.endsWith("/")) {
            publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
        }
        return publicUrl + "/"
                + storageProperties.getBucketName()
                + "/"
                + objectName;
    }

    /** 构造固定结构的文件完成消息。 */
    private FepFileMessage buildMessage(
            String fileUrl,
            String fileType,
            LocalDateTime completedTime) {
        // 第一步：把文件访问地址和后缀写入消息，未知需求信息保持空字符串。
        FepFileMessage message = new FepFileMessage();
        message.setFileUrl(fileUrl);
        message.setFileType(fileType);

        // 第二步：当前仅写入接收完成时间，其余基础信息保持空字符串。
        FepFileMessage.BaseInfo baseInfo = new FepFileMessage.BaseInfo();
        baseInfo.setTime(completedTime.format(MESSAGE_TIME_FORMATTER));
        message.setBaseInfo(baseInfo);
        return message;
    }

    /** 组合文件消息主题和可选标签。 */
    private String buildDestination() {
        // 标签为空时只使用主题，避免生成多余冒号。
        String fileTag = mqProperties.getFileTag();
        return fileTag == null || fileTag.trim().isEmpty()
                ? mqProperties.getFileTopic()
                : mqProperties.getFileTopic() + ":" + fileTag;
    }

    /** 计算带上限的逐步延长重试时间。 */
    private long calculateRetryDelay(Integer retryCount) {
        // 次数过大时限制位移，避免数值溢出。
        int safeRetryCount = retryCount == null
                ? 0
                : Math.min(retryCount, 10);
        long delay = fepProperties.getRetryIntervalMillis()
                * (1L << safeRetryCount);
        return Math.min(delay, MAX_RETRY_DELAY_MILLIS);
    }

    /** 将可能过长的外部异常信息限制在数据库字段长度内。 */
    private String abbreviateError(String message) {
        // 没有异常正文时使用明确中文提示。
        if (message == null || message.trim().isEmpty()) {
            return "未提供具体失败原因";
        }
        return message.length() <= 1900
                ? message
                : message.substring(0, 1900);
    }

    /** 检查待处理的本地完整文件。 */
    private void validateCompletedFile(Path completedFile) {
        // 只有真实存在的普通文件才能进入上传阶段。
        if (completedFile == null || !Files.isRegularFile(completedFile)) {
            throw new IllegalArgumentException("接收完成的本地文件不存在");
        }
    }

    /** 检查对象存储配置。 */
    private void validateStorageConfiguration() {
        // 对象上传所需字段全部由配置中心提供。
        requireText(storageProperties.getEndpoint(), "对象存储服务地址不能为空");
        requireText(storageProperties.getPublicUrl(), "文件对外访问地址不能为空");
        requireText(storageProperties.getAccessKey(), "对象存储访问账号不能为空");
        requireText(storageProperties.getSecretKey(), "对象存储访问密码不能为空");
        requireText(storageProperties.getBucketName(), "对象存储桶名称不能为空");
    }

    /** 检查消息发布配置。 */
    private void validateMessageConfiguration() {
        // 消息发布阶段只校验本阶段需要的配置。
        requireText(mqProperties.getFileTopic(), "文件消息主题不能为空");
    }

    /** 检查必填文本。 */
    private void requireText(String value, String message) {
        // 空值和空白字符串均视为未配置。
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(message);
        }
    }

    /** 将没有任务编号的测试接口统一保存为固定测试任务编号。 */
    private String normalizeTaskId(String taskId) {
        // 测试接口与正式接口使用同一字符串唯一键规则。
        return taskId == null || taskId.trim().isEmpty()
                ? "TEST" : taskId.trim();
    }
}
