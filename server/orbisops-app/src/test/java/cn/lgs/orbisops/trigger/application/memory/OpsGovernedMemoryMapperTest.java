package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.CaptureMemoryResult;
import cn.lgs.orbisops.application.memory.GovernedMemoryCreateCommand;
import cn.lgs.orbisops.application.memory.GovernedMemoryCreationResult;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsGovernedMemoryMapperTest {

    private final OpsGovernedMemoryMapper mapper = new OpsGovernedMemoryMapper();

    @Test
    void mapsLegacyRequestToTypedCreateCommand() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("scopeType", "PROJECT");
        request.put("scopeId", "demo-project");
        request.put("memoryType", "PROJECT_FACT");
        request.put("content", "  original content  ");
        request.put("normalizedContent", "normalized");
        request.put("logicalKey", "logical");
        request.put("userId", "user-1");
        request.put("projectId", "demo-project");
        request.put("agentId", "agent-1");
        request.put("sessionId", "session-1");
        request.put("sourceType", "USER_ASSERTED");
        request.put("sourceRunId", "run-1");
        request.put("verified", true);
        request.put("confidence", 0.88D);
        request.put("riskLevel", "MEDIUM");
        request.put("proofRefs", List.of(Map.of("proofId", "proof-1")));
        GovernedMemoryCreateCommand command = mapper.createCommand(request, "user-1");

        assertEquals("PROJECT", command.scopeType());
        assertEquals("PROJECT_FACT", command.memoryType());
        assertEquals("  original content  ", command.content());
        assertEquals(true, command.verified());
        assertEquals(0.88D, command.confidence());
        assertEquals("proof-1", command.proofRefs().get(0).get("proofId"));
        assertEquals("user-1", command.actor());
    }

    @Test
    void requiredLegacyFieldsKeepHistoricalMessages() {
        assertEquals("scopeType 不能为空",
                assertThrows(IllegalArgumentException.class,
                        () -> mapper.createCommand(Map.of(), "user-1")).getMessage());
        assertEquals("Memory content 不能为空",
                assertThrows(IllegalArgumentException.class,
                        () -> mapper.createCommand(Map.of(
                                "scopeType", "PROJECT",
                                "scopeId", "demo-project",
                                "memoryType", "PROJECT_FACT",
                                "content", " "), "user-1")).getMessage());
    }

    @Test
    void mapsTypedSnapshotAndConflictToLegacyView() {
        Map<String, Object> view = mapper.creationView(
                GovernedMemoryCreationResult.created(snapshot(), "mem-conflict-1"));

        assertEquals(8L, view.get("id"));
        assertEquals("memory-1", view.get("memoryId"));
        assertEquals("PROJECT", view.get("scopeType"));
        assertEquals("PROJECT_FACT", view.get("memoryType"));
        assertEquals(1, view.get("verified"));
        assertEquals("[{\"proofId\":\"proof-1\"}]", view.get("proofRefsJson"));
        assertEquals("2026-07-22T02:00:00Z", view.get("expiresAt"));
        assertEquals("mem-conflict-1", view.get("conflictId"));
    }

    @Test
    void mapsExplicitCaptureResultWithoutForegroundLearningPayload() {
        Map<String, Object> view = mapper.captureView(new CaptureMemoryResult(
                GovernedMemoryCreationResult.created(snapshot(), "")));

        assertEquals("memory-1", view.get("memoryId"));
        assertEquals(false, view.containsKey("skillEvolutionSignal"));
    }

    @Test
    void mapsRuntimeAndVerifyContracts() {
        assertEquals(50, mapper.runtimeQuery("u", "p", "s", "run", 100).limit());
        assertEquals("memory-1",
                mapper.verifyCommand(" memory-1 ", List.of(), " user-1 ").memoryId());
        assertEquals(List.of(), mapper.views(null));
    }

    private GovernedMemorySnapshot snapshot() {
        return new GovernedMemorySnapshot(
                8L,
                "memory-1",
                MemoryScope.PROJECT,
                "demo-project",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                MemoryType.PROJECT_FACT,
                "mysql-primary",
                "content",
                "normalized",
                "USER_ASSERTED",
                "run-1",
                true,
                0.9D,
                "LOW",
                "CONFLICT",
                2,
                "hash-1",
                List.of(Map.of("proofId", "proof-1")),
                Instant.parse("2026-07-22T02:00:00Z"),
                "user-1",
                "idempotency-1",
                Instant.parse("2026-07-22T01:00:00Z"),
                Instant.parse("2026-07-22T01:30:00Z"));
    }
}
