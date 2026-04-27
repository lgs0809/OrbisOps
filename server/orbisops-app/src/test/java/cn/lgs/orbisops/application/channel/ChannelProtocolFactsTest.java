package cn.lgs.orbisops.application.channel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelProtocolFactsTest {

    @Test
    void outboundMetadataKeepsOnlyStableAllowlistedCorrelationFacts() {
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(Map.of(
                "runId", "run-1",
                "sessionId", "session-1",
                "outboxId", 7L,
                "source", "AGENT_TOOL",
                "token", "must-not-leak"));

        ChannelOutboundMetadata resolved = metadata.withDeliveryId(metadata.resolveDeliveryId("message-1"));
        Map<String, Object> protocol = resolved.toProtocolMap();

        assertEquals("run-1", resolved.runId());
        assertEquals("session-1", resolved.sessionId());
        assertEquals(7L, resolved.outboxId());
        assertEquals("channel-outbox-7", resolved.deliveryId());
        assertEquals(7L, protocol.get("outboxId"));
        assertFalse(protocol.containsKey("token"));
    }

    @Test
    void directMessagesUseMessageIdAsDeliveryId() {
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(Map.of("runId", "run-2"));

        assertEquals("message-2", metadata.resolveDeliveryId("message-2"));
    }

    @Test
    void deliveryConfigurationSeparatesStableReplyCapabilityFromOpenProtocolConfig() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("outboundUrl", "https://example.test/hook");
        source.put("customHeader", "value");

        ChannelDeliveryConfiguration configuration = ChannelDeliveryConfiguration.from(source);
        source.put("outboundUrl", "https://mutated.test/hook");

        assertTrue(configuration.replyConfigured());
        assertEquals("https://example.test/hook", configuration.outboundUrl());
        assertEquals("value", configuration.protocolConfig().get("customHeader"));
    }
}
