package cn.lgs.orbisops.trigger.ops.channel.slack;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.socket_mode.SocketModeClient;
import com.slack.api.socket_mode.response.AckResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class OpsSlackChannelConnectionDriver implements ChannelConnectionDriver<OpsSlackChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final ReceiveChannelMessageUseCase inbound;
    private final OpsChannelInteractiveActionDispatcher interactiveActions;
    private final OpsSlackAttachmentService attachments;
    private final OpsSlackProtocolCodec codec = new OpsSlackProtocolCodec();
    private final Map<String, SocketModeClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ChannelHealthSnapshot> health = new ConcurrentHashMap<>();

    public OpsSlackChannelConnectionDriver(OpsSecretResolver secrets,
                                           ReceiveChannelMessageUseCase inbound,
                                           OpsChannelInteractiveActionDispatcher interactiveActions,
                                           OpsSlackAttachmentService attachments) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        if (interactiveActions == null) throw new IllegalArgumentException("CHANNEL_INTERACTIVE_ACTION_DISPATCHER_REQUIRED");
        if (attachments == null) throw new IllegalArgumentException("SLACK_ATTACHMENT_SERVICE_REQUIRED");
        this.secrets = secrets;
        this.inbound = inbound;
        this.interactiveActions = interactiveActions;
        this.attachments = attachments;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SLACK;
    }

    @Override
    public synchronized void start(OpsSlackChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (clients.containsKey(configuration.channelId())) return;
        String appToken = secrets.resolve(configuration.appCredentialRef());
        String botToken = secrets.resolve(configuration.credentialRef());
        if (blank(appToken) || blank(botToken)) {
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "SLACK_CREDENTIAL_UNAVAILABLE", "Slack app/bot token reference could not be resolved"));
            return;
        }
        try {
            SocketModeClient client = Slack.getInstance().socketMode(appToken, SocketModeClient.Backend.JavaWebSocket);
            client.addWebSocketMessageListener(message -> onRaw(configuration, client, message));
            client.addWebSocketErrorListener(reason -> health.put(configuration.channelId(),
                    snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                            "SLACK_SOCKET_ERROR", safe(reason == null ? null : reason.getMessage()))));
            clients.put(configuration.channelId(), client);
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "SLACK_CONNECTING", "Slack Socket Mode connection is being established"));
            client.connect();
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                    "SLACK_SOCKET_CONNECTED", "Authenticated Slack Socket Mode connection is active"));
        } catch (Exception failure) {
            SocketModeClient client = clients.remove(configuration.channelId());
            closeQuietly(client);
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "SLACK_CONNECTION_FAILED", safe(failure.getMessage())));
        }
    }

    @Override
    public synchronized void stop(String channelId) {
        SocketModeClient client = clients.remove(channelId);
        closeQuietly(client);
        health.put(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "SLACK_CONNECTION_STOPPED", "Slack Socket Mode connection stopped"));
    }

    @Override
    public ChannelHealthSnapshot health(String channelId) {
        return health.getOrDefault(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "SLACK_CONNECTION_NOT_STARTED", "Slack connection has not been started"));
    }

    public ChannelDeliveryReceipt send(OpsSlackChannelConfiguration configuration, ChannelOutboundMessage message) {
        String botToken = resolved(configuration.credentialRef(), "SLACK_BOT_TOKEN_UNAVAILABLE");
        try {
            MethodsClient methods = Slack.getInstance().methods(botToken);
            String markdown = codec.markdown(message.content());
            List<LayoutBlock> blocks = codec.blocks(message.content());
            ChatPostMessageResponse response = methods.chatPostMessage(request -> {
                request.channel(message.conversation().externalConversationId()).text(markdown);
                if (!blocks.isEmpty()) request.blocks(blocks);
                if (!message.metadata().replyToMessageId().isBlank()) {
                    request.threadTs(message.metadata().replyToMessageId());
                }
                return request;
            });
            if (response == null || !response.isOk()) {
                throw new IllegalStateException("SLACK_API_REJECTED:" + safe(response == null ? null : response.getError()));
            }
            return new ChannelDeliveryReceipt(true, "DELIVERED", safe(response.getTs()), null, Instant.now());
        } catch (Exception failure) {
            throw new IllegalStateException("SLACK_SEND_FAILED:" + safe(failure.getMessage()), failure);
        }
    }

    public ChannelDeliveryReceipt update(OpsSlackChannelConfiguration configuration,
                                         ChannelMessageRef existingMessage,
                                         ChannelOutboundMessage message) {
        String botToken = resolved(configuration.credentialRef(), "SLACK_BOT_TOKEN_UNAVAILABLE");
        try {
            MethodsClient methods = Slack.getInstance().methods(botToken);
            String markdown = codec.markdown(message.content());
            List<LayoutBlock> blocks = codec.blocks(message.content());
            ChatUpdateResponse response = methods.chatUpdate(request -> {
                request.channel(existingMessage.conversation().externalConversationId())
                        .ts(existingMessage.externalMessageId())
                        .text(markdown);
                if (!blocks.isEmpty()) request.blocks(blocks);
                return request;
            });
            if (response == null || !response.isOk()) {
                throw new IllegalStateException("SLACK_API_REJECTED:" + safe(response == null ? null : response.getError()));
            }
            return new ChannelDeliveryReceipt(true, "UPDATED", existingMessage.externalMessageId(), null, Instant.now());
        } catch (Exception failure) {
            throw new IllegalStateException("SLACK_UPDATE_FAILED:" + safe(failure.getMessage()), failure);
        }
    }

    private void onRaw(OpsSlackChannelConfiguration configuration, SocketModeClient client, String raw) {
        OpsSlackProtocolCodec.DecodedEnvelope decoded = codec.decode(configuration, raw);
        if (decoded.inbound() != null) {
            ChannelInboundEnvelope envelope = decoded.inbound();
            if (!decoded.files().isEmpty()) {
                envelope = new ChannelInboundEnvelope(
                        envelope.message(), envelope.sender(), envelope.content(),
                        attachments.ingest(configuration, decoded.files()),
                        envelope.receivedAt(), envelope.idempotencyKey());
            }
            inbound.receiveProvider(configuration.channelId(), envelope);
        }
        if (decoded.interactive() != null) {
            dispatchInteractive(configuration, decoded.interactive());
        }
        if (!decoded.envelopeId().isBlank()) {
            try {
                client.sendSocketModeResponse(AckResponse.builder().envelopeId(decoded.envelopeId()).build());
            } catch (Exception ignored) {
                // Slack retries unacked envelopes; provider message/action idempotency remains authoritative downstream.
            }
        }
    }

    private void dispatchInteractive(OpsSlackChannelConfiguration configuration,
                                     cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope actionEnvelope) {
        try {
            var outcome = interactiveActions.tryExecute(configuration.channelId(), actionEnvelope);
            if (outcome.isEmpty() || !outcome.get().terminal()) return;
            ChannelMessageRef message = actionEnvelope.message();
            update(configuration, message,
                    new ChannelOutboundMessage(message.conversation(),
                            ChannelRichContent.text(outcome.get().presentation()), List.of(),
                            cn.lgs.orbisops.application.channel.ChannelOutboundMetadata.from(null)));
        } catch (RuntimeException denied) {
            // Unauthorized/duplicate clicks must leave a shared approval message intact for valid approvers.
        }
    }

    private String resolved(String reference, String reasonCode) {
        String value = secrets.resolve(reference);
        if (blank(value)) throw new IllegalStateException(reasonCode);
        return value;
    }

    private void closeQuietly(SocketModeClient client) {
        if (client == null) return;
        try {
            client.disconnect();
        } catch (Exception ignored) {
            // best effort provider shutdown
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // best effort provider shutdown
        }
    }

    private ChannelHealthSnapshot snapshot(ChannelHealthSnapshot.HealthStatus status, String reasonCode, String detail) {
        return new ChannelHealthSnapshot(status, reasonCode, detail, Instant.now());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
