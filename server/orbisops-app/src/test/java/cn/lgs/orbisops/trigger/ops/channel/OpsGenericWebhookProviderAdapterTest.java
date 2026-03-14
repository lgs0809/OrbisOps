package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelOutboundMetadata;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsGenericWebhookProviderAdapterTest {

    private OpsGenericWebhookChannelAdapter transport;
    private OpsOutboundUrlPolicy urlPolicy;
    private OpsGenericWebhookProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        transport = mock(OpsGenericWebhookChannelAdapter.class);
        urlPolicy = mock(OpsOutboundUrlPolicy.class);
        when(urlPolicy.validate("https://bridge.test/reply")).thenReturn(URI.create("https://bridge.test/reply"));
        adapter = new OpsGenericWebhookProviderAdapter(transport, urlPolicy);
    }

    @Test
    void advertisesTypedGenericWebhookCapabilitiesWithoutLongConnection() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertFalse(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
    }

    @Test
    void configurationStaysTypedAndDelegatesSecurityValidationToHardenedTransport() {
        var configuration = adapter.configuration(channel(Map.of("outboundUrl", "https://bridge.test/reply")));

        assertEquals("channel-1", configuration.channelId());
        assertEquals("project-1", configuration.projectId());
        assertEquals(URI.create("https://bridge.test/reply"), configuration.outboundUrl());

        adapter.validateConfiguration(configuration);
        verify(transport).validateConfiguration(any());
    }

    @Test
    void inboundEnvelopeUsesExternalMessageIdAsStableIdempotencyKeyAndParsesAction() {
        var configuration = adapter.configuration(channel(Map.of()));
        String json = """
                {
                  "externalMessageId":"message-1",
                  "externalConversationId":"conversation-1",
                  "senderId":"sender-1",
                  "text":"approve request",
                  "timestamp":1710000000,
                  "messageType":"ACTION",
                  "action":{"actionId":"approve","actionType":"APPROVE","value":"opaque-action-token","parameters":{}}
                }
                """;

        ChannelInboundEnvelope envelope = adapter.parseInbound(configuration,
                new ChannelInboundPacket("application/json", json.getBytes(StandardCharsets.UTF_8), List.of()));

        assertEquals("message-1", envelope.message().externalMessageId());
        assertEquals("message-1", envelope.idempotencyKey());
        assertEquals("sender-1", envelope.sender().externalPrincipalId());
        assertEquals("opaque-action-token", envelope.content().actions().get(0).opaqueActionToken());
        assertTrue(adapter.parseInteractiveAction(configuration,
                new ChannelInboundPacket("application/json", json.getBytes(StandardCharsets.UTF_8), List.of())).isPresent());
    }

    @Test
    void sendUsesTypedOutboundMessageAndReturnsTypedReceipt() {
        var configuration = adapter.configuration(channel(Map.of("outboundUrl", "https://bridge.test/reply")));
        when(transport.send(any(), eq("conversation-1"), eq("hello"), any()))
                .thenReturn(Map.of("status", "DELIVERED", "delivered", true, "httpStatus", 200));
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(Map.of("deliveryId", "delivery-1", "runId", "run-1"));
        ChannelOutboundMessage message = new ChannelOutboundMessage(
                new ChannelConversationRef("conversation-1", ChannelConversationRef.ConversationKind.DIRECT),
                ChannelRichContent.text("hello"), List.of(), metadata);

        ChannelDeliveryReceipt receipt = adapter.send(configuration, message);

        assertTrue(receipt.delivered());
        assertEquals("DELIVERED", receipt.status());
        assertEquals(200, receipt.providerStatusCode());
    }

    @Test
    void preflightNeverClaimsExternalReadinessWithoutAnActualProbe() {
        var configured = adapter.configuration(channel(Map.of("outboundUrl", "https://bridge.test/reply")));
        var inboundOnly = adapter.configuration(channel(Map.of()));

        ChannelHealthSnapshot configuredHealth = adapter.preflight(configured);
        ChannelHealthSnapshot inboundOnlyHealth = adapter.preflight(inboundOnly);

        assertEquals(ChannelHealthSnapshot.HealthStatus.UNKNOWN, configuredHealth.status());
        assertEquals("CHANNEL_EXTERNAL_CONNECTIVITY_NOT_PROBED", configuredHealth.reasonCode());
        assertEquals(ChannelHealthSnapshot.HealthStatus.DEGRADED, inboundOnlyHealth.status());
        assertEquals("CHANNEL_OUTBOUND_ENDPOINT_NOT_CONFIGURED", inboundOnlyHealth.reasonCode());
    }

    private ChannelRecord channel(Map<String, Object> config) {
        return new ChannelRecord("channel-1", "project-1", ExecutionBinding.react(), "Webhook", "GENERIC_WEBHOOK",
                "credential-ref", config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", Instant.now(), Instant.now());
    }
}
