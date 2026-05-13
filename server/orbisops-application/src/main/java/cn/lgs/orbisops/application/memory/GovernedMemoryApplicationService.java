package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IGovernedMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryDraft;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryHead;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryRuntimeQuery;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryVersionSnapshot;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryHashPolicy;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Application orchestration for governed explicit-memory create, query and verification use cases. */
public class GovernedMemoryApplicationService {

    private final IGovernedMemoryRepository repository;
    private final GovernedMemoryPolicy memoryPolicy;
    private final GovernedMemoryHashPolicy hashPolicy;
    private final Supplier<String> memoryIdSupplier;
    private final Clock clock;
    private final GovernedMemoryExternalAuditPort externalAuditPort;
    private final Duration defaultTtl;

    public GovernedMemoryApplicationService(
            IGovernedMemoryRepository repository,
            GovernedMemoryPolicy memoryPolicy,
            GovernedMemoryHashPolicy hashPolicy,
            Supplier<String> memoryIdSupplier,
            Clock clock,
            GovernedMemoryExternalAuditPort externalAuditPort,
            Duration defaultTtl) {
        if (repository == null) throw new IllegalArgumentException("GOVERNED_MEMORY_REPOSITORY_REQUIRED");
        if (memoryIdSupplier == null) throw new IllegalArgumentException("GOVERNED_MEMORY_ID_SUPPLIER_REQUIRED");
        this.repository = repository;
        this.memoryPolicy = memoryPolicy == null ? new GovernedMemoryPolicy() : memoryPolicy;
        this.hashPolicy = hashPolicy == null ? new GovernedMemoryHashPolicy() : hashPolicy;
        this.memoryIdSupplier = memoryIdSupplier;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.externalAuditPort = externalAuditPort;
        this.defaultTtl = defaultTtl == null ? Duration.ofHours(24) : defaultTtl;
    }

    public GovernedMemoryCreationResult create(GovernedMemoryCreateCommand command) {
        if (command == null) throw new IllegalArgumentException("GOVERNED_MEMORY_CREATE_COMMAND_REQUIRED");
        requireRepository();
        GovernedMemoryDraft draft = memoryPolicy.draft(
                command.scopeType(),
                command.scopeId(),
                command.memoryType(),
                command.content(),
                command.normalizedContent(),
                command.logicalKey(),
                command.sourceType(),
                command.verified(),
                command.confidence(),
                command.riskLevel());
        String lockKey = hashPolicy.sha256(draft.scope() + ":" + draft.scopeId() + ":" + draft.type() + ":" + draft.logicalKey());
        return repository.withLogicalLock(lockKey, () -> createLocked(command, draft));
    }

    private GovernedMemoryCreationResult createLocked(GovernedMemoryCreateCommand command, GovernedMemoryDraft draft) {
        String idempotencyKey = hashPolicy.idempotencyKey(draft, command.sourceRunId());
        Optional<GovernedMemorySnapshot> duplicate = repository.findByIdempotencyKey(idempotencyKey);
        if (duplicate.isPresent()) {
            return GovernedMemoryCreationResult.duplicate(duplicate.get());
        }

        Optional<GovernedMemoryHead> existing = repository.findLatestHead(
                draft.scope(),
                draft.scopeId(),
                draft.type(),
                draft.logicalKey());
        Optional<GovernedMemorySnapshot> previous = existing.flatMap(head -> repository.findByMemoryId(head.memoryId()));
        if (previous.isPresent() && !hashPolicy.conflicts(hashPolicy.contentFingerprint(previous.get()), hashPolicy.contentFingerprint(draft))) {
            GovernedMemorySnapshot same = previous.get();
            repository.appendSource(same.memoryId(), idempotencyKey, command.sourceRunId(), command.proofRefs(), command.actor());
            repository.recordAudit(same.memoryId(), "SOURCE_ADDED", command.actor(), same, "");
            return GovernedMemoryCreationResult.duplicate(same);
        }
        int version = memoryPolicy.nextVersion(existing.map(GovernedMemoryHead::version).orElse(null));
        String memoryId = requireMemoryId(memoryIdSupplier.get());
        String memoryHash = hashPolicy.memoryHash(draft, version);
        boolean conflict = existing.isPresent();
        String status = memoryPolicy.status(conflict);
        Instant now = clock.instant();
        Instant expiresAt = memoryPolicy.expiresAt(draft.type(), now, defaultTtl);
        GovernedMemorySnapshot pending = new GovernedMemorySnapshot(
                0L,
                memoryId,
                draft.scope(),
                draft.scopeId(),
                command.userId(),
                command.projectId(),
                command.agentId(),
                command.sessionId(),
                draft.type(),
                draft.logicalKey(),
                draft.content(),
                draft.normalizedContent(),
                draft.sourceType(),
                command.sourceRunId(),
                draft.verified(),
                draft.confidence(),
                draft.riskLevel(),
                status,
                version,
                memoryHash,
                command.proofRefs(),
                expiresAt,
                command.actor(),
                idempotencyKey,
                null,
                null);
        repository.insert(pending);
        repository.appendSource(memoryId, idempotencyKey, command.sourceRunId(), command.proofRefs(), command.actor());
        repository.appendVersion(new GovernedMemoryVersionSnapshot(
                memoryId,
                version,
                memoryHash,
                status,
                draft.content(),
                draft.normalizedContent(),
                command.sourceRunId(),
                command.actor()));

        String conflictId = recordConflict(existing, pending, conflict);
        GovernedMemorySnapshot stored = requireMemory(memoryId);
        repository.recordAudit(
                memoryId,
                conflict ? "CONFLICT_CREATED" : "MEMORY_CREATED",
                command.actor(),
                stored,
                conflictId);
        GovernedMemoryCreationResult result = GovernedMemoryCreationResult.created(stored, conflictId);
        if (externalAuditPort != null) {
            externalAuditPort.recordCreate(command.projectId(), conflict, result);
        }
        return result;
    }

    public GovernedMemorySnapshot require(String memoryId) {
        requireRepository();
        return requireMemory(memoryId);
    }

    public List<GovernedMemorySnapshot> selectForRuntime(GovernedMemoryRuntimeQuery query) {
        requireRepository();
        return repository.selectForRuntime(query);
    }

    public GovernedMemorySnapshot verifyProjectFact(GovernedMemoryVerifyCommand command) {
        if (command == null) throw new IllegalArgumentException("GOVERNED_MEMORY_VERIFY_COMMAND_REQUIRED");
        requireRepository();
        GovernedMemorySnapshot before = requireMemory(command.memoryId());
        memoryPolicy.requireProjectFact(before.type());
        if (!repository.verifyProjectFact(command.memoryId(), command.proofRefs())) {
            throw new IllegalStateException("MEMORY_VERSION_CONFLICT：Memory 已变化或不可验证");
        }
        GovernedMemorySnapshot after = requireMemory(command.memoryId());
        repository.recordAudit(
                command.memoryId(),
                "PROJECT_FACT_VERIFIED",
                command.actor(),
                after,
                "");
        return after;
    }

    private String recordConflict(
            Optional<GovernedMemoryHead> existing,
            GovernedMemorySnapshot incoming,
            boolean conflict) {
        if (!conflict) return "";
        GovernedMemoryHead head = existing.orElseThrow();
        repository.markConflict(head.memoryId());
        return repository.recordConflict(
                incoming.scope(),
                incoming.scopeId(),
                incoming.logicalKey(),
                head.memoryId(),
                incoming.memoryId(),
                incoming.createdBy());
    }

    private GovernedMemorySnapshot requireMemory(String memoryId) {
        return repository.findByMemoryId(memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory 不存在：" + memoryId));
    }

    private void requireRepository() {
        if (!repository.available()) {
            throw new IllegalStateException("Memory Store 数据库未配置");
        }
    }

    private String requireMemoryId(String memoryId) {
        String normalized = memoryId == null ? "" : memoryId.trim();
        if (normalized.isBlank()) throw new IllegalStateException("GOVERNED_MEMORY_ID_EMPTY");
        return normalized;
    }
}
