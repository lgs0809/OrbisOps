package cn.lgs.orbisops.trigger.ops.channel.wecom;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Native WeCom intelligent-bot WebSocket driver. Provider threads and reconnects stay outside Application. */
@Component
public final class OpsWeComChannelConnectionDriver implements ChannelConnectionDriver<OpsWeComChannelConfiguration> {

    static final URI DEFAULT_ENDPOINT = URI.create("wss://openws.work.weixin.qq.com");
    private static final long HEARTBEAT_SECONDS = 30L;
    private static final long REQUEST_TIMEOUT_SECONDS = 10L;

    private final OpsSecretResolver secrets;
    private final ReceiveChannelMessageUseCase inbound;
    private final OpsChannelInteractiveActionDispatcher interactiveActions;
    private final HttpClient httpClient;
    private final OpsWeComProtocolCodec codec;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ConnectionState> states = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<OpsWeComProtocolCodec.Ack>> pending = new ConcurrentHashMap<>();
    private final Map<String, ReplyContext> replyContexts = new ConcurrentHashMap<>();

    @Autowired
    public OpsWeComChannelConnectionDriver(OpsSecretResolver secrets,
                                           ReceiveChannelMessageUseCase inbound,
                                           OpsChannelInteractiveActionDispatcher interactiveActions) {
        this(secrets, inbound, interactiveActions,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new OpsWeComProtocolCodec(),
                daemonScheduler());
    }

    OpsWeComChannelConnectionDriver(OpsSecretResolver secrets,
                                    ReceiveChannelMessageUseCase inbound,
                                    OpsChannelInteractiveActionDispatcher interactiveActions,
                                    HttpClient httpClient,
                                    OpsWeComProtocolCodec codec,
                                    ScheduledExecutorService scheduler) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        if (interactiveActions == null) throw new IllegalArgumentException("CHANNEL_INTERACTIVE_ACTION_DISPATCHER_REQUIRED");
        if (httpClient == null) throw new IllegalArgumentException("WECOM_HTTP_CLIENT_REQUIRED");
        if (codec == null) throw new IllegalArgumentException("WECOM_PROTOCOL_CODEC_REQUIRED");
        if (scheduler == null) throw new IllegalArgumentException("WECOM_SCHEDULER_REQUIRED");
        this.secrets = secrets;
        this.inbound = inbound;
        this.interactiveActions = interactiveActions;
        this.httpClient = httpClient;
        this.codec = codec;
        this.scheduler = scheduler;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WECOM;
    }

    @Override
    public synchronized void start(OpsWeComChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (configuration.connectionMode() != cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode.LONG_CONNECTION
                && configuration.connectionMode() != cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode.HYBRID) {
            states.compute(configuration.channelId(), (id, state) -> stoppedState(configuration,
                    "WECOM_LONG_CONNECTION_DISABLED", "Channel is not configured for long connection"));
            return;
        }
        ConnectionState existing = states.get(configuration.channelId());
        if (existing != null && !existing.stopped && (existing.webSocket != null || existing.connecting)) return;
        String secret = secrets.resolve(configuration.credentialRef());
        if (secret == null || secret.isBlank()) {
            ConnectionState blocked = new ConnectionState(configuration);
            blocked.stopped = true;
            blocked.health = snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "WECOM_BOT_SECRET_UNAVAILABLE", "Credential reference could not be resolved");
            states.put(configuration.channelId(), blocked);
            return;
        }
        ConnectionState state = existing == null ? new ConnectionState(configuration) : existing.reset(configuration);
        state.connecting = true;
        state.stopped = false;
        state.secret = secret;
        state.health = snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN, "WECOM_CONNECTING", "Opening WeCom WebSocket");
        states.put(configuration.channelId(), state);
        connect(state);
    }

    @Override
    public synchronized void stop(String channelId) {
        ConnectionState state = states.get(channelId);
        if (state == null) return;
        state.stopped = true;
        state.connecting = false;
        cancelHeartbeat(state);
        WebSocket socket = state.webSocket;
        state.webSocket = null;
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "orbisops-stop");
        state.health = snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "WECOM_CONNECTION_STOPPED", "WeCom connection stopped");
    }

    @Override
    public ChannelHealthSnapshot health(String channelId) {
        ConnectionState state = states.get(channelId);
        return state == null
                ? snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "WECOM_CONNECTION_NOT_STARTED", "WeCom connection has not been started")
                : state.health;
    }

    public ChannelDeliveryReceipt send(OpsWeComChannelConfiguration configuration, ChannelOutboundMessage message) {
        ConnectionState state = ready(configuration);
        String content = message.content().markdown().isBlank()
                ? message.content().plainText()
                : message.content().markdown();
        if (!message.content().actions().isEmpty()) {
            String requestId = requestId("card");
            sendAwaitingAck(state, requestId, codec.approvalCard(
                    requestId,
                    message.conversation().externalConversationId(),
                    "OrbisOps Approval Required",
                    content,
                    message.content().actions()));
            return new ChannelDeliveryReceipt(true, "DELIVERED", "", null, Instant.now());
        }
        String replyTo = message.metadata().replyToMessageId();
        ReplyContext reply = replyTo.isBlank() ? null : replyContexts.get(replyTo);
        if (reply != null && !reply.requestId.isBlank()) {
            try {
                sendAwaitingAck(state, reply.requestId, codec.replyMarkdown(reply.requestId, content));
                return new ChannelDeliveryReceipt(true, "REPLIED", "", null, Instant.now());
            } catch (RuntimeException ignored) {
                // Reply windows are provider-controlled; fall back to a proactive push on the same conversation.
            }
        }
        String requestId = requestId("send");
        sendAwaitingAck(state, requestId,
                codec.proactiveMarkdown(requestId, message.conversation().externalConversationId(), content));
        return new ChannelDeliveryReceipt(true, "DELIVERED", "", null, Instant.now());
    }

    public ChannelDeliveryReceipt updateCard(OpsWeComChannelConfiguration configuration,
                                             String externalMessageId,
                                             String title) {
        ConnectionState state = ready(configuration);
        ReplyContext context = replyContexts.get(externalMessageId);
        if (context == null || context.requestId.isBlank() || context.taskId.isBlank()) {
            throw new IllegalStateException("WECOM_CARD_UPDATE_CONTEXT_UNAVAILABLE");
        }
        sendAwaitingAck(state, context.requestId, codec.updateCard(context.requestId, context.taskId, title));
        return new ChannelDeliveryReceipt(true, "UPDATED", externalMessageId, null, Instant.now());
    }

    private void connect(ConnectionState state) {
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(DEFAULT_ENDPOINT, new Listener(state))
                .whenComplete((socket, failure) -> {
                    if (failure != null) connectionFailed(state, "WECOM_CONNECTION_FAILED", failure.getMessage());
                });
    }

    private ConnectionState ready(OpsWeComChannelConfiguration configuration) {
        ConnectionState state = states.get(configuration.channelId());
        if (state == null || state.health.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            start(configuration);
            state = states.get(configuration.channelId());
        }
        if (state == null || state.webSocket == null || state.health.status() != ChannelHealthSnapshot.HealthStatus.READY) {
            ChannelHealthSnapshot current = state == null ? health(configuration.channelId()) : state.health;
            throw new IllegalStateException("WECOM_CHANNEL_NOT_READY:" + current.reasonCode());
        }
        return state;
    }

    private void sendAwaitingAck(ConnectionState state, String requestId, String frame) {
        CompletableFuture<OpsWeComProtocolCodec.Ack> future = new CompletableFuture<>();
        CompletableFuture<OpsWeComProtocolCodec.Ack> previous = pending.putIfAbsent(requestId, future);
        if (previous != null) throw new IllegalStateException("WECOM_REQUEST_ALREADY_PENDING");
        try {
            state.webSocket.sendText(frame, true).join();
            OpsWeComProtocolCodec.Ack ack = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!ack.success()) throw new IllegalStateException("WECOM_PROVIDER_REJECTED:" + safe(ack.errorMessage()));
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("WECOM_PROVIDER_ACK_TIMEOUT", failure);
        } finally {
            pending.remove(requestId, future);
        }
    }

    private void handle(ConnectionState state, String raw) {
        try {
            var inboundFrame = codec.inbound(raw);
            if (inboundFrame.isPresent()) {
                handleInbound(state, inboundFrame.get());
                return;
            }
            OpsWeComProtocolCodec.Ack ack = codec.ack(raw);
            if (ack.requestId().equals(state.authRequestId)) {
                if (ack.success()) authenticated(state);
                else authenticationFailed(state, ack.errorMessage());
                return;
            }
            CompletableFuture<OpsWeComProtocolCodec.Ack> future = pending.get(ack.requestId());
            if (future != null) future.complete(ack);
        } catch (RuntimeException failure) {
            state.health = snapshot(ChannelHealthSnapshot.HealthStatus.DEGRADED,
                    "WECOM_FRAME_PROCESSING_FAILED", safe(failure.getMessage()));
        }
    }

    private void handleInbound(ConnectionState state, OpsWeComProtocolCodec.InboundFrame frame) {
        String messageId = frame.envelope().message().externalMessageId();
        replyContexts.put(messageId, new ReplyContext(frame.requestId(), frame.taskId()));
        if (!frame.envelope().content().actions().isEmpty()) {
            ChannelInteractiveActionEnvelope actionEnvelope = new ChannelInteractiveActionEnvelope(
                    frame.envelope().content().actions().get(0),
                    frame.envelope().sender(),
                    frame.envelope().message(),
                    frame.envelope().receivedAt(),
                    frame.envelope().idempotencyKey());
            try {
                var outcome = interactiveActions.tryExecute(state.configuration.channelId(), actionEnvelope);
                if (outcome.isPresent()) {
                    if (outcome.get().terminal()) {
                        bestEffortCardUpdate(state, frame, outcome.get().presentation());
                    }
                    return;
                }
            } catch (RuntimeException denied) {
                // Unauthorized/duplicate clicks must not destroy a shared approval card for other valid approvers.
                return;
            }
        }
        inbound.receiveProvider(state.configuration.channelId(), frame.envelope());
    }

    private void bestEffortCardUpdate(ConnectionState state,
                                      OpsWeComProtocolCodec.InboundFrame frame,
                                      String title) {
        if (frame.requestId().isBlank() || frame.taskId().isBlank() || state.webSocket == null) return;
        try {
            state.webSocket.sendText(codec.updateCard(frame.requestId(), frame.taskId(), title), true);
        } catch (RuntimeException ignored) {
            // Approval state is authoritative in OrbisOps; card refresh is only presentation.
        }
    }

    private void authenticated(ConnectionState state) {
        state.connecting = false;
        state.reconnectAttempts = 0;
        state.health = snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                "WECOM_WEBSOCKET_AUTHENTICATED", "Authenticated WeCom long connection is active");
        cancelHeartbeat(state);
        state.heartbeat = scheduler.scheduleAtFixedRate(() -> {
            WebSocket socket = state.webSocket;
            if (!state.stopped && socket != null) {
                socket.sendText(codec.heartbeat(requestId("ping")), true)
                        .exceptionally(failure -> {
                            connectionFailed(state, "WECOM_HEARTBEAT_FAILED", failure.getMessage());
                            return null;
                        });
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    private void authenticationFailed(ConnectionState state, String message) {
        state.connecting = false;
        state.authFailed = true;
        state.health = snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                "WECOM_AUTHENTICATION_FAILED", safe(message));
        WebSocket socket = state.webSocket;
        state.webSocket = null;
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "authentication-failed");
    }

    private void connectionFailed(ConnectionState state, String reasonCode, String message) {
        if (state.stopped || state.authFailed) return;
        state.connecting = false;
        state.webSocket = null;
        cancelHeartbeat(state);
        state.health = snapshot(ChannelHealthSnapshot.HealthStatus.DEGRADED, reasonCode, safe(message));
        scheduleReconnect(state);
    }

    private void scheduleReconnect(ConnectionState state) {
        if (state.stopped || state.authFailed || state.reconnectAttempts >= 10) return;
        int attempt = ++state.reconnectAttempts;
        long delay = Math.min(30L, 1L << Math.min(attempt - 1, 5));
        scheduler.schedule(() -> {
            if (state.stopped || state.authFailed) return;
            state.connecting = true;
            state.health = snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "WECOM_RECONNECTING", "Reconnect attempt " + attempt);
            connect(state);
        }, delay, TimeUnit.SECONDS);
    }

    private void cancelHeartbeat(ConnectionState state) {
        ScheduledFuture<?> heartbeat = state.heartbeat;
        state.heartbeat = null;
        if (heartbeat != null) heartbeat.cancel(false);
    }

    private ConnectionState stoppedState(OpsWeComChannelConfiguration configuration, String reasonCode, String detail) {
        ConnectionState state = new ConnectionState(configuration);
        state.stopped = true;
        state.health = snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED, reasonCode, detail);
        return state;
    }

    private ChannelHealthSnapshot snapshot(ChannelHealthSnapshot.HealthStatus status, String reasonCode, String detail) {
        return new ChannelHealthSnapshot(status, reasonCode, detail, Instant.now());
    }

    private String requestId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static ScheduledExecutorService daemonScheduler() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "orbisops-wecom-channel");
            thread.setDaemon(true);
            return thread;
        });
    }

    private final class Listener implements WebSocket.Listener {
        private final ConnectionState state;
        private final StringBuilder buffer = new StringBuilder();

        private Listener(ConnectionState state) {
            this.state = state;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            state.webSocket = webSocket;
            state.connecting = false;
            state.authRequestId = requestId("auth");
            webSocket.request(1);
            webSocket.sendText(codec.subscribe(state.authRequestId, state.configuration.botId(), state.secret), true)
                    .exceptionally(failure -> {
                        connectionFailed(state, "WECOM_SUBSCRIBE_FAILED", failure.getMessage());
                        return null;
                    });
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String raw = buffer.toString();
                buffer.setLength(0);
                handle(state, raw);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connectionFailed(state, "WECOM_CONNECTION_CLOSED", reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connectionFailed(state, "WECOM_CONNECTION_ERROR", error == null ? "" : error.getMessage());
        }
    }

    private static final class ConnectionState {
        private OpsWeComChannelConfiguration configuration;
        private volatile WebSocket webSocket;
        private volatile String secret = "";
        private volatile String authRequestId = "";
        private volatile ChannelHealthSnapshot health;
        private volatile boolean stopped;
        private volatile boolean connecting;
        private volatile boolean authFailed;
        private volatile int reconnectAttempts;
        private volatile ScheduledFuture<?> heartbeat;

        private ConnectionState(OpsWeComChannelConfiguration configuration) {
            this.configuration = configuration;
            this.health = new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                    "WECOM_CONNECTION_NOT_STARTED", "WeCom connection has not been started", Instant.now());
        }

        private ConnectionState reset(OpsWeComChannelConfiguration next) {
            this.configuration = next;
            this.authFailed = false;
            this.reconnectAttempts = 0;
            return this;
        }
    }

    private record ReplyContext(String requestId, String taskId) {
        private ReplyContext {
            requestId = requestId == null ? "" : requestId.trim();
            taskId = taskId == null ? "" : taskId.trim();
        }
    }
}
