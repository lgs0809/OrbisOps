package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-side Channel outbox query and health projection. */
public final class OpsChannelOutboxQueryService {

    private final IChannelOutboxRepository repository;
    private final ChannelQueryService channelQueryService;
    private final OpsChannelOutboxViewMapper viewMapper;

    public OpsChannelOutboxQueryService(
            IChannelOutboxRepository repository,
            ChannelQueryService channelQueryService,
            OpsChannelOutboxViewMapper viewMapper) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_REPOSITORY_REQUIRED");
        if (channelQueryService == null) throw new IllegalArgumentException("CHANNEL_QUERY_SERVICE_REQUIRED");
        if (viewMapper == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_VIEW_MAPPER_REQUIRED");
        this.repository = repository;
        this.channelQueryService = channelQueryService;
        this.viewMapper = viewMapper;
    }

    public List<Map<String, Object>> list(String projectId, int limit) {
        requireAvailable();
        return repository.findByProject(projectId, limit).stream().map(viewMapper::toView).toList();
    }

    public Map<String, Object> get(String projectId, long id) {
        requireAvailable();
        return repository.findByProjectAndId(projectId, id)
                .map(viewMapper::toView)
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_OUTBOX_NOT_FOUND"));
    }

    public String statusOf(long id) {
        return repository.findById(id).map(ChannelOutboxRecord::status).orElse("UNKNOWN");
    }

    public Map<String, Object> status(String projectId) {
        if (!repository.available()) {
            return Map.of("ready", false, "reason", "CHANNEL_NOTIFICATION_STORE_UNAVAILABLE");
        }
        ChannelOutboxStatus outboxStatus = repository.status(projectId);
        return combine(channelQueryService.status(projectId), outboxStatus);
    }

    public Map<String, Object> statusAll() {
        if (!repository.available()) {
            return Map.of("ready", false, "reason", "CHANNEL_NOTIFICATION_STORE_UNAVAILABLE");
        }
        return combine(channelQueryService.statusAll(), repository.statusAll());
    }

    public boolean available() {
        return repository.available();
    }

    public void requireAvailable() {
        if (!repository.available()) {
            throw new IllegalStateException("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE");
        }
    }

    private Map<String, Object> combine(
            Map<String, Object> channelStatus,
            ChannelOutboxStatus outboxStatus) {
        Map<String, Object> status = channelStatus == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(channelStatus);
        status.put("pendingNotifications", outboxStatus.pending());
        status.put("deadLetterNotifications", outboxStatus.deadLetters());
        status.put("outboxReady", true);
        return Collections.unmodifiableMap(new LinkedHashMap<>(status));
    }
}
