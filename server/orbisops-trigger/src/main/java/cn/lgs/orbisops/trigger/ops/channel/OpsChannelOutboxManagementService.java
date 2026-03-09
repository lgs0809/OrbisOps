package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Audited administrative commands for Channel outbox dead letters. */
public final class OpsChannelOutboxManagementService {

    private final IChannelOutboxRepository repository;
    private final OpsChannelOutboxQueryService queries;
    private final OpsConfigAuditService auditService;

    public OpsChannelOutboxManagementService(
            IChannelOutboxRepository repository,
            OpsChannelOutboxQueryService queries,
            OpsConfigAuditService auditService) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_REPOSITORY_REQUIRED");
        if (queries == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_QUERY_SERVICE_REQUIRED");
        if (auditService == null) throw new IllegalArgumentException("CONFIG_AUDIT_SERVICE_REQUIRED");
        this.repository = repository;
        this.queries = queries;
        this.auditService = auditService;
    }

    public Map<String, Object> requeue(String projectId, long id, String actor) {
        return mutate(projectId, id, actor, "requeue",
                () -> repository.requeue(projectId, id),
                "CHANNEL_OUTBOX_REQUEUE_STATE_CONFLICT");
    }

    public Map<String, Object> cancel(String projectId, long id, String actor) {
        return mutate(projectId, id, actor, "cancel",
                () -> repository.cancel(projectId, id),
                "CHANNEL_OUTBOX_CANCEL_STATE_CONFLICT");
    }

    private Map<String, Object> mutate(
            String projectId,
            long id,
            String actor,
            String action,
            Mutation mutation,
            String conflictCode) {
        Map<String, Object> before = queries.get(projectId, id);
        if (!mutation.apply()) throw new IllegalStateException(conflictCode);
        Map<String, Object> after = queries.get(projectId, id);
        auditService.record(
                projectId,
                "channel-notification-outbox",
                action,
                String.valueOf(id),
                before,
                Map.of("status", after.get("status"), "actor", text(actor)));
        return after;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @FunctionalInterface
    private interface Mutation {
        boolean apply();
    }
}
