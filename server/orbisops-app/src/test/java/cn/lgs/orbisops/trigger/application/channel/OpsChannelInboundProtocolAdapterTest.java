package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelInboundProtocolPort;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelPayloadCipherPort;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAction;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAttachment;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelMessage;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelSignature;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelInboundProtocolAdapterTest {

    @Test
    void unavailableCipherFailsClosedBeforePersistencePayloadIsCreated() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("credential-ref")).thenReturn("secret");
        ChannelPayloadCipherPort cipher = mock(ChannelPayloadCipherPort.class);
        when(cipher.available()).thenReturn(false);
        OpsChannelInboundProtocolAdapter adapter = adapter(secrets, cipher);
        ChannelModels.InboundMessage message = message(Instant.now().getEpochSecond());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adapter.verifyAndProtect(channel(), message, String.valueOf(message.timestamp()), signature(message)));

        assertEquals("CHANNEL_INBOUND_ENCRYPTION_UNAVAILABLE", failure.getMessage());
        verify(cipher, never()).encrypt(anyString(), anyString());
    }

    @Test
    void timestampMismatchStopsBeforeCredentialLookup() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        ChannelPayloadCipherPort cipher = mock(ChannelPayloadCipherPort.class);
        OpsChannelInboundProtocolAdapter adapter = adapter(secrets, cipher);
        long timestamp = Instant.now().getEpochSecond();
        ChannelModels.InboundMessage message = message(timestamp - 1);

        SecurityException failure = assertThrows(SecurityException.class,
                () -> adapter.verifyAndProtect(channel(), message, String.valueOf(timestamp), "unused"));

        assertEquals("CHANNEL_TIMESTAMP_MISMATCH", failure.getMessage());
        verify(secrets, never()).resolve(anyString());
        verify(cipher, never()).encrypt(anyString(), anyString());
    }

    @Test
    void arbitraryRemoteAttachmentIsRejectedBeforeCredentialLookup() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        ChannelPayloadCipherPort cipher = mock(ChannelPayloadCipherPort.class);
        OpsChannelInboundProtocolAdapter adapter = adapter(secrets, cipher);
        long timestamp = Instant.now().getEpochSecond();
        ChannelModels.InboundMessage message = new ChannelModels.InboundMessage(
                "external-1", "conversation-1", "sender-1", "analyze", timestamp, Map.of(), "TEXT",
                List.of(new ChannelModels.Attachment("attachment-1", "trace.log", "text/plain", 128,
                        "https://attacker.example/trace.log", "a".repeat(64))), null);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> adapter.verifyAndProtect(channel(), message, String.valueOf(timestamp), "unused"));

        assertEquals("CHANNEL_ATTACHMENT_CONTENT_REF_UNTRUSTED", failure.getMessage());
        verify(secrets, never()).resolve(anyString());
        verify(cipher, never()).encrypt(anyString(), anyString());
    }

    @Test
    void inboundSafetyLimitsFailClosedBeforeEncryption() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        ChannelPayloadCipherPort cipher = mock(ChannelPayloadCipherPort.class);
        OpsChannelInboundProtocolAdapter adapter = adapter(secrets, cipher);
        long timestamp = Instant.now().getEpochSecond();

        ChannelModels.InboundMessage oversizedMessage = new ChannelModels.InboundMessage(
                "external-1", "conversation-1", "sender-1", "x".repeat(12_001), timestamp,
                Map.of(), "TEXT", List.of(), null);
        assertEquals("CHANNEL_INBOUND_MESSAGE_TOO_LARGE", assertThrows(IllegalArgumentException.class,
                () -> adapter.protectTrusted(channel(), oversizedMessage)).getMessage());

        ChannelModels.InboundMessage oversizedMetadata = new ChannelModels.InboundMessage(
                "external-2", "conversation-1", "sender-1", "analyze", timestamp,
                Map.of("bulk", "x".repeat(8_192)), "TEXT", List.of(), null);
        assertEquals("CHANNEL_INBOUND_METADATA_TOO_LARGE", assertThrows(IllegalArgumentException.class,
                () -> adapter.protectTrusted(channel(), oversizedMetadata)).getMessage());

        List<ChannelModels.Attachment> tooManyAttachments = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> attachment("attachment-" + index, 128))
                .toList();
        ChannelModels.InboundMessage attachmentFlood = new ChannelModels.InboundMessage(
                "external-3", "conversation-1", "sender-1", "analyze", timestamp,
                Map.of(), "TEXT", tooManyAttachments, null);
        assertEquals("CHANNEL_TOO_MANY_ATTACHMENTS", assertThrows(IllegalArgumentException.class,
                () -> adapter.protectTrusted(channel(), attachmentFlood)).getMessage());

        ChannelModels.InboundMessage oversizedAttachment = new ChannelModels.InboundMessage(
                "external-4", "conversation-1", "sender-1", "analyze", timestamp,
                Map.of(), "TEXT", List.of(attachment("attachment-large", 50L * 1024 * 1024 + 1)), null);
        assertEquals("CHANNEL_ATTACHMENT_SIZE_OUT_OF_RANGE", assertThrows(IllegalArgumentException.class,
                () -> adapter.protectTrusted(channel(), oversizedAttachment)).getMessage());

        ChannelModels.InboundMessage oversizedAction = new ChannelModels.InboundMessage(
                "external-5", "conversation-1", "sender-1", "", timestamp,
                Map.of(), "ACTION", List.of(),
                new ChannelModels.Action("action-1", "ACK", "x".repeat(2_001), Map.of()));
        assertEquals("CHANNEL_ACTION_VALUE_TOO_LONG", assertThrows(IllegalArgumentException.class,
                () -> adapter.protectTrusted(channel(), oversizedAction)).getMessage());

        verify(cipher, never()).encrypt(anyString(), anyString());
    }

    @Test
    void verifiedPayloadRoundTripsWithoutPersistingPlaintextSecret() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("credential-ref")).thenReturn("secret");
        ChannelPayloadCipherPort cipher = cipher();
        OpsChannelInboundProtocolAdapter adapter = adapter(secrets, cipher);
        long timestamp = Instant.now().getEpochSecond();
        ChannelModels.InboundMessage message = new ChannelModels.InboundMessage(
                "external-1", "conversation-1", "sender-1", "token=plain-secret investigate", timestamp,
                Map.of("source", "bridge"), "ACTION",
                List.of(new ChannelModels.Attachment("attachment-1", "trace.log", "text/plain", 128,
                        "object://trace/1", "a".repeat(64))),
                new ChannelModels.Action("action-1", "ACK", "confirm", Map.of("severity", "high")));

        ChannelInboundProtocolPort.VerifiedInbound verified = adapter.verifyAndProtect(
                channel(), message, String.valueOf(timestamp), signature(message));

        assertFalse(verified.storagePayloadJson().contains("plain-secret"));
        ChannelMessageRecord stored = new ChannelMessageRecord(
                "message-1", "channel-1", "project-1", "external-1", "conversation-1", "sender-1",
                "session-1", "run-1", "INBOUND", "QUEUED", verified.storagePayloadJson(), null, null, null);
        ChannelModels.InboundMessage restored = adapter.restore(stored);
        assertEquals(message, restored);
    }

    private OpsChannelInboundProtocolAdapter adapter(OpsSecretResolver secrets, ChannelPayloadCipherPort cipher) {
        return new OpsChannelInboundProtocolAdapter(
                secrets, cipher, new ChannelOutboundContentPolicy(), 300, 12000, 8192);
    }

    private ChannelRecord channel() {
        return new ChannelRecord("channel-1", "project-1",
                ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 3, "agent-hash"),
                "Oncall", "GENERIC_WEBHOOK", "credential-ref", Map.of(), ChannelStatus.ACTIVE,
                "admin", null, null);
    }

    private ChannelModels.InboundMessage message(long timestamp) {
        return new ChannelModels.InboundMessage(
                "external-1", "conversation-1", "sender-1", "investigate", timestamp,
                Map.of(), "TEXT", List.of(), null);
    }

    private ChannelModels.Attachment attachment(String id, long sizeBytes) {
        return new ChannelModels.Attachment(
                id, id + ".log", "text/plain", sizeBytes,
                "object://channel/11111111-1111-1111-1111-111111111111", "a".repeat(64));
    }

    private String signature(ChannelModels.InboundMessage message) {
        OpsChannelMessage protocol = new OpsChannelMessage(
                message.externalMessageId(), message.externalConversationId(), message.senderId(), message.text(),
                message.timestamp(), message.metadata(), message.messageType(),
                message.attachments().stream().map(item -> new OpsChannelAttachment(
                        item.attachmentId(), item.fileName(), item.mediaType(), item.sizeBytes(),
                        item.contentRef(), item.contentHash())).toList(),
                message.action() == null ? null : new OpsChannelAction(
                        message.action().actionId(), message.action().actionType(),
                        message.action().value(), message.action().parameters()));
        return OpsChannelSignature.sign("secret", OpsChannelSignature.inboundPayload(protocol, message.timestamp()));
    }

    private ChannelPayloadCipherPort cipher() {
        return new ChannelPayloadCipherPort() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String encrypt(String plaintext, String associatedData) {
                return "test:" + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String decrypt(String protectedPayload, String associatedData) {
                return new String(Base64.getDecoder().decode(protectedPayload.substring("test:".length())),
                        StandardCharsets.UTF_8);
            }
        };
    }
}
