package cn.lgs.orbisops.application.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Application process manager for importing one Provider's `/models` catalog. */
public final class AiClientModelSyncUseCase {

    private static final int MAX_MODELS_PER_SYNC = 200;

    private final AiClientModelSyncTargetPort targetPort;
    private final AiClientModelSyncProtocolPort protocolPort;
    private final AiClientModelSyncCatalogPort catalogPort;
    private final AiClientModelSyncAuditPort auditPort;
    private final Clock clock;

    public AiClientModelSyncUseCase(
            AiClientModelSyncTargetPort targetPort,
            AiClientModelSyncProtocolPort protocolPort,
            AiClientModelSyncCatalogPort catalogPort,
            AiClientModelSyncAuditPort auditPort) {
        this(targetPort, protocolPort, catalogPort, auditPort, Clock.systemDefaultZone());
    }

    public AiClientModelSyncUseCase(
            AiClientModelSyncTargetPort targetPort,
            AiClientModelSyncProtocolPort protocolPort,
            AiClientModelSyncCatalogPort catalogPort,
            AiClientModelSyncAuditPort auditPort,
            Clock clock) {
        if (targetPort == null || protocolPort == null || catalogPort == null
                || auditPort == null || clock == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_SYNC_DEPENDENCY_REQUIRED");
        }
        this.targetPort = targetPort;
        this.protocolPort = protocolPort;
        this.catalogPort = catalogPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public AiClientModelSyncResult sync(String apiId) {
        LocalDateTime syncedAt = LocalDateTime.now(clock);
        AiClientModelSyncTarget target = targetPort.find(apiId);
        if (target == null) {
            throw new IllegalArgumentException("未找到对应的 API Provider：" + apiId);
        }
        String endpoint = protocolPort.resolveEndpoint(target);
        try {
            AiClientModelSyncFetchResult fetched = protocolPort.fetch(target, endpoint);
            List<String> fetchedModelIds = fetched == null ? List.of() : fetched.modelIds();
            int created = 0;
            int updated = 0;
            int skipped = 0;
            List<String> syncedModelIds = new ArrayList<>();
            for (String modelId : fetchedModelIds.stream().limit(MAX_MODELS_PER_SYNC).toList()) {
                AiClientModelDefinition existing = catalogPort.findByModelId(modelId);
                if (existing != null && !same(existing.apiId(), target.apiId())) {
                    skipped++;
                    continue;
                }
                AiClientModelDefinition next = synchronizedDefinition(target.apiId(), modelId, existing);
                if (existing == null) {
                    created += catalogPort.insert(next) ? 1 : 0;
                } else {
                    updated += catalogPort.updateByModelId(next) ? 1 : 0;
                }
                syncedModelIds.add(modelId);
            }
            skipped += Math.max(0, fetchedModelIds.size() - MAX_MODELS_PER_SYNC);
            AiClientModelSyncResult result = new AiClientModelSyncResult(
                    target.apiId(),
                    fetched == null ? endpoint : fetched.endpoint(),
                    fetched == null ? null : fetched.httpStatus(),
                    fetchedModelIds.size(),
                    created,
                    updated,
                    skipped,
                    syncedModelIds,
                    "",
                    syncedAt);
            auditPort.succeeded(result);
            return result;
        } catch (Exception e) {
            AiClientModelSyncResult failed = new AiClientModelSyncResult(
                    target.apiId(),
                    endpoint,
                    null,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    e.getMessage(),
                    syncedAt);
            auditPort.failed(failed);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private AiClientModelDefinition synchronizedDefinition(
            String apiId,
            String modelId,
            AiClientModelDefinition existing) {
        String usage = inferUsage(modelId);
        LocalDateTime now = LocalDateTime.now(clock);
        return new AiClientModelDefinition(
                existing == null ? null : existing.id(),
                modelId,
                apiId,
                hasText(existing == null ? null : existing.modelName()) ? existing.modelName() : modelId,
                hasText(existing == null ? null : existing.modelType()) ? existing.modelType() : usage,
                hasText(existing == null ? null : existing.modelUsage()) ? existing.modelUsage() : usage,
                hasText(existing == null ? null : existing.description())
                        ? existing.description()
                        : "Synced from Provider " + apiId,
                existing == null ? 1 : existing.status(),
                existing == null ? now : existing.createTime(),
                now);
    }

    private String inferUsage(String modelId) {
        String normalized = modelId == null ? "" : modelId.toLowerCase();
        if (normalized.contains("embed")) {
            return "EMBEDDING";
        }
        if (normalized.contains("rerank")) {
            return "RERANK";
        }
        if (normalized.contains("vision") || normalized.contains("-vl") || normalized.contains("vl-")) {
            return "VISION";
        }
        return "CHAT";
    }

    private boolean same(String left, String right) {
        return hasText(left) && hasText(right) && left.trim().equals(right.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
