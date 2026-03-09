package cn.lgs.orbisops.application.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application process manager for AI model configuration CRUD and ordered query. */
public final class AiClientModelCatalogUseCase {

    private final AiClientModelCatalogPort catalogPort;
    private final AiClientModelCatalogAuditPort auditPort;
    private final Clock clock;

    public AiClientModelCatalogUseCase(
            AiClientModelCatalogPort catalogPort,
            AiClientModelCatalogAuditPort auditPort) {
        this(catalogPort, auditPort, Clock.systemDefaultZone());
    }

    public AiClientModelCatalogUseCase(
            AiClientModelCatalogPort catalogPort,
            AiClientModelCatalogAuditPort auditPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_CATALOG_PORT_REQUIRED");
        }
        if (auditPort == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_CATALOG_AUDIT_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_CATALOG_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public boolean create(AiClientModelDefinition requested) {
        LocalDateTime now = LocalDateTime.now(clock);
        AiClientModelDefinition resolved = requireDefinition(requested).withDefaultsAndTimes(now, now);
        boolean created = catalogPort.insert(resolved);
        if (created) {
            auditPort.created(resolved);
        }
        return created;
    }

    public boolean updateById(AiClientModelDefinition requested) {
        AiClientModelDefinition command = requireDefinition(requested);
        AiClientModelDefinition before = catalogPort.findById(command.id());
        AiClientModelDefinition resolved = command.withDefaultsAndTimes(
                command.createTime(),
                LocalDateTime.now(clock));
        boolean updated = catalogPort.updateById(resolved);
        if (updated) {
            auditPort.updatedById(command.id(), before, resolved);
        }
        return updated;
    }

    public boolean updateByModelId(AiClientModelDefinition requested) {
        AiClientModelDefinition command = requireDefinition(requested);
        AiClientModelDefinition before = catalogPort.findByModelId(command.modelId());
        AiClientModelDefinition resolved = command.withDefaultsAndTimes(
                command.createTime(),
                LocalDateTime.now(clock));
        boolean updated = catalogPort.updateByModelId(resolved);
        if (updated) {
            auditPort.updatedByModelId(command.modelId(), before, resolved);
        }
        return updated;
    }

    public boolean deleteById(Long id) {
        AiClientModelDefinition before = catalogPort.findById(id);
        boolean deleted = catalogPort.deleteById(id);
        if (deleted) {
            auditPort.deletedById(id, before);
        }
        return deleted;
    }

    public boolean deleteByModelId(String modelId) {
        AiClientModelDefinition before = catalogPort.findByModelId(modelId);
        boolean deleted = catalogPort.deleteByModelId(modelId);
        if (deleted) {
            auditPort.deletedByModelId(modelId, before);
        }
        return deleted;
    }

    public AiClientModelDefinition findById(Long id) {
        return catalogPort.findById(id);
    }

    public AiClientModelDefinition findByModelId(String modelId) {
        return catalogPort.findByModelId(modelId);
    }

    public List<AiClientModelDefinition> findByApiId(String apiId) {
        return immutable(catalogPort.findByApiId(apiId));
    }

    public List<AiClientModelDefinition> findByModelType(String modelType) {
        return immutable(catalogPort.findByModelType(modelType));
    }

    public List<AiClientModelDefinition> listEnabled() {
        return immutable(catalogPort.listEnabled());
    }

    public List<AiClientModelDefinition> listAll() {
        return immutable(catalogPort.listAll());
    }

    public List<AiClientModelDefinition> query(AiClientModelCatalogQuery query) {
        AiClientModelCatalogQuery safe = query == null ? AiClientModelCatalogQuery.all() : query;
        if (hasText(safe.modelId())) {
            AiClientModelDefinition model = findByModelId(safe.modelId());
            return model == null ? List.of() : List.of(model);
        }
        if (hasText(safe.apiId())) {
            return findByApiId(safe.apiId());
        }
        if (hasText(safe.modelType())) {
            return findByModelType(safe.modelType());
        }
        if (Integer.valueOf(1).equals(safe.status())) {
            return listEnabled();
        }
        return listAll();
    }

    private AiClientModelDefinition requireDefinition(AiClientModelDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_DEFINITION_REQUIRED");
        }
        return definition;
    }

    private List<AiClientModelDefinition> immutable(List<AiClientModelDefinition> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
