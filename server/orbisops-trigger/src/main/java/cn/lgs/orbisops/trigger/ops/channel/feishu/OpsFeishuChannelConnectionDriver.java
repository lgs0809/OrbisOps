package cn.lgs.orbisops.trigger.ops.channel.feishu;

import cn.lgs.orbisops.application.channel.ChannelAttachmentAssetPort;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelAttachment;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.lark.oapi.channel.ChannelEventHandler;
import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.LarkChannelFactory;
import com.lark.oapi.channel.config.LarkChannelOptions;
import com.lark.oapi.channel.model.CardActionEvent;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.channel.model.ResourceDescriptor;
import com.lark.oapi.channel.model.SendInput;
import com.lark.oapi.channel.model.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Owns Feishu WebSocket lifecycle. Application services never manage provider threads. */
@Component
public final class OpsFeishuChannelConnectionDriver implements ChannelConnectionDriver<OpsFeishuChannelConfiguration> {

    private static final long MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 8;

    private final OpsSecretResolver secrets;
    private final ReceiveChannelMessageUseCase inbound;
    private final OpsChannelInteractiveActionDispatcher interactiveActions;
    private final ChannelAttachmentAssetPort attachmentAssets;
    private final OpsFeishuApprovalCardCodec cards = new OpsFeishuApprovalCardCodec();
    private final Map<String, LarkChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, ChannelHealthSnapshot> health = new ConcurrentHashMap<>();

    public OpsFeishuChannelConnectionDriver(OpsSecretResolver secrets,
                                            ReceiveChannelMessageUseCase inbound,
                                            OpsChannelInteractiveActionDispatcher interactiveActions,
                                            ChannelAttachmentAssetPort attachmentAssets) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        if (interactiveActions == null) throw new IllegalArgumentException("CHANNEL_INTERACTIVE_ACTION_DISPATCHER_REQUIRED");
        if (attachmentAssets == null) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_ASSET_PORT_REQUIRED");
        this.secrets = secrets;
        this.inbound = inbound;
        this.interactiveActions = interactiveActions;
        this.attachmentAssets = attachmentAssets;
    }

    @Override
    public ChannelType type() {
        return ChannelType.FEISHU;
    }

    @Override
    public synchronized void start(OpsFeishuChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (configuration.connectionMode() != cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode.LONG_CONNECTION
                && configuration.connectionMode() != cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode.HYBRID) {
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                    "FEISHU_LONG_CONNECTION_DISABLED", "Channel is configured for webhook transport"));
            return;
        }
        if (channels.containsKey(configuration.channelId())) return;
        String appSecret = secrets.resolve(configuration.credentialRef());
        if (appSecret == null || appSecret.isBlank()) {
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "FEISHU_APP_SECRET_UNAVAILABLE", "Credential reference could not be resolved"));
            return;
        }
        try {
            LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
            policy.setRequireMention(configuration.requireMention());
            policy.setRespondToMentionAll(configuration.respondToMentionAll());
            LarkChannel channel = LarkChannelFactory.createLarkChannel(
                    LarkChannelOptions.newBuilder(configuration.appId(), appSecret)
                            .transport("websocket")
                            .policy(policy)
                            .source("orbisops")
                            .build());
            channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
                @Override
                public void handle(NormalizedMessage message) {
                    onMessage(configuration, channel, message);
                }
            });
            channel.on("cardAction", new ChannelEventHandler<CardActionEvent>() {
                @Override
                public void handle(CardActionEvent event) {
                    onCardAction(configuration.channelId(), channel, event);
                }
            });
            channels.put(configuration.channelId(), channel);
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "FEISHU_CONNECTING", "WebSocket connection is being established"));
            channel.connect().whenComplete((identity, failure) -> {
                if (failure != null) {
                    channels.remove(configuration.channelId(), channel);
                    health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                            "FEISHU_CONNECTION_FAILED", safe(failure.getMessage())));
                    return;
                }
                health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                        "FEISHU_WEBSOCKET_CONNECTED", "Authenticated Feishu WebSocket connection is active"));
            });
        } catch (RuntimeException failure) {
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "FEISHU_CONNECTION_FAILED", safe(failure.getMessage())));
        }
    }

    @Override
    public synchronized void stop(String channelId) {
        LarkChannel channel = channels.remove(channelId);
        if (channel != null) {
            try {
                channel.disconnect().get();
            } catch (Exception ignored) {
                // Health is authoritative; provider shutdown failure must not stop global channel supervision.
            }
        }
        health.put(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "FEISHU_CONNECTION_STOPPED", "Feishu connection stopped"));
    }

    @Override
    public ChannelHealthSnapshot health(String channelId) {
        return health.getOrDefault(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "FEISHU_CONNECTION_NOT_STARTED", "Feishu connection has not been started"));
    }

    public ChannelDeliveryReceipt send(OpsFeishuChannelConfiguration configuration, ChannelOutboundMessage message) {
        LarkChannel channel = readyChannel(configuration);
        try {
            ChannelRichContent content = message.content();
            SendInput input;
            if (!content.actions().isEmpty()) {
                input = SendInput.card(cards.approvalCard(content));
            } else {
                input = content.markdown().isBlank()
                        ? SendInput.text(content.plainText())
                        : SendInput.markdown(content.markdown());
            }
            SendResult result = channel.sendSync(message.conversation().externalConversationId(), input);
            return new ChannelDeliveryReceipt(true, "DELIVERED",
                    result == null ? "" : safe(result.getMessageId()), null, Instant.now());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("FEISHU_SEND_FAILED:" + safe(failure.getMessage()), failure);
        }
    }

    public ChannelDeliveryReceipt update(OpsFeishuChannelConfiguration configuration,
                                         ChannelMessageRef existingMessage,
                                         ChannelOutboundMessage message) {
        LarkChannel channel = readyChannel(configuration);
        try {
            String content = message.content().markdown().isBlank()
                    ? message.content().plainText()
                    : message.content().markdown();
            channel.editMessage(existingMessage.externalMessageId(), content).get();
            return new ChannelDeliveryReceipt(true, "UPDATED", existingMessage.externalMessageId(), null, Instant.now());
        } catch (Exception failure) {
            throw new IllegalStateException("FEISHU_UPDATE_FAILED:" + safe(failure.getMessage()), failure);
        }
    }

    private LarkChannel readyChannel(OpsFeishuChannelConfiguration configuration) {
        if (!channels.containsKey(configuration.channelId())) start(configuration);
        ChannelHealthSnapshot current = health(configuration.channelId());
        LarkChannel channel = channels.get(configuration.channelId());
        if (channel == null || current.status() != ChannelHealthSnapshot.HealthStatus.READY) {
            throw new IllegalStateException("FEISHU_CHANNEL_NOT_READY:" + current.reasonCode());
        }
        return channel;
    }

    private void onMessage(OpsFeishuChannelConfiguration configuration,
                           LarkChannel channel,
                           NormalizedMessage message) {
        if (message == null || safe(message.getMessageId()).isBlank() || safe(message.getSenderId()).isBlank()) return;
        String content = safe(message.getContent());
        List<ResourceDescriptor> resources = message.getResources() == null ? List.of() : List.copyOf(message.getResources());
        if (resources.size() > MAX_ATTACHMENTS) throw new IllegalArgumentException("CHANNEL_TOO_MANY_ATTACHMENTS");
        List<ChannelAttachment> attachments = downloadAttachments(configuration, channel, message.getMessageId(), resources);
        if (content.isBlank() && !attachments.isEmpty()) {
            content = attachmentSummary(attachments);
        }
        if (content.isBlank()) return;
        long created = message.getCreateTime();
        Instant receivedAt = created > 0 ? Instant.ofEpochMilli(created) : Instant.now();
        ChannelConversationRef.ConversationKind kind = "p2p".equalsIgnoreCase(safe(message.getChatType()))
                ? ChannelConversationRef.ConversationKind.DIRECT
                : ChannelConversationRef.ConversationKind.GROUP;
        ChannelConversationRef conversation = new ChannelConversationRef(message.getChatId(), kind);
        ChannelInboundEnvelope envelope = new ChannelInboundEnvelope(
                new ChannelMessageRef(message.getMessageId(), conversation),
                new ChannelExternalPrincipal(message.getSenderId(), message.getSenderName(),
                        ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(content),
                attachments,
                receivedAt,
                message.getMessageId());
        inbound.receiveProvider(configuration.channelId(), envelope);
    }

    List<ChannelAttachment> downloadAttachments(OpsFeishuChannelConfiguration configuration,
                                                        LarkChannel channel,
                                                        String messageId,
                                                        List<ResourceDescriptor> resources) {
        if (resources == null || resources.isEmpty()) return List.of();
        List<ChannelAttachment> result = new ArrayList<>();
        for (int index = 0; index < resources.size(); index++) {
            ResourceDescriptor resource = resources.get(index);
            if (resource == null || safe(resource.getFileKey()).isBlank()) continue;
            String type = safe(resource.getType());
            if (type.isBlank()) type = "file";
            byte[] bytes;
            try {
                bytes = channel.downloadResource(resource.getFileKey(), type).join();
            } catch (RuntimeException failure) {
                throw new IllegalStateException("FEISHU_ATTACHMENT_DOWNLOAD_FAILED:" + safe(failure.getMessage()), failure);
            }
            byte[] binary = bytes == null ? new byte[0] : bytes;
            if (binary.length > MAX_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_OUT_OF_RANGE");
            }
            String attachmentId = safe(messageId) + ":" + index;
            String fileName = "feishu-" + type + "-" + (index + 1) + extension(type);
            String mediaType = mediaType(type);
            ChannelAttachmentAssetPort.StoredAttachment stored = attachmentAssets.store(
                    new ChannelAttachmentAssetPort.StoreAttachment(
                            configuration.projectId(), configuration.channelId(), attachmentId,
                            fileName, mediaType, binary));
            result.add(new ChannelAttachment(
                    attachmentId, fileName, mediaType, stored.sizeBytes(), stored.contentRef(), stored.contentHash()));
        }
        return List.copyOf(result);
    }

    String attachmentSummary(List<ChannelAttachment> attachments) {
        String joined = attachments.stream().limit(8).map(ChannelAttachment::fileName)
                .reduce((left, right) -> left + ", " + right).orElse("attachment");
        return "Shared attachment" + (attachments.size() == 1 ? ": " : "s: ") + joined;
    }

    private String mediaType(String type) {
        return switch (safe(type).toLowerCase()) {
            case "image" -> "image/*";
            case "audio" -> "audio/*";
            case "media", "video" -> "video/*";
            default -> "application/octet-stream";
        };
    }

    private String extension(String type) {
        return switch (safe(type).toLowerCase()) {
            case "image" -> ".image";
            case "audio" -> ".audio";
            case "media", "video" -> ".video";
            default -> ".bin";
        };
    }

    private void onCardAction(String channelId, LarkChannel channel, CardActionEvent event) {
        if (event == null) return;
        Map<String, Object> value = event.getActionValue();
        String actionKey = value == null ? "" : safe(value.get("actionKey"));
        String externalPrincipalId = safe(event.getOperatorId());
        if (actionKey.isBlank() || externalPrincipalId.isBlank()) return;
        ChannelConversationRef conversation = new ChannelConversationRef(
                safe(event.getChatId()), ChannelConversationRef.ConversationKind.UNKNOWN);
        ChannelInteractiveActionEnvelope envelope = new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("feishu-card-action", "Approval Action", actionKey,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal(externalPrincipalId, "", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef(safe(event.getMessageId()), conversation),
                Instant.now(),
                safe(event.getMessageId()) + ":" + externalPrincipalId + ":" + actionKey);
        try {
            var outcome = interactiveActions.tryExecute(channelId, envelope);
            if (outcome.isEmpty() || !outcome.get().terminal()) return;
            channel.updateCard(event.getMessageId(), cards.resolvedCard(outcome.get().presentation()));
        } catch (RuntimeException denied) {
            // Unauthorized/duplicate clicks must not destroy a shared approval card for other valid approvers.
        }
    }

    private ChannelHealthSnapshot snapshot(ChannelHealthSnapshot.HealthStatus status, String reasonCode, String detail) {
        return new ChannelHealthSnapshot(status, reasonCode, detail, Instant.now());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
