package cn.lgs.orbisops.trigger.ops.channel.qq;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public final class OpsQqChannelConnectionDriver implements ChannelConnectionDriver<OpsQqChannelConfiguration> {

    private final ReceiveChannelMessageUseCase inbound;
    private final OpsChannelInteractiveActionDispatcher interactiveActions;
    private final OpsQqApiClient api;
    private final OpsQqProtocolCodec codec = new OpsQqProtocolCodec();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> daemon(task, "orbisops-qq-gateway"));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            task -> daemon(task, "orbisops-qq-heartbeat"));
    private final Map<String, GatewaySession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ChannelHealthSnapshot> health = new ConcurrentHashMap<>();

    public OpsQqChannelConnectionDriver(ReceiveChannelMessageUseCase inbound,
                                        OpsChannelInteractiveActionDispatcher interactiveActions,
                                        OpsQqApiClient api) {
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        if (interactiveActions == null) throw new IllegalArgumentException("CHANNEL_INTERACTIVE_ACTION_DISPATCHER_REQUIRED");
        if (api == null) throw new IllegalArgumentException("QQ_API_CLIENT_REQUIRED");
        this.inbound = inbound;
        this.interactiveActions = interactiveActions;
        this.api = api;
    }

    @Override
    public ChannelType type() {
        return ChannelType.QQ;
    }

    @Override
    public void start(OpsQqChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        GatewaySession state = new GatewaySession(configuration);
        if (sessions.putIfAbsent(configuration.channelId(), state) != null) return;
        health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                "QQ_CONNECTING", "QQ Gateway session is starting"));
        state.bootstrap = executor.submit(() -> bootstrap(state));
    }

    @Override
    public void stop(String channelId) {
        GatewaySession state = sessions.remove(channelId);
        if (state != null) state.stop();
        health.put(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "QQ_CONNECTION_STOPPED", "QQ Gateway session stopped"));
    }

    @Override
    public ChannelHealthSnapshot health(String channelId) {
        return health.getOrDefault(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "QQ_CONNECTION_NOT_STARTED", "QQ Gateway session has not been started"));
    }

    ChannelDeliveryReceipt send(OpsQqChannelConfiguration configuration, ChannelOutboundMessage message) {
        return api.send(configuration, message);
    }

    void handleGatewayPayload(GatewaySession state, String raw) {
        if (state == null || raw == null || raw.isBlank()) return;
        JSONObject payload;
        try {
            payload = JSON.parseObject(raw);
        } catch (RuntimeException malformed) {
            return;
        }
        if (payload == null) return;
        int op = payload.getIntValue("op");
        Long sequence = payload.getLong("s");
        if (sequence != null) state.sequence.set(sequence);
        switch (op) {
            case 0 -> onDispatch(state, payload);
            case 1 -> sendHeartbeat(state);
            case 7 -> reconnect(state, true, "QQ_RECONNECT_REQUESTED");
            case 9 -> reconnect(state, payload.getBooleanValue("d"), "QQ_INVALID_SESSION");
            case 10 -> onHello(state, payload.getJSONObject("d"));
            case 11 -> state.heartbeatAck.set(true);
            default -> { }
        }
    }

    @Override
    public void shutdown() {
        for (String channelId : List.copyOf(sessions.keySet())) stop(channelId);
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    private void bootstrap(GatewaySession state) {
        if (!state.running.get()) return;
        try {
            state.gatewayUrl = api.gatewayUrl(state.configuration);
            connect(state, false);
        } catch (RuntimeException failure) {
            sessions.remove(state.configuration.channelId(), state);
            health.put(state.configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "QQ_CONNECTION_FAILED", safe(failure.getMessage())));
        }
    }

    private void connect(GatewaySession state, boolean resume) {
        if (!state.running.get()) return;
        String endpoint = state.gatewayUrl;
        if (endpoint == null || endpoint.isBlank()) {
            reconnect(state, false, "QQ_GATEWAY_URL_MISSING");
            return;
        }
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(endpoint), new GatewayListener(state))
                .whenComplete((socket, failure) -> {
                    if (failure != null) {
                        state.webSocket = null;
                        reconnect(state, resume, "QQ_GATEWAY_CONNECT_FAILED");
                    } else state.webSocket = socket;
                });
    }

    private void onHello(GatewaySession state, JSONObject data) {
        if (data == null) return;
        long interval = data.getLongValue("heartbeat_interval");
        if (interval <= 0) {
            reconnect(state, true, "QQ_HEARTBEAT_INTERVAL_INVALID");
            return;
        }
        scheduleHeartbeat(state, interval);
        if (!state.sessionId.isBlank() && state.sequence.get() >= 0) sendResume(state);
        else sendIdentify(state);
    }

    private void onDispatch(GatewaySession state, JSONObject payload) {
        String eventType = safe(payload.getString("t"));
        JSONObject data = payload.getJSONObject("d");
        if ("READY".equals(eventType)) {
            if (data != null) state.sessionId = safe(data.getString("session_id"));
            state.reconnecting.set(false);
            health.put(state.configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                    "QQ_GATEWAY_READY", "Authenticated QQ Gateway session is active"));
            return;
        }
        if ("RESUMED".equals(eventType)) {
            state.reconnecting.set(false);
            health.put(state.configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                    "QQ_GATEWAY_RESUMED", "QQ Gateway session resumed"));
            return;
        }
        OpsQqProtocolCodec.DecodedDispatch decoded = codec.decodeDispatch(eventType, data);
        if (decoded.inbound() != null) inbound.receiveProvider(state.configuration.channelId(), decoded.inbound());
        if (decoded.interactive() != null) dispatchInteractive(state.configuration, decoded);
    }

    private void dispatchInteractive(OpsQqChannelConfiguration configuration,
                                     OpsQqProtocolCodec.DecodedDispatch decoded) {
        try {
            api.acknowledgeInteraction(configuration, decoded.interactionId());
        } catch (RuntimeException ignored) {
            // Platform acknowledgement does not decide the OrbisOps approval.
        }
        try {
            interactiveActions.tryExecute(configuration.channelId(), decoded.interactive());
        } catch (RuntimeException ignored) {
            // Unified RBAC / expiry / replay rules remain authoritative.
        }
    }

    private void sendIdentify(GatewaySession state) {
        JSONObject data = new JSONObject();
        data.put("token", api.gatewayAuthorization(state.configuration));
        data.put("intents", state.configuration.gatewayIntents());
        data.put("shard", List.of(0, 1));
        data.put("properties", Map.of("os", System.getProperty("os.name", "unknown"), "browser", "orbisops", "device", "orbisops"));
        send(state, envelope(2, data));
    }

    private void sendResume(GatewaySession state) {
        JSONObject data = new JSONObject();
        data.put("token", api.gatewayAuthorization(state.configuration));
        data.put("session_id", state.sessionId);
        data.put("seq", state.sequence.get());
        send(state, envelope(6, data));
    }

    private void scheduleHeartbeat(GatewaySession state, long intervalMs) {
        cancelHeartbeat(state);
        long initialDelay = ThreadLocalRandom.current().nextLong(Math.max(1L, intervalMs));
        state.heartbeat = scheduler.scheduleAtFixedRate(() -> {
            if (!state.running.get()) return;
            if (state.heartbeatSent.get() && !state.heartbeatAck.get()) {
                reconnect(state, true, "QQ_HEARTBEAT_ACK_MISSING");
                return;
            }
            state.heartbeatAck.set(false);
            state.heartbeatSent.set(true);
            sendHeartbeat(state);
        }, initialDelay, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat(GatewaySession state) {
        JSONObject payload = new JSONObject();
        payload.put("op", 1);
        payload.put("d", state.sequence.get() < 0 ? null : state.sequence.get());
        send(state, payload);
    }

    private void reconnect(GatewaySession state, boolean resume, String reasonCode) {
        if (!state.running.get() || !state.reconnecting.compareAndSet(false, true)) return;
        if (!resume) state.clearSession();
        cancelHeartbeat(state);
        WebSocket socket = state.webSocket;
        state.webSocket = null;
        if (socket != null) socket.abort();
        health.put(state.configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                reasonCode, resume ? "QQ Gateway session will attempt resume" : "QQ Gateway session will re-identify"));
        executor.submit(() -> {
            sleepBackoff();
            if (!state.running.get()) return;
            try {
                state.gatewayUrl = api.gatewayUrl(state.configuration);
                state.reconnecting.set(false);
                connect(state, resume && !state.sessionId.isBlank());
            } catch (RuntimeException failure) {
                state.reconnecting.set(false);
                health.put(state.configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                        "QQ_RECONNECT_FAILED", safe(failure.getMessage())));
            }
        });
    }

    private void onClosed(GatewaySession state, int statusCode) {
        cancelHeartbeat(state);
        if (!state.running.get()) return;
        if (fatalClose(statusCode)) {
            sessions.remove(state.configuration.channelId(), state);
            health.put(state.configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "QQ_GATEWAY_CLOSED_" + statusCode, "QQ rejected the Gateway session configuration"));
            return;
        }
        boolean resume = statusCode != 4006 && statusCode != 4007 && statusCode != 4009 && !state.sessionId.isBlank();
        reconnect(state, resume, "QQ_GATEWAY_CLOSED_" + statusCode);
    }

    private boolean fatalClose(int code) {
        return code == 4004 || code == 4914 || code == 4915;
    }

    private void cancelHeartbeat(GatewaySession state) {
        ScheduledFuture<?> heartbeat = state.heartbeat;
        state.heartbeat = null;
        if (heartbeat != null) heartbeat.cancel(true);
        state.heartbeatSent.set(false);
        state.heartbeatAck.set(true);
    }

    private JSONObject envelope(int op, Object data) {
        JSONObject payload = new JSONObject();
        payload.put("op", op);
        payload.put("d", data);
        return payload;
    }

    private void send(GatewaySession state, JSONObject payload) {
        WebSocket socket = state.webSocket;
        if (socket != null && state.running.get()) socket.sendText(payload.toJSONString(), true);
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ChannelHealthSnapshot snapshot(ChannelHealthSnapshot.HealthStatus status, String reasonCode, String detail) {
        return new ChannelHealthSnapshot(status, reasonCode, detail, Instant.now());
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    final class GatewayListener implements WebSocket.Listener {
        private final GatewaySession state;
        private final StringBuilder text = new StringBuilder();

        GatewayListener(GatewaySession state) { this.state = state; }

        @Override
        public void onOpen(WebSocket webSocket) {
            state.webSocket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String payload = text.toString();
                text.setLength(0);
                handleGatewayPayload(state, payload);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            onClosed(state, statusCode);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            reconnect(state, true, "QQ_GATEWAY_ERROR");
        }
    }

    final class GatewaySession {
        private final OpsQqChannelConfiguration configuration;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean reconnecting = new AtomicBoolean(false);
        private final AtomicBoolean heartbeatSent = new AtomicBoolean(false);
        private final AtomicBoolean heartbeatAck = new AtomicBoolean(true);
        private final AtomicLong sequence = new AtomicLong(-1L);
        private volatile String gatewayUrl = "";
        private volatile String sessionId = "";
        private volatile WebSocket webSocket;
        private volatile ScheduledFuture<?> heartbeat;
        private volatile Future<?> bootstrap;

        GatewaySession(OpsQqChannelConfiguration configuration) { this.configuration = configuration; }

        private void clearSession() {
            sessionId = "";
            sequence.set(-1L);
        }

        private void stop() {
            running.set(false);
            cancelHeartbeat(this);
            if (bootstrap != null) bootstrap.cancel(true);
            WebSocket socket = webSocket;
            webSocket = null;
            if (socket != null) socket.abort();
        }
    }
}
