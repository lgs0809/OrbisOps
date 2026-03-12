package cn.lgs.orbisops.application.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application process manager for AI model-provider API configuration CRUD and query. */
public final class AiClientApiCatalogUseCase {

    private static final String MASK_PLACEHOLDER = "******";

    private final AiClientApiCatalogPort catalogPort;
    private final AiClientApiCatalogAuditPort auditPort;
    private final Clock clock;

    public AiClientApiCatalogUseCase(
            AiClientApiCatalogPort catalogPort,
            AiClientApiCatalogAuditPort auditPort) {
        this(catalogPort, auditPort, Clock.systemDefaultZone());
    }

    public AiClientApiCatalogUseCase(
            AiClientApiCatalogPort catalogPort,
            AiClientApiCatalogAuditPort auditPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_PORT_REQUIRED");
        }
        if (auditPort == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_AUDIT_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public boolean create(AiClientApiDefinition requested) {
        AiClientApiDefinition resolved = prepareCreate(requireDefinition(requested));
        boolean created = catalogPort.insert(resolved);
        if (created) {
            auditPort.created(resolved);
        }
        return created;
    }

    public boolean updateById(AiClientApiDefinition requested) {
        AiClientApiDefinition command = requireDefinition(requested);
        AiClientApiDefinition before = catalogPort.findById(command.id());
        AiClientApiDefinition resolved = prepareUpdate(command, before);
        boolean updated = catalogPort.updateById(resolved);
        if (updated) {
            auditPort.updatedById(command.id(), before, resolved);
        }
        return updated;
    }

    public boolean updateByApiId(AiClientApiDefinition requested) {
        AiClientApiDefinition command = requireDefinition(requested);
        AiClientApiDefinition before = catalogPort.findByApiId(command.apiId());
        AiClientApiDefinition resolved = prepareUpdate(command, before);
        boolean updated = catalogPort.updateByApiId(resolved);
        if (updated) {
            auditPort.updatedByApiId(command.apiId(), before, resolved);
        }
        return updated;
    }

    public boolean deleteById(Long id) {
        AiClientApiDefinition before = catalogPort.findById(id);
        boolean deleted = catalogPort.deleteById(id);
        if (deleted) {
            auditPort.deletedById(id, before);
        }
        return deleted;
    }

    public boolean deleteByApiId(String apiId) {
        AiClientApiDefinition before = catalogPort.findByApiId(apiId);
        boolean deleted = catalogPort.deleteByApiId(apiId);
        if (deleted) {
            auditPort.deletedByApiId(apiId, before);
        }
        return deleted;
    }

    public AiClientApiDefinition findById(Long id) {
        return catalogPort.findById(id);
    }

    public AiClientApiDefinition findByApiId(String apiId) {
        return catalogPort.findByApiId(apiId);
    }

    public List<AiClientApiDefinition> listEnabled() {
        return immutable(catalogPort.listEnabled());
    }

    public List<AiClientApiDefinition> listAll() {
        return immutable(catalogPort.listAll());
    }

    public List<AiClientApiDefinition> query(AiClientApiCatalogQuery query) {
        AiClientApiCatalogQuery safe = query == null ? AiClientApiCatalogQuery.all() : query;
        List<AiClientApiDefinition> filtered = listAll().stream()
                .filter(api -> !hasText(safe.apiId())
                        || api != null && api.apiId() != null && api.apiId().contains(safe.apiId()))
                .filter(api -> !hasText(safe.baseUrl())
                        || api != null && api.baseUrl() != null && api.baseUrl().contains(safe.baseUrl()))
                .filter(api -> safe.status() == null
                        || api != null && safe.status().equals(api.status()))
                .toList();
        int startIndex = (safe.pageNum() - 1) * safe.pageSize();
        if (startIndex >= filtered.size()) {
            return List.of();
        }
        int endIndex = Math.min(startIndex + safe.pageSize(), filtered.size());
        return List.copyOf(filtered.subList(startIndex, endIndex));
    }

    private AiClientApiDefinition prepareCreate(AiClientApiDefinition requested) {
        LocalDateTime now = LocalDateTime.now(clock);
        AiClientApiDefinition withSecret = resolveMaskedSecret(requested, null);
        return withSecret.withDefaultsAndTimes(now, now);
    }

    private AiClientApiDefinition prepareUpdate(
            AiClientApiDefinition requested,
            AiClientApiDefinition before) {
        AiClientApiDefinition withSecret = resolveMaskedSecret(requested, before);
        return withSecret.withDefaultsAndTimes(
                requested.createTime(),
                LocalDateTime.now(clock));
    }

    private AiClientApiDefinition resolveMaskedSecret(
            AiClientApiDefinition requested,
            AiClientApiDefinition knownExisting) {
        if (!hasText(requested.apiKey()) || !requested.apiKey().contains(MASK_PLACEHOLDER)) {
            return requested;
        }
        AiClientApiDefinition existing = knownExisting;
        if (existing == null && requested.id() != null) {
            existing = catalogPort.findById(requested.id());
        }
        if (existing == null && hasText(requested.apiId())) {
            existing = catalogPort.findByApiId(requested.apiId());
        }
        return existing == null ? requested : requested.withApiKey(existing.apiKey());
    }

    private AiClientApiDefinition requireDefinition(AiClientApiDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_DEFINITION_REQUIRED");
        }
        return definition;
    }

    private List<AiClientApiDefinition> immutable(List<AiClientApiDefinition> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
