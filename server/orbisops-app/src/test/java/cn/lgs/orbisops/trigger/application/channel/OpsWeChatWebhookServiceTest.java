package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.channel.wechat.OpsWeChatChannelConfiguration;
import cn.lgs.orbisops.trigger.ops.channel.wechat.OpsWeChatProviderAdapter;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsWeChatWebhookServiceTest {

    private ChannelRuntimeReadPort channels;
    private OpsWeChatProviderAdapter adapter;
    private ReceiveChannelMessageUseCase inbound;
    private OpsWeChatChannelConfiguration configuration;
    private OpsWeChatWebhookService service;

    @BeforeEach
    void setUp() {
        channels = mock(ChannelRuntimeReadPort.class);
        adapter = mock(OpsWeChatProviderAdapter.class);
        inbound = mock(ReceiveChannelMessageUseCase.class);
        configuration = mock(OpsWeChatChannelConfiguration.class);
        service = new OpsWeChatWebhookService(channels, adapter, inbound);
    }

    @Test
    void authenticatedUnsupportedMessageIsAcknowledgedWithoutEnteringRuntime() {
        ChannelRecord channel = channel(ExecutionBinding.react(), ChannelStatus.ACTIVE);
        when(channels.findById("wechat-1")).thenReturn(Optional.of(channel));
        when(adapter.configuration(channel)).thenReturn(configuration);
        when(adapter.parseCallback(eq(configuration), any())).thenReturn(Optional.empty());

        Map<String, Object> result = service.receive("wechat-1", "1", "n", "sig", "<xml/>".getBytes());

        assertEquals("IGNORED_UNSUPPORTED_MESSAGE_TYPE", result.get("status"));
        verifyNoInteractions(inbound);
    }

    @Test
    void outputOnlyChannelAcknowledgesValidInboundWithoutStartingRuntime() {
        ChannelRecord channel = channel(ExecutionBinding.none(), ChannelStatus.ACTIVE);
        when(channels.findById("wechat-1")).thenReturn(Optional.of(channel));
        when(adapter.configuration(channel)).thenReturn(configuration);
        when(adapter.parseCallback(eq(configuration), any())).thenReturn(Optional.of(envelope()));

        Map<String, Object> result = service.receive("wechat-1", "1", "n", "sig", "<xml/>".getBytes());

        assertEquals("IGNORED_INBOUND_DISABLED", result.get("status"));
        verifyNoInteractions(inbound);
    }

    @Test
    void activeInboundTextReusesSharedDurableProviderPipeline() {
        ChannelRecord channel = channel(ExecutionBinding.react(), ChannelStatus.ACTIVE);
        ChannelInboundEnvelope envelope = envelope();
        when(channels.findById("wechat-1")).thenReturn(Optional.of(channel));
        when(adapter.configuration(channel)).thenReturn(configuration);
        when(adapter.parseCallback(eq(configuration), any())).thenReturn(Optional.of(envelope));
        when(inbound.receiveProvider("wechat-1", envelope)).thenReturn(Map.of("status", "ACCEPTED"));

        Map<String, Object> result = service.receive("wechat-1", "1", "n", "sig", "<xml/>".getBytes());

        assertEquals("ACCEPTED", result.get("status"));
        verify(inbound).receiveProvider("wechat-1", envelope);
    }

    private ChannelRecord channel(ExecutionBinding binding, ChannelStatus status) {
        return new ChannelRecord(
                "wechat-1", "project-1", binding, "WeChat", "WECHAT", "credential-ref",
                Map.of(), ChannelAccessPolicy.DENY_UNKNOWN, status, "admin", null, null);
    }

    private ChannelInboundEnvelope envelope() {
        ChannelConversationRef conversation = new ChannelConversationRef("openid-1", ChannelConversationRef.ConversationKind.DIRECT);
        return new ChannelInboundEnvelope(
                new ChannelMessageRef("message-1", conversation),
                new ChannelExternalPrincipal("openid-1", "", ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text("hello"),
                List.of(),
                Instant.now(),
                "message-1");
    }
}
