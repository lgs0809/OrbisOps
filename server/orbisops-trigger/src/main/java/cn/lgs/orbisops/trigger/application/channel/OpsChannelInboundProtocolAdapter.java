package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelInboundProtocolPort;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelPayloadCipherPort;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelContentFingerprint;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAction;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAttachment;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelMessage;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelSignature;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class OpsChannelInboundProtocolAdapter implements ChannelInboundProtocolPort {

    private final OpsSecretResolver secrets;
    private final ChannelPayloadCipherPort cipher;
    private final ChannelOutboundContentPolicy contentPolicy;
    private final OpsChannelInboundProtocolSettings settings;

    public OpsChannelInboundProtocolAdapter(
            OpsSecretResolver secrets,
            ChannelPayloadCipherPort cipher,
            ChannelOutboundContentPolicy contentPolicy,
            long maxClockSkewSeconds,
            int maxMessageChars,
            int maxMetadataChars) {
        this(
                secrets,
                cipher,
                contentPolicy,
                new OpsChannelInboundProtocolSettings(
                        maxClockSkewSeconds,
                        maxMessageChars,
                        maxMetadataChars));
    }

    @Autowired
    public OpsChannelInboundProtocolAdapter(
            OpsSecretResolver secrets,
            ChannelPayloadCipherPort cipher,
            ChannelOutboundContentPolicy contentPolicy,
            OpsChannelInboundProtocolSettings settings) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (cipher == null) throw new IllegalArgumentException("CHANNEL_PAYLOAD_CIPHER_REQUIRED");
        if (contentPolicy == null) throw new IllegalArgumentException("CHANNEL_CONTENT_POLICY_REQUIRED");
        this.secrets = secrets;
        this.cipher = cipher;
        this.contentPolicy = contentPolicy;
        this.settings = settings == null
                ? OpsChannelInboundProtocolSettings.defaults()
                : settings;
    }

    @Override
    public VerifiedInbound verifyAndProtect(ChannelRecord channel,
                                            ChannelModels.InboundMessage message,
                                            String timestampHeader,
                                            String signature) {
        validate(message);
        long timestamp;
        try {
            timestamp = Long.parseLong(text(timestampHeader));
        } catch (RuntimeException failure) {
            throw new SecurityException("CHANNEL_TIMESTAMP_INVALID");
        }
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > settings.maxClockSkewSeconds()) {
            throw new SecurityException("CHANNEL_TIMESTAMP_EXPIRED");
        }
        if (message.timestamp() != timestamp) throw new SecurityException("CHANNEL_TIMESTAMP_MISMATCH");
        String secret = secrets.resolve(text(channel.credentialRef()));
        if (!StringUtils.hasText(secret)) throw new SecurityException("CHANNEL_CREDENTIAL_UNAVAILABLE");
        OpsChannelMessage protocolMessage = protocolMessage(message);
        if (!OpsChannelSignature.verify(secret, OpsChannelSignature.inboundPayload(protocolMessage, timestamp), signature)) {
            throw new SecurityException("CHANNEL_SIGNATURE_INVALID");
        }
        return protect(channel, message);
    }

    @Override
    public VerifiedInbound protectTrusted(ChannelRecord channel, ChannelModels.InboundMessage message) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        validate(message);
        return protect(channel, message);
    }

    private VerifiedInbound protect(ChannelRecord channel, ChannelModels.InboundMessage message) {
        if (cipher == null || !cipher.available()) {
            throw new IllegalStateException("CHANNEL_INBOUND_ENCRYPTION_UNAVAILABLE");
        }
        String protectedPayload = cipher.encrypt(JSON.toJSONString(payload(message)),
                aad(channel.channelId(), message.externalMessageId()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contentHash", ChannelContentFingerprint.sha256(message.text()));
        envelope.put("contentPreview", abbreviate(contentPolicy.sanitize(message.text()), 500));
        envelope.put("metadataKeys", message.metadata().keySet());
        envelope.put("messageType", message.messageType());
        envelope.put("attachmentCount", message.attachments().size());
        envelope.put("timestamp", message.timestamp());
        envelope.put("protectedPayload", protectedPayload);
        return new VerifiedInbound(JSON.toJSONString(envelope));
    }

    @Override
    public ChannelModels.InboundMessage restore(ChannelMessageRecord stored) {
        if (stored == null) throw new IllegalArgumentException("CHANNEL_STORED_MESSAGE_REQUIRED");
        if (!cipher.available()) throw new IllegalStateException("CHANNEL_INBOUND_ENCRYPTION_UNAVAILABLE");
        Map<String, Object> envelope = objectMap(JSON.parseObject(stored.payloadJson()));
        String protectedPayload = required(envelope.get("protectedPayload"), "CHANNEL_INBOUND_PROTECTED_PAYLOAD_MISSING");
        Map<String, Object> payload = objectMap(JSON.parseObject(cipher.decrypt(
                protectedPayload, aad(stored.channelId(), stored.externalMessageId()))));
        List<ChannelModels.Attachment> attachments = new ArrayList<>();
        if (payload.get("attachments") instanceof List<?> rows) {
            for (Object row : rows) {
                Map<String, Object> item = objectMap(row);
                attachments.add(new ChannelModels.Attachment(text(item.get("attachmentId")), text(item.get("fileName")),
                        text(item.get("mediaType")), longValue(item.get("sizeBytes")), text(item.get("contentRef")),
                        text(item.get("contentHash"))));
            }
        }
        ChannelModels.Action action = null;
        if (payload.get("action") instanceof Map<?, ?> rawAction) {
            Map<String, Object> item = objectMap(rawAction);
            action = new ChannelModels.Action(text(item.get("actionId")), text(item.get("actionType")),
                    text(item.get("value")), objectMap(item.get("parameters")));
        }
        ChannelModels.InboundMessage restored = new ChannelModels.InboundMessage(
                text(payload.get("externalMessageId")), text(payload.get("externalConversationId")),
                text(payload.get("senderId")), text(payload.get("text")), longValue(payload.get("timestamp")),
                objectMap(payload.get("metadata")), text(payload.get("messageType")), attachments, action);
        validate(restored);
        if (!stored.externalMessageId().equals(restored.externalMessageId())
                || !stored.externalConversationId().equals(restored.externalConversationId())
                || !stored.senderId().equals(restored.senderId())) {
            throw new SecurityException("CHANNEL_INBOUND_ENVELOPE_IDENTITY_MISMATCH");
        }
        return restored;
    }

    private void validate(ChannelModels.InboundMessage message) {
        if (message == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REQUIRED");
        bounded(message.externalMessageId(), 256, "CHANNEL_MESSAGE_ID_TOO_LONG");
        bounded(message.externalConversationId(), 256, "CHANNEL_CONVERSATION_ID_TOO_LONG");
        bounded(message.senderId(), 256, "CHANNEL_SENDER_ID_TOO_LONG");
        String type = text(message.messageType()).toUpperCase(Locale.ROOT);
        if (!List.of("TEXT", "ACTION").contains(type)) throw new IllegalArgumentException("CHANNEL_MESSAGE_TYPE_UNSUPPORTED");
        if ("TEXT".equals(type) && text(message.text()).isBlank()) throw new IllegalArgumentException("CHANNEL_MESSAGE_CONTENT_REQUIRED");
        if (text(message.text()).length() > settings.maxMessageChars()) throw new IllegalArgumentException("CHANNEL_INBOUND_MESSAGE_TOO_LARGE");
        if (message.attachments().size() > 8) throw new IllegalArgumentException("CHANNEL_TOO_MANY_ATTACHMENTS");
        message.attachments().forEach(this::validateAttachment);
        if ("ACTION".equals(type)) validateAction(message.action());
        if (JSON.toJSONString(message.metadata()).length() > settings.maxMetadataChars()) {
            throw new IllegalArgumentException("CHANNEL_INBOUND_METADATA_TOO_LARGE");
        }
    }

    private void validateAttachment(ChannelModels.Attachment value) {
        bounded(value.attachmentId(), 128, "CHANNEL_ATTACHMENT_ID_TOO_LONG");
        bounded(value.fileName(), 256, "CHANNEL_ATTACHMENT_NAME_TOO_LONG");
        bounded(value.mediaType(), 128, "CHANNEL_ATTACHMENT_MEDIA_TYPE_TOO_LONG");
        if (value.sizeBytes() > 50L * 1024 * 1024) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_OUT_OF_RANGE");
        String ref = bounded(value.contentRef(), 512, "CHANNEL_ATTACHMENT_CONTENT_REF_TOO_LONG");
        if (!(ref.startsWith("bridge://") || ref.startsWith("object://"))) {
            throw new IllegalArgumentException("CHANNEL_ATTACHMENT_CONTENT_REF_UNTRUSTED");
        }
        if (!bounded(value.contentHash(), 128, "CHANNEL_ATTACHMENT_HASH_TOO_LONG").matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CHANNEL_ATTACHMENT_HASH_INVALID");
        }
    }

    private void validateAction(ChannelModels.Action action) {
        if (action == null) throw new IllegalArgumentException("CHANNEL_ACTION_REQUIRED");
        bounded(action.actionId(), 128, "CHANNEL_ACTION_ID_TOO_LONG");
        bounded(action.actionType(), 64, "CHANNEL_ACTION_TYPE_TOO_LONG");
        if (action.value().length() > 2000) throw new IllegalArgumentException("CHANNEL_ACTION_VALUE_TOO_LONG");
        if (JSON.toJSONString(action.parameters()).length() > settings.maxMetadataChars()) {
            throw new IllegalArgumentException("CHANNEL_ACTION_PARAMETERS_TOO_LARGE");
        }
    }

    private Map<String, Object> payload(ChannelModels.InboundMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalMessageId", message.externalMessageId());
        payload.put("externalConversationId", message.externalConversationId());
        payload.put("senderId", message.senderId());
        payload.put("text", message.text());
        payload.put("timestamp", message.timestamp());
        payload.put("metadata", message.metadata());
        payload.put("messageType", message.messageType());
        payload.put("attachments", message.attachments());
        payload.put("action", message.action());
        return payload;
    }

    private OpsChannelMessage protocolMessage(ChannelModels.InboundMessage source) {
        List<OpsChannelAttachment> attachments = source.attachments().stream()
                .map(item -> new OpsChannelAttachment(item.attachmentId(), item.fileName(), item.mediaType(),
                        item.sizeBytes(), item.contentRef(), item.contentHash())).toList();
        OpsChannelAction action = source.action() == null ? null : new OpsChannelAction(source.action().actionId(),
                source.action().actionType(), source.action().value(), source.action().parameters());
        return new OpsChannelMessage(source.externalMessageId(), source.externalConversationId(), source.senderId(),
                source.text(), source.timestamp(), source.metadata(), source.messageType(), attachments, action);
    }

    private String aad(String channelId, String messageId) { return text(channelId) + "\n" + text(messageId); }
    private String bounded(String value, int max, String reasonCode) {
        String normalized = required(value, reasonCode);
        if (normalized.length() > max) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private long longValue(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(text(value)); }
    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "..."; }
}
