package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Recovers and dispatches a bounded batch of durable Channel outbox work. */
public final class OpsChannelOutboxBatchProcessor {

    private final IChannelOutboxRepository repository;
    private final OpsChannelOutboxDispatcher dispatcher;
    private final OpsChannelNotificationSettings settings;

    public OpsChannelOutboxBatchProcessor(
            IChannelOutboxRepository repository,
            OpsChannelOutboxDispatcher dispatcher,
            OpsChannelNotificationSettings settings) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_REPOSITORY_REQUIRED");
        if (dispatcher == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_DISPATCHER_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("CHANNEL_NOTIFICATION_SETTINGS_REQUIRED");
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.settings = settings;
    }

    public ProcessingSummary process(int limit) {
        requireAvailable();
        repository.recoverExpiredLeases(settings.maxAttempts());
        List<Long> ids = repository.findDispatchableIds(settings.maxAttempts(), limit);
        int succeeded = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();
        for (Long id : ids) {
            OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                    id,
                    new OpsChannelOutboxDispatcher.Settings(
                            settings.maxAttempts(),
                            settings.leaseSeconds()));
            if (result.success()) {
                succeeded++;
            } else {
                failed++;
            }
            messages.add(result.message());
        }
        return new ProcessingSummary(ids.size(), succeeded, failed, messages);
    }

    private void requireAvailable() {
        if (!repository.available()) {
            throw new IllegalStateException("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE");
        }
    }

    public record ProcessingSummary(
            int processed,
            int success,
            int failed,
            List<String> messages) {

        public ProcessingSummary {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public Map<String, Object> asMap() {
            return Map.of(
                    "processed", processed,
                    "success", success,
                    "failed", failed,
                    "messages", messages);
        }
    }
}
