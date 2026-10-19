package com.example.simulator.controller;

import com.example.simulator.enums.ProtocolProfile;
import com.example.simulator.model.*;
import javax.validation.Valid;
import javax.validation.constraints.*;

/** 创建与完整更新的请求，禁止客户端写入运行统计。 */
public class SaveRequest {
    @Positive
    public Long id;
    @NotBlank(message = "模拟源名称不能为空") @Size(max = 100)
    public String sourceName;
    @NotNull(message = "协议不能为空")
    public Integer transferProtocol;
    public Integer transferType;
    @NotBlank(message = "文件路径不能为空") @Size(max = 1000)
    public String filePath;
    @NotBlank(message = "目标地址不能为空") @Size(max = 100)
    public String targetHost = "127.0.0.1";
    @Min(1) @Max(65535)
    public Integer targetPort;
    @Min(0) @Max(86400000)
    public Long sendIntervalMillis;
    @Min(0) @Max(1)
    public Integer loopEnabled;
    @Size(max = 500)
    public String remark;
    @Valid
    public PdxpConfig pdxp;

    /** 将允许编辑的字段复制到实体，并补充协议默认值。 */
    public SourceConfig toConfig() {
        ProtocolProfile profile = ProtocolProfile.of(transferProtocol);
        if (transferType != null && transferType != profile.transport) {
            throw new IllegalArgumentException("协议与传输方式不匹配");
        }
        SourceConfig config = new SourceConfig();
        config.id = id;
        config.sourceName = sourceName.trim();
        config.transferProtocol = profile.protocol;
        config.transferType = profile.transport;
        config.filePath = filePath.trim();
        config.targetHost = targetHost.trim();
        config.targetPort = targetPort == null ? profile.port : targetPort;
        config.sendIntervalMillis = sendIntervalMillis == null ? profile.interval : sendIntervalMillis;
        config.loopEnabled = loopEnabled == null ? profile.loop : loopEnabled;
        config.remark = remark;
        // FEP不接受无关参数，防止前端误保存隐藏字段。
        if (profile == ProtocolProfile.FEP && pdxp != null) {
            throw new IllegalArgumentException("FEP模拟源不需要PDXP参数");
        }
        config.pdxp = profile == ProtocolProfile.PDXP
                ? (pdxp == null ? new PdxpConfig() : pdxp) : null;
        return config;
    }
}

