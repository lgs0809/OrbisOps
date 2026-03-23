package cn.lgs.orbisops.trigger.ops.channel.telegram;

import cn.lgs.orbisops.application.channel.ChannelOutboundMetadata;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public final class OpsTelegramChannelConnectionDriver implements ChannelConnectionDriver<OpsTelegramChannelConfiguration> {

    private final ReceiveChannelMessageUseCase inbound;
    private final OpsChannelInteractiveActionDispatcher interactiveActions;
    private final OpsTelegramBotApiClient client;
    private final OpsTelegramProtocolCodec codec = new OpsTelegramProtocolCodec();
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "orbisops-telegram-poll");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final Map<String, ChannelHealthSnapshot> health = new ConcurrentHashMap<>();

    public OpsTelegramChannelConnectionDriver(ReceiveChannelMessageUseCase inbound,
                                              OpsChannelInteractiveActionDispatcher interactiveActions,
                                              OpsTelegramBotApiClient client) {
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        if (interactiveActions == null) throw new IllegalArgumentException("CHANNEL_INTERACTIVE_ACTION_DISPATCHER_REQUIRED");
        if (client == null) throw new IllegalArgumentException("TELEGRAM_API_CLIENT_REQUIRED");
        this.inbound = inbound;
        this.interactiveActions = interactiveActions;
        this.client = client;
    }

    @Override
    public ChannelType type() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public void start(OpsTelegramChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (workers.containsKey(configuration.channelId())) return;
        try {
            String remoteUsername = client.botUsername(configuration);
            if (!configuration.botUsername().isBlank()
                    && !configuration.botUsername().equalsIgnoreCase(remoteUsername)) {
                health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                        "TELEGRAM_BOT_USERNAME_MISMATCH",
                        "Configured bot username does not match Telegram getMe response"));
                return;
            }
            Worker worker = new Worker();
            Worker existing = workers.putIfAbsent(configuration.channelId(), worker);
            if (existing != null) return;
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "TELEGRAM_CONNECTING",
                    "Telegram Bot API credential is verified; long polling is starting"));
            worker.future = executor.submit(() -> pollLoop(configuration, worker));
        } catch (RuntimeException failure) {
            workers.remove(configuration.channelId());
            health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "TELEGRAM_CONNECTION_FAILED", safe(failure.getMessage())));
        }
    }

    @Override
    public void stop(String channelId) {
        Worker worker = workers.remove(channelId);
        if (worker != null) worker.stop();
        health.put(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "TELEGRAM_CONNECTION_STOPPED", "Telegram long polling stopped"));
    }

    @Override
    public ChannelHealthSnapshot health(String channelId) {
        return health.getOrDefault(channelId, snapshot(ChannelHealthSnapshot.HealthStatus.STOPPED,
                "TELEGRAM_CONNECTION_NOT_STARTED", "Telegram connection has not been started"));
    }

    public ChannelDeliveryReceipt send(OpsTelegramChannelConfiguration configuration, ChannelOutboundMessage message) {
        return client.send(configuration, message);
    }

    public ChannelDeliveryReceipt update(OpsTelegramChannelConfiguration configuration,
                                         ChannelMessageRef existingMessage,
                                         ChannelOutboundMessage message) {
        return client.update(configuration, existingMessage, message);
    }

    long pollOnce(OpsTelegramChannelConfiguration configuration, long offset) {
        long nextOffset = Math.max(0L, offset);
        List<JSONObject> updates = client.poll(configuration, nextOffset);
        for (JSONObject update : updates) {
            OpsTelegramProtocolCodec.DecodedUpdate decoded = codec.decode(configuration, update);
            if (decoded.updateId() > 0) nextOffset = Math.max(nextOffset, decoded.updateId() + 1);
            if (decoded.inbound() != null) {
                inbound.receiveProvider(configuration.channelId(), decoded.inbound());
            }
            if (decoded.interactive() != null) {
                dispatchInteractive(configuration, decoded);
            }
        }
        return nextOffset;
    }

    @Override
    public void shutdown() {
        for (String channelId : List.copyOf(workers.keySet())) stop(channelId);
        executor.shutdownNow();
    }

    private void pollLoop(OpsTelegramChannelConfiguration configuration, Worker worker) {
        while (worker.running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                long next = pollOnce(configuration, worker.offset.get());
                worker.offset.set(next);
                health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.READY,
                        "TELEGRAM_LONG_POLLING_ACTIVE", "Telegram Bot API long polling is active"));
            } catch (RuntimeException failure) {
                health.put(configuration.channelId(), snapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                        "TELEGRAM_POLL_FAILED", safe(failure.getMessage())));
                sleepBackoff();
            }
        }
    }

    private void dispatchInteractive(OpsTelegramChannelConfiguration configuration,
                                     OpsTelegramProtocolCodec.DecodedUpdate decoded) {
        String presentation = "Action unavailable";
        try {
            var outcome = interactiveActions.tryExecute(configuration.channelId(), decoded.interactive());
            if (outcome.isPresent()) {
                presentation = safe(outcome.get().presentation());
                if (outcome.get().terminal()) {
                    ChannelMessageRef message = decoded.interactive().message();
                    client.update(configuration, message,
                            new ChannelOutboundMessage(
                                    message.conversation(),
                                    ChannelRichContent.text(presentation),
                                    List.of(),
                                    ChannelOutboundMetadata.from(null)));
                }
            }
        } catch (RuntimeException denied) {
            presentation = "Action unavailable";
        } finally {
            try {
                client.answerCallback(configuration, decoded.callbackQueryId(), presentation);
            } catch (RuntimeException ignored) {
                // Callback acknowledgement failure must not change approval authority or replay semantics.
            }
        }
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ChannelHealthSnapshot snapshot(ChannelHealthSnapshot.HealthStatus status,
                                           String reasonCode,
                                           String detail) {
        return new ChannelHealthSnapshot(status, reasonCode, detail, Instant.now());
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class Worker {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicLong offset = new AtomicLong(0L);
        private volatile Future<?> future;

        private void stop() {
            running.set(false);
            Future<?> current = future;
            if (current != null) current.cancel(true);
        }
    }
}
