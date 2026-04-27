package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Owns DingTalk Stream lifecycle. Business routing stays in the shared Channel application layer. */
@Component
public final class OpsDingTalkChannelConnectionDriver implements ChannelConnectionDriver<OpsDingTalkChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final ReceiveChannelMessageUseCase inbound;
    private final OpsDingTalkProactiveMessageClient proactive;
    private final OpsDingTalkCardClient cards;
    private final OpsChannelInteractiveActionDispatcher interactiveActions;
    private final OpsDingTalkProtocolCodec codec = new OpsDingTalkProtocolCodec();
    private final OpsDingTalkCardProtocolCodec cardCodec = new OpsDingTalkCardProtocolCodec();
    private final Map<String, OpenDingTalkClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ChannelHealthSnapshot> health = new ConcurrentHashMap<>();
    private final Map<String, ReplyContext> replyContexts = new ConcurrentHashMap<>();

    public OpsDingTalkChannelConnectionDriver(OpsSecretResolver secrets,
                                              ReceiveChannelMessageUseCase inbound,
                                              OpsDingTalkProactiveMessageClient proactive,
                                              OpsDingTalkCardClient cards,
                                              OpsChannelInteractiveActionDispatcher interactiveActions) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        if (proactive == null) throw new IllegalArgumentException("DINGTALK_PROACTIVE_CLIENT_REQUIRED");
        if (cards == null) throw new IllegalArgumentException("DINGTALK_CARD_CLIENT_REQUIRED");
        if (interactiveActions == null) throw new IllegalArgumentException("CHANNEL_INTERACTIVE_ACTION_DISPATCHER_REQUIRED");
        this.secrets = secrets;
        this.inbound = inbound;
        this.proactive = proactive;
        this.cards = cards;
        this.interactiveActions = interactiveActions;
    }

    @Override
    public ChannelType type() {
        return ChannelType.DINGTALK;
    }

    @Override
    public synchronized void start(OpsDingTalkChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (clients.containsKey(configuration.channelId())) return;
        String clientSecret = secrets.resolve(configuration.credentialRef());
        if (clientSecret == null || clientSecret.isBlank()) {
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "DINGTALK_CLIENT_SECRET_UNAVAILABLE", "Client Secret reference could not be resolved"));
            return;
        }
        try {
            OpenDingTalkCallbackListener<ChatbotMessage, Void> listener = new OpenDingTalkCallbackListener<>() {
                @Override
                public Void execute(ChatbotMessage message) {
                    onMessage(configuration, message);
                    return null;
                }
            };
            OpenDingTalkCallbackListener<String, JSONObject> cardListener = new OpenDingTalkCallbackListener<>() {
                @Override
                public JSONObject execute(String messageString) {
                    return onCardCallback(configuration, messageString);
                }
            };
            OpenDingTalkClient client = OpenDingTalkStreamClientBuilder.custom()
                    .credential(new AuthClientCredential(configuration.clientId(), clientSecret))
                    .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, listener)
                    .registerCallbackListener("/v1.0/card/instances/callback", cardListener)
                    .build();
            clients.put(configuration.channelId(), client);
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "DINGTALK_CONNECTING", "DingTalk Stream connection is being established"));
            client.start();
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                    "DINGTALK_STREAM_CONNECTED", "Authenticated DingTalk Stream client is active"));
        } catch (Exception failure) {
            clients.remove(configuration.channelId());
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "DINGTALK_CONNECTION_FAILED", safe(failure.getMessage())));
        }
    }

    @Override
    public synchronized void stop(String channelId) {
        OpenDingTalkClient client = clients.remove(channelId);
        if (client != null) {
            try {
                client.stop();
            } catch (Exception ignored) {
                // best effort provider shutdown
            }
        }
        replyContexts.keySet().removeIf(key -> key.startsWith(channelId + "\n"));
        health.put(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "DINGTALK_CONNECTION_STOPPED", "DingTalk Stream connection stopped"));
    }

    @Override
    public ChannelHealthSnapshot health(String channelId) {
        return health.getOrDefault(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "DINGTALK_CONNECTION_NOT_STARTED", "DingTalk Stream connection has not been started"));
    }

    public ChannelDeliveryReceipt send(OpsDingTalkChannelConfiguration configuration,
                                       ChannelOutboundMessage message) {
        try {
            return cards.create(configuration, message);
        } catch (RuntimeException cardFailure) {
            // Text delivery remains a safe degradation path; interactive messages must never silently lose actions.
            if (!message.content().actions().isEmpty()) throw cardFailure;
        }
        String key = replyKey(configuration.channelId(), message.conversation().externalConversationId());
        ReplyContext context = replyContexts.get(key);
        if (context != null && !context.expired()) {
            try {
                String markdown = markdown(message.content());
                BotReplier.fromWebhook(context.sessionWebhook())
                        .replyMarkdown("OrbisOps", markdown, context.atUserId().isBlank() ? List.of() : List.of(context.atUserId()));
                return new ChannelDeliveryReceipt(true, "DELIVERED", "", null, Instant.now());
            } catch (Exception sessionReplyFailure) {
                // Durable delivery must not depend on the short-lived session webhook.
            }
        } else {
            replyContexts.remove(key);
        }
        return proactive.send(configuration, message);
    }

    public ChannelDeliveryReceipt update(OpsDingTalkChannelConfiguration configuration,
                                         ChannelMessageRef existingMessage,
                                         ChannelOutboundMessage message) {
        return cards.update(configuration, existingMessage, message);
    }

    private void onMessage(OpsDingTalkChannelConfiguration configuration, ChatbotMessage message) {
        OpsDingTalkProtocolCodec.DecodedMessage decoded = codec.decode(message);
        if (decoded.envelope() == null) return;
        if (decoded.hasReplyContext()) {
            String conversationId = decoded.envelope().message().conversation().externalConversationId();
            replyContexts.put(replyKey(configuration.channelId(), conversationId),
                    new ReplyContext(decoded.sessionWebhook(), decoded.sessionWebhookExpiresAt(), decoded.atUserId()));
        }
        inbound.receiveProvider(configuration.channelId(), decoded.envelope());
    }

    private JSONObject onCardCallback(OpsDingTalkChannelConfiguration configuration, String messageString) {
        OpsDingTalkCardProtocolCodec.DecodedAction decoded = cardCodec.decodeCallback(messageString);
        if (decoded.envelope() == null) return new JSONObject();
        try {
            var outcome = interactiveActions.tryExecute(configuration.channelId(), decoded.envelope());
            if (outcome.isEmpty() || !outcome.get().terminal()) return new JSONObject();
            return cardCodec.callbackUpdateResponse(outcome.get().presentation());
        } catch (RuntimeException denied) {
            // Unauthorized/duplicate clicks must leave a shared approval card unchanged for other valid approvers.
            return new JSONObject();
        }
    }

    private String markdown(ChannelRichContent content) {
        if (content == null) return "";
        return content.markdown().isBlank() ? content.plainText() : content.markdown();
    }

    private String prefixed(String kind, String value) {
        String normalized = safe(value);
        return normalized.isBlank() ? "" : kind + ":" + normalized;
    }

    private String replyKey(String channelId, String conversationId) {
        return safe(channelId) + "\n" + safe(conversationId);
    }

    private ChannelHealthSnapshot snapshot(ChannelHealthSnapshot.HealthStatus status, String reasonCode, String detail) {
        return new ChannelHealthSnapshot(status, reasonCode, detail, Instant.now());
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ReplyContext(String sessionWebhook, Instant expiresAt, String atUserId) {
        private boolean expired() {
            return expiresAt == null || !Instant.now().isBefore(expiresAt);
        }
    }
}
