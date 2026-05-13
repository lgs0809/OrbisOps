package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.GovernedMemoryCreationResult;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsGovernedMemoryApplicationAdaptersTest {

    @Test
    void externalAuditAdapterPreservesLegacyAuditProjection() {
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsGovernedMemoryMapper mapper = new OpsGovernedMemoryMapper();
        OpsGovernedMemoryExternalAuditAdapter adapter =
                new OpsGovernedMemoryExternalAuditAdapter(auditService, mapper);
        GovernedMemoryCreationResult result = GovernedMemoryCreationResult.created(
                snapshot(),
                "mem-conflict-1");

        adapter.recordCreate("demo-project", true, result);

        verify(auditService).record(
                eq("demo-project"),
                eq("memory"),
                eq("conflict"),
                eq("memory-1"),
                isNull(),
                eq(mapper.creationView(result)));
    }

    private GovernedMemorySnapshot snapshot() {
        return new GovernedMemorySnapshot(
                7L,
                "memory-1",
                MemoryScope.PROJECT,
                "demo-project",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                MemoryType.PROJECT_FACT,
                "logical",
                "content",
                "normalized",
                "USER_ASSERTED",
                "run-1",
                false,
                0.8D,
                "LOW",
                "CONFLICT",
                2,
                "hash-1",
                List.of(Map.of("proofId", "proof-1")),
                null,
                "user-1",
                "idempotency-1",
                Instant.parse("2026-07-22T01:00:00Z"),
                Instant.parse("2026-07-22T01:30:00Z"));
    }
}
