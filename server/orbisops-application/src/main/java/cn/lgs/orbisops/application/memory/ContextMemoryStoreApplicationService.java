package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IContextMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryProjectionPolicy;

import java.util.List;

/** Application use case for Context Memory persistence, availability, mutation audit and projection writes. */
public class ContextMemoryStoreApplicationService {

    private final IContextMemoryRepository repository;
    private final ContextMemoryProjectionPolicy projectionPolicy;
    private final ContextMemoryAuditPort auditPort;
    private final ContextMemoryStoreFailurePort failurePort;

    public ContextMemoryStoreApplicationService(IContextMemoryRepository repository,
                                                ContextMemoryProjectionPolicy projectionPolicy,
                                                ContextMemoryAuditPort auditPort,
                                                ContextMemoryStoreFailurePort failurePort) {
        this.repository = repository;
        this.projectionPolicy = projectionPolicy == null
                ? new ContextMemoryProjectionPolicy()
                : projectionPolicy;
        this.auditPort = auditPort == null ? event -> { } : auditPort;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
    }

    public boolean available() {
        return repository != null && repository.available();
    }

    public List<ContextMemorySnapshot> search(ContextMemorySearchCriteria criteria) {
        if (!available()) return List.of();
        return repository.search(criteria);
    }

    public ContextMemorySnapshot require(String memoryId) {
        if (!hasText(memoryId)) throw new IllegalArgumentException("memoryId 不能为空");
        return requireRepository().findByMemoryId(memoryId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Context Memory 不存在：" + memoryId));
    }

    public ContextMemorySnapshot create(ContextMemorySnapshot snapshot) {
        ContextMemorySnapshot required = requireSnapshot(snapshot);
        IContextMemoryRepository target = requireRepository();
        if (target.exists(required.memoryId())) {
            throw new IllegalArgumentException("Context Memory 已存在：" + required.memoryId());
        }
        target.upsert(required);
        ContextMemorySnapshot persisted = refreshed(required);
        audit("create", persisted);
        return persisted;
    }

    public ContextMemorySnapshot upsert(ContextMemorySnapshot snapshot) {
        ContextMemorySnapshot required = requireSnapshot(snapshot);
        requireRepository().upsert(required);
        ContextMemorySnapshot persisted = refreshed(required);
        audit("upsert", persisted);
        return persisted;
    }

    public ContextMemorySnapshot updateStatus(String memoryId, String status) {
        IContextMemoryRepository target = requireRepository();
        if (!target.updateStatus(memoryId, status)) {
            throw new IllegalArgumentException("Context Memory 不存在：" + memoryId);
        }
        ContextMemorySnapshot persisted = require(memoryId);
        audit("update-status", persisted);
        return persisted;
    }

    public void saveExtractedItems(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty() || !available()) return;
        for (ColdMemoryItemSnapshot item : items) {
            try {
                projectionPolicy.project(item).ifPresent(snapshot -> {
                    repository.upsert(snapshot);
                    audit("upsert", refreshed(snapshot));
                });
            } catch (RuntimeException error) {
                observeFailure("projection-upsert", error);
            }
        }
    }

    public void saveExtractedItemsStrict(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty()) return;
        IContextMemoryRepository target = requireRepository();
        for (ColdMemoryItemSnapshot item : items) {
            projectionPolicy.project(item).ifPresent(snapshot -> {
                target.upsert(snapshot);
                audit("upsert", refreshed(snapshot));
            });
        }
    }

    private ContextMemorySnapshot refreshed(ContextMemorySnapshot fallback) {
        return repository.findByMemoryId(fallback.memoryId()).orElse(fallback);
    }

    private ContextMemorySnapshot requireSnapshot(ContextMemorySnapshot snapshot) {
        if (snapshot == null || !hasText(snapshot.memoryId())) {
            throw new IllegalArgumentException("memoryId 不能为空");
        }
        return snapshot;
    }

    private IContextMemoryRepository requireRepository() {
        if (!available()) throw new IllegalStateException("Context Memory 数据库未配置");
        return repository;
    }

    private void audit(String action, ContextMemorySnapshot snapshot) {
        try {
            auditPort.record(new ContextMemoryAuditEvent(action, snapshot));
        } catch (RuntimeException ignored) {
            // Persistence remains authoritative even if diagnostics are unavailable.
        }
    }

    private void observeFailure(String operation, RuntimeException error) {
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
            // Projection persistence remains best-effort even if diagnostics are unavailable.
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
