package com.example.dataadmin.service.impl;

import com.example.dataadmin.dto.collection.InterfaceSaveRequest;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.CollectProtocolConfig;
import com.example.dataadmin.mapper.CollectInterfaceConfigMapper;
import com.example.dataadmin.mapper.CollectProtocolConfigMapper;
import com.example.dataadmin.mapper.SysRuntimeLogMapper;
import com.example.dataadmin.service.CollectInterfaceRuntimeSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证两种处理方式统一关联协议配置。 */
class DataCollectionServiceImplTest {
    /** 使用模拟数据访问组件，测试不连接真实数据库。 */
    private final CollectInterfaceConfigMapper interfaces = mock(CollectInterfaceConfigMapper.class);
    private final CollectProtocolConfigMapper protocols = mock(CollectProtocolConfigMapper.class);
    private final DataCollectionServiceImpl service = new DataCollectionServiceImpl(
            interfaces,
            protocols,
            mock(SysRuntimeLogMapper.class),
            new ObjectMapper(),
            // 同步通知不是当前测试目标，使用模拟组件隔离外部服务调用。
            mock(CollectInterfaceRuntimeSyncService.class));

    /** 本地处理保存协议配置主键。 */
    @Test
    void shouldSaveProtocolForLocalProcessing() {
        // 准备存在的协议配置，未传处理开关时默认走本地处理。
        InterfaceSaveRequest request = request(null);
        when(protocols.findById(21L)).thenReturn(protocol(2));
        // 保存后确认本地处理保留协议配置主键。
        service.createInterfaceConfig(request);
        ArgumentCaptor<CollectInterfaceConfig> saved = ArgumentCaptor.forClass(CollectInterfaceConfig.class);
        verify(interfaces).insert(saved.capture());
        assertEquals(21L, saved.getValue().getProtocolConfigId());
    }

    /** 切换到RPC时继续保存协议配置主键。 */
    @Test
    void shouldSaveProtocolWhenSwitchingToRpc() {
        // 模拟已存在接口和可选协议。
        CollectInterfaceConfig current = new CollectInterfaceConfig();
        current.setTaskId("TASK-1");
        when(interfaces.findById(8L)).thenReturn(current);
        when(interfaces.update(any())).thenReturn(1);
        when(protocols.findById(21L)).thenReturn(protocol(4));
        // 更新后确认RPC处理保存协议关联。
        service.updateInterfaceConfig(8L, request(1));
        ArgumentCaptor<CollectInterfaceConfig> saved = ArgumentCaptor.forClass(CollectInterfaceConfig.class);
        verify(interfaces).update(saved.capture());
        assertEquals(21L, saved.getValue().getProtocolConfigId());
    }

    /** 切换到本地时继续保存协议配置主键。 */
    @Test
    void shouldSaveProtocolWhenSwitchingToLocal() {
        // 准备目标协议配置和待更新接口。
        CollectInterfaceConfig current = new CollectInterfaceConfig();
        current.setTaskId("TASK-1");
        when(interfaces.findById(8L)).thenReturn(current);
        when(interfaces.update(any())).thenReturn(1);
        when(protocols.findById(21L)).thenReturn(protocol(2));
        // 验证更新实体携带协议配置主键。
        service.updateInterfaceConfig(8L, request(0));
        ArgumentCaptor<CollectInterfaceConfig> saved = ArgumentCaptor.forClass(CollectInterfaceConfig.class);
        verify(interfaces).update(saved.capture());
        assertEquals(21L, saved.getValue().getProtocolConfigId());
    }

    /** 协议配置存在时，任务主键仍保持原有可选规则。 */
    @Test
    void shouldAllowMissingTaskWithProtocol() {
        // 清空任务并准备存在的协议配置。
        InterfaceSaveRequest request = request(0);
        request.setTaskId(null);
        when(protocols.findById(21L)).thenReturn(protocol(2));
        // 协议配置校验通过后允许保存。
        service.createInterfaceConfig(request);
        verify(interfaces).insert(any(CollectInterfaceConfig.class));
    }

    /** 本地处理必须选择存在的协议配置。 */
    @Test
    void shouldRejectMissingOrUnknownLocalProtocol() {
        // 缺少协议配置主键时拒绝保存。
        InterfaceSaveRequest request = request(0);
        request.setProtocolConfigId(null);
        assertThrows(IllegalArgumentException.class, () -> service.createInterfaceConfig(request));
        // 协议配置主键不存在时同样拒绝保存。
        request.setProtocolConfigId(21L);
        assertThrows(IllegalArgumentException.class, () -> service.createInterfaceConfig(request));
        verify(interfaces, never()).insert(any());
    }

    /** RPC处理必须选择存在的协议配置。 */
    @Test
    void shouldRejectMissingOrUnknownProtocol() {
        // 分别验证缺少主键和主键不存在的情况。
        InterfaceSaveRequest request = request(1);
        request.setProtocolConfigId(null);
        assertThrows(IllegalArgumentException.class, () -> service.createInterfaceConfig(request));
        request.setProtocolConfigId(21L);
        assertThrows(IllegalArgumentException.class, () -> service.createInterfaceConfig(request));
        verify(interfaces, never()).insert(any());
    }

    /** 创建统一携带协议配置的采集接口请求。 */
    private InterfaceSaveRequest request(Integer rpcEnabled) {
        // 统一构造采集接口，协议类型由各处理方式对应的配置决定。
        InterfaceSaveRequest request = new InterfaceSaveRequest();
        request.setTaskId("TASK-1");
        // 使用PDXP传输协议构造有效请求。
        request.setTransferProtocol(2);
        request.setRpcEnabled(rpcEnabled);
        request.setProtocolConfigId(21L);
        return request;
    }

    /** 为已有测试准备符合处理方式的新协议类型数据。 */
    private CollectProtocolConfig protocol(Integer protocolType) {
        // 模拟真实协议记录中的类型，避免缺失类型导致业务校验失败。
        CollectProtocolConfig protocol = new CollectProtocolConfig();
        protocol.setProtocolType(protocolType);
        return protocol;
    }
}
