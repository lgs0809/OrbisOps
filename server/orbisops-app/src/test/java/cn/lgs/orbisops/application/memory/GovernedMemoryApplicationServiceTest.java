package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IGovernedMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryHead;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryHashPolicy;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernedMemoryApplicationServiceTest {

    @Test
    void sameNormalizedFactFromAnotherRunAddsSourceWithoutConflictOrNewVersion() {
        IGovernedMemoryRepository repository = mock(IGovernedMemoryRepository.class);
        when(repository.available()).thenReturn(true);
        GovernedMemorySnapshot old = snapshot("memory-old", MemoryType.PROJECT_FACT, "ACTIVE", 1, "legacy-audit-hash");
        var existing = new GovernedMemorySnapshot(old.databaseId(), old.memoryId(), old.scope(), old.scopeId(),
                old.userId(), old.projectId(), old.agentId(), old.sessionId(), old.type(), "mysql-primary",
                "MySQL 主库", "MySQL 主库", old.sourceType(), "previous-run", old.verified(), old.confidence(),
                old.riskLevel(), old.status(), old.version(), old.memoryHash(), old.proofRefs(), old.expiresAt(),
                old.createdBy(), old.idempotencyKey(), old.createdAt(), old.updatedAt());
        when(repository.findLatestHead(any(), anyString(), any(), anyString()))
                .thenReturn(Optional.of(new GovernedMemoryHead("memory-old", 1, "legacy-audit-hash")));
        when(repository.findByMemoryId("memory-old")).thenReturn(Optional.of(existing));
        when(repository.findByMemoryId("memory-1")).thenReturn(Optional.of(existing));

        GovernedMemoryCreationResult result = service(repository, null).create(command("PROJECT_FACT"));

        assertTrue(result.duplicate());
        assertEquals("", result.conflictId());
        assertEquals("ACTIVE", result.snapshot().status());
        assertEquals(1, result.snapshot().version());
        verify(repository, never()).markConflict(anyString());
        verify(repository, never()).insert(any());
    }

    @Test
    void createsTypedMemoryAndPublishesInternalAndExternalAudit() {
        IGovernedMemoryRepository repository = mock(IGovernedMemoryRepository.class);
        GovernedMemoryExternalAuditPort externalAudit = mock(GovernedMemoryExternalAuditPort.class);
        AtomicReference<GovernedMemorySnapshot> inserted = new AtomicReference<>();
        when(repository.available()).thenReturn(true);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.findLatestHead(any(), anyString(), any(), anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return null;
        }).when(repository).insert(any(GovernedMemorySnapshot.class));
        when(repository.findByMemoryId("memory-1")).thenAnswer(invocation ->
                Optional.of(persisted(inserted.get(), 41L)));
        GovernedMemoryApplicationService service = service(repository, externalAudit);

        GovernedMemoryCreationResult result = service.create(command("PROJECT_FACT"));

        assertEquals(false, result.duplicate());
        assertEquals("", result.conflictId());
        assertEquals(41L, result.snapshot().databaseId());
        GovernedMemorySnapshot pending = inserted.get();
        assertEquals(MemoryScope.PROJECT, pending.scope());
        assertEquals(MemoryType.PROJECT_FACT, pending.type());
        assertEquals("MySQL 主库", pending.normalizedContent());
        assertEquals(1, pending.version());
        assertEquals("ACTIVE", pending.status());
        assertEquals("memory-1", pending.memoryId());
        verify(repository).appendVersion(any());
        verify(repository).recordAudit(
                "memory-1", "MEMORY_CREATED", "user-1", result.snapshot(), "");
        verify(externalAudit).recordCreate("demo-project", false, result);
    }

    @Test
    void duplicateReturnsWithoutWriteOrAudit() {
        IGovernedMemoryRepository repository = mock(IGovernedMemoryRepository.class);
        GovernedMemoryExternalAuditPort externalAudit = mock(GovernedMemoryExternalAuditPort.class);
        GovernedMemorySnapshot duplicate = snapshot("memory-old", MemoryType.PROJECT_FACT, "ACTIVE", 2, "hash-old");
        when(repository.available()).thenReturn(true);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(duplicate));
        GovernedMemoryApplicationService service = service(repository, externalAudit);

        GovernedMemoryCreationResult result = service.create(command("PROJECT_FACT"));

        assertEquals(true, result.duplicate());
        assertEquals(duplicate, result.snapshot());
        verify(repository, never()).insert(any());
        verify(repository, never()).appendVersion(any());
        verify(repository, never()).recordAudit(anyString(), anyString(), anyString(), any(), anyString());
        verify(externalAudit, never()).recordCreate(anyString(), anyBoolean(), any());
    }

    @Test
    void conflictMarksOldPointerRecordsConflictAndAuditsCreatedResult() {
        IGovernedMemoryRepository repository = mock(IGovernedMemoryRepository.class);
        GovernedMemoryExternalAuditPort externalAudit = mock(GovernedMemoryExternalAuditPort.class);
        AtomicReference<GovernedMemorySnapshot> inserted = new AtomicReference<>();
        when(repository.available()).thenReturn(true);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.findLatestHead(any(), anyString(), any(), anyString()))
                .thenReturn(Optional.of(new GovernedMemoryHead("memory-old", 3, "different-hash")));
        doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return null;
        }).when(repository).insert(any(GovernedMemorySnapshot.class));
        when(repository.recordConflict(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("mem-conflict-1");
        when(repository.findByMemoryId("memory-1")).thenAnswer(invocation ->
                Optional.of(persisted(inserted.get(), 42L)));
        GovernedMemoryApplicationService service = service(repository, externalAudit);

        GovernedMemoryCreationResult result = service.create(command("PROJECT_FACT"));

        assertEquals("CONFLICT", inserted.get().status());
        assertEquals(4, inserted.get().version());
        assertEquals("mem-conflict-1", result.conflictId());
        verify(repository).markConflict("memory-old");
        verify(repository).recordConflict(
                MemoryScope.PROJECT,
                "demo-project",
                "mysql-primary",
                "memory-old",
                "memory-1",
                "user-1");
        verify(repository).recordAudit(
                "memory-1", "CONFLICT_CREATED", "user-1", result.snapshot(), "mem-conflict-1");
        verify(externalAudit).recordCreate("demo-project", true, result);
    }

    @Test
    void verifiesProjectFactWithCasAndInternalAudit() {
        IGovernedMemoryRepository repository = mock(IGovernedMemoryRepository.class);
        GovernedMemorySnapshot before = snapshot("memory-1", MemoryType.PROJECT_FACT, "ACTIVE", 1, "hash-1");
        GovernedMemorySnapshot after = new GovernedMemorySnapshot(
                before.databaseId(), before.memoryId(), before.scope(), before.scopeId(), before.userId(),
                before.projectId(), before.agentId(), before.sessionId(), before.type(), before.logicalKey(),
                before.content(), before.normalizedContent(), before.sourceType(), before.sourceRunId(),
                true, 0.9D, before.riskLevel(), before.status(), before.version(), before.memoryHash(),
                List.of(Map.of("proofId", "proof-1")), before.expiresAt(), before.createdBy(),
                before.idempotencyKey(), before.createdAt(), before.updatedAt());
        when(repository.available()).thenReturn(true);
        when(repository.findByMemoryId("memory-1")).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.verifyProjectFact("memory-1", List.of(Map.of("proofId", "proof-1"))))
                .thenReturn(true);
        GovernedMemoryApplicationService service = service(repository, null);

        GovernedMemorySnapshot result = service.verifyProjectFact(new GovernedMemoryVerifyCommand(
                "memory-1", List.of(Map.of("proofId", "proof-1")), "user-1"));

        assertTrue(result.verified());
        verify(repository).recordAudit(
                "memory-1", "PROJECT_FACT_VERIFIED", "user-1", after, "");
    }

    @Test
    void unavailableRepositoryAndVerifyConflictFailClosed() {
        IGovernedMemoryRepository unavailable = mock(IGovernedMemoryRepository.class);
        when(unavailable.available()).thenReturn(false);
        GovernedMemoryApplicationService unavailableService = service(unavailable, null);
        assertEquals("Memory Store 数据库未配置",
                assertThrows(IllegalStateException.class,
                        () -> unavailableService.create(command("PROJECT_FACT"))).getMessage());

        IGovernedMemoryRepository repository = mock(IGovernedMemoryRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findByMemoryId("memory-1")).thenReturn(Optional.of(
                snapshot("memory-1", MemoryType.PROJECT_FACT, "ACTIVE", 1, "hash-1")));
        when(repository.verifyProjectFact(anyString(), any())).thenReturn(false);
        GovernedMemoryApplicationService service = service(repository, null);
        assertEquals("MEMORY_VERSION_CONFLICT：Memory 已变化或不可验证",
                assertThrows(IllegalStateException.class,
                        () -> service.verifyProjectFact(new GovernedMemoryVerifyCommand(
                                "memory-1", List.of(), "user-1"))).getMessage());
    }

    private GovernedMemoryApplicationService service(
            IGovernedMemoryRepository repository,
            GovernedMemoryExternalAuditPort externalAudit) {
        when(repository.withLogicalLock(anyString(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        return new GovernedMemoryApplicationService(
                repository,
                new GovernedMemoryPolicy(),
                new GovernedMemoryHashPolicy(),
                () -> "memory-1",
                Clock.fixed(
                        Instant.parse("2026-07-22T02:00:00Z"),
                        ZoneOffset.UTC),
                externalAudit,
                Duration.ofHours(24));
    }

    private GovernedMemoryCreateCommand command(String memoryType) {
        return new GovernedMemoryCreateCommand(
                "PROJECT",
                "demo-project",
                memoryType,
                "MySQL   主库",
                "",
                "mysql-primary",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                "USER_ASSERTED",
                "run-1",
                false,
                0.8D,
                "LOW",
                List.of(),
                "user-1");
    }

    private GovernedMemorySnapshot persisted(GovernedMemorySnapshot pending, long id) {
        return new GovernedMemorySnapshot(
                id,
                pending.memoryId(),
                pending.scope(),
                pending.scopeId(),
                pending.userId(),
                pending.projectId(),
                pending.agentId(),
                pending.sessionId(),
                pending.type(),
                pending.logicalKey(),
                pending.content(),
                pending.normalizedContent(),
                pending.sourceType(),
                pending.sourceRunId(),
                pending.verified(),
                pending.confidence(),
                pending.riskLevel(),
                pending.status(),
                pending.version(),
                pending.memoryHash(),
                pending.proofRefs(),
                pending.expiresAt(),
                pending.createdBy(),
                pending.idempotencyKey(),
                Instant.parse("2026-07-22T02:00:01Z"),
                Instant.parse("2026-07-22T02:00:01Z"));
    }

    private GovernedMemorySnapshot snapshot(
            String memoryId,
            MemoryType type,
            String status,
            int version,
            String hash) {
        return new GovernedMemorySnapshot(
                1L,
                memoryId,
                MemoryScope.PROJECT,
                "demo-project",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                type,
                "mysql-primary",
                "content",
                "normalized",
                "USER_ASSERTED",
                "run-1",
                false,
                0.8D,
                "LOW",
                status,
                version,
                hash,
                List.of(),
                null,
                "user-1",
                "idempotency-1",
                Instant.parse("2026-07-22T01:00:00Z"),
                Instant.parse("2026-07-22T01:00:00Z"));
    }
}
