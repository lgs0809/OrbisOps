package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleAuditPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleCreateApplicationService;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleCreateCommand;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleQueryApplicationService;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextCanarySkillPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextSkillSelectionPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextSkillSelectionRequest;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextToolsetPort;
import cn.lgs.orbisops.domain.runtime.contextbundle.adapter.repository.IRuntimeContextBundleRepository;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleLayerInput;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextCanarySkillSnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextSkillSelection;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolPolicySnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolRisk;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolsetSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextBundleApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T02:00:00Z");

    @Test
    void createPersistsAuthoritativeHashesBeforeAudit() {
        InMemoryRepository repository = new InMemoryRepository();
        List<RuntimeContextBundleSnapshot> audited = new ArrayList<>();
        RuntimeContextBundleCreateApplicationService service = service(
                repository,
                selection(List.of(skillRef("catalog-skill", "QUERY_RELEVANCE"))),
                List.of(),
                List.of(toolsetRef()),
                audited);

        RuntimeContextBundleSnapshot saved = service.create(command(
                List.of(Map.of("memoryId", "mem-1")),
                List.of(),
                6));

        assertEquals("ctx-1", saved.bundleId());
        assertTrue(saved.bundleHash().length() >= 32);
        assertTrue(saved.usedSkillRefsHash().length() >= 32);
        assertTrue(saved.toolsetBoundaryHash().length() >= 32);
        assertTrue(saved.runtimeBoundaryHash().length() >= 32);
        assertEquals(saved, repository.saved);
        assertEquals(List.of(saved), audited);
        assertEquals("catalog-skill",
                saved.usedSkillVersionRefs().get(0).get("skillId"));
        assertEquals("mem-1", saved.memoryRefs().get(0).get("memoryId"));
    }

    @Test
    void explicitSkillRefsAreNotTruncatedByAutomaticLimit() {
        List<Map<String, Object>> explicit = List.of(
                skillRef("explicit-one", "REQUESTED_ACTIVE_SKILL"),
                skillRef("explicit-two", "REQUESTED_ACTIVE_SKILL"));
        RuntimeContextBundleCreateApplicationService service = service(
                new InMemoryRepository(), selection(explicit), List.of(), List.of(), new ArrayList<>());

        RuntimeContextBundleSnapshot saved = service.create(command(List.of(),
                List.of("explicit-one", "explicit-two"), 1));

        assertEquals(2, saved.usedSkillVersionRefs().size());
        assertEquals(List.of("explicit-one", "explicit-two"), saved.usedSkillVersionRefs().stream()
                .map(ref -> String.valueOf(ref.get("skillId")))
                .toList());
    }

    @Test
    void canaryIsInsertedOnceBeforeAutomaticRelevantSkills() {
        List<Map<String, Object>> selected = List.of(
                skillRef("relevant-one", "QUERY_RELEVANCE"),
                skillRef("relevant-two", "QUERY_RELEVANCE"));
        RuntimeContextBundleCreateApplicationService service = service(
                new InMemoryRepository(),
                selection(selected),
                List.of(canaryRef("canary-one"), canaryRef("canary-one")),
                List.of(),
                new ArrayList<>());

        RuntimeContextBundleSnapshot saved = service.create(command(List.of(), List.of(), 2));

        assertEquals(List.of("canary-one", "relevant-one"), saved.usedSkillVersionRefs().stream()
                .map(ref -> String.valueOf(ref.get("skillId")))
                .toList());
    }

    @Test
    void persistenceFailurePreventsAuditAndFalseSuccess() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.saveFailure = new IllegalStateException("database unavailable");
        List<RuntimeContextBundleSnapshot> audited = new ArrayList<>();
        RuntimeContextBundleCreateApplicationService service = service(
                repository, selection(List.of()), List.of(), List.of(), audited);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.create(command(List.of(), List.of(), 6)));

        assertEquals("database unavailable", error.getMessage());
        assertTrue(audited.isEmpty());
    }

    @Test
    void requireRejectsForgedHashAndReturnsStoredCompatiblePayload() {
        InMemoryRepository repository = new InMemoryRepository();
        RuntimeContextBundleCreateApplicationService create = service(
                repository, selection(List.of()), List.of(), List.of(), new ArrayList<>());
        RuntimeContextBundleSnapshot stored = create.create(command(List.of(), List.of(), 6));
        RuntimeContextBundleQueryApplicationService query =
                new RuntimeContextBundleQueryApplicationService(repository);

        SecurityException error = assertThrows(SecurityException.class,
                () -> query.require("ctx-1", "forged-hash"));
        RuntimeContextBundleSnapshot required = query.require("ctx-1", stored.bundleHash());

        assertTrue(error.getMessage().contains("hash 不匹配"));
        assertEquals(stored.bundleHash(), required.compatiblePayload().get("contextBundleHash"));
        assertEquals("ctx-1", required.compatiblePayload().get("contextBundleId"));
    }

    @Test
    void recursiveCanonicalHashIgnoresMapInsertionOrder() {
        cn.lgs.orbisops.domain.runtime.contextbundle.service.RuntimeContextBundlePolicy policy =
                new cn.lgs.orbisops.domain.runtime.contextbundle.service.RuntimeContextBundlePolicy();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", Map.of("b", 2, "a", 1));
        first.put("a", List.of(Map.of("d", 4, "c", 3)));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", List.of(Map.of("c", 3, "d", 4)));
        second.put("z", Map.of("a", 1, "b", 2));

        assertEquals(policy.hashObject(first), policy.hashObject(second));
        assertNotEquals(policy.hashObject(first), policy.hashObject(Map.of("z", Map.of("a", 2))));
    }

    private RuntimeContextBundleCreateApplicationService service(
            InMemoryRepository repository,
            RuntimeContextSkillSelection selection,
            List<RuntimeContextCanarySkillSnapshot> canaryRefs,
            List<RuntimeContextToolsetSnapshot> toolsets,
            List<RuntimeContextBundleSnapshot> audited) {
        RuntimeContextSkillSelectionPort skillPort = request -> selection;
        RuntimeContextCanarySkillPort canaryPort = (projectId, agentId, runId) -> canaryRefs;
        RuntimeContextToolsetPort toolsetPort = (projectId, actor) -> toolsets;
        RuntimeContextBundleAuditPort auditPort = audited::add;
        return new RuntimeContextBundleCreateApplicationService(
                repository, skillPort, canaryPort, toolsetPort, auditPort);
    }

    private RuntimeContextBundleCreateCommand command(
            List<Map<String, Object>> memoryRefs,
            List<String> requestedSkills,
            int limit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scene", "OPS_TROUBLESHOOTING");
        metadata.put("serviceId", "orders");
        metadata.put("environment", "prod");
        metadata.put("approvalBoundary", Map.of("required", true));
        return new RuntimeContextBundleCreateCommand(
                new RuntimeContextBundleLayerInput(
                        "ctx-1",
                        "run-1",
                        "session-1",
                        "project-1",
                        "agent-1",
                        "alice",
                        "check order errors",
                        "memory",
                        metadata,
                        NOW),
                memoryRefs,
                requestedSkills,
                limit);
    }

    private RuntimeContextSkillSelection selection(List<Map<String, Object>> selected) {
        return new RuntimeContextSkillSelection(
                selected,
                selected,
                List.of(),
                selected.size(),
                selected.size());
    }

    private Map<String, Object> skillRef(String skillId, String reason) {
        return Map.of(
                "skillId", skillId,
                "version", 5,
                "skillHash", "skill-hash-" + skillId,
                "packageHash", "package-hash-" + skillId,
                "artifactHashes", Map.of("SKILL.md", "artifact-hash-" + skillId),
                "scope", "PROJECT",
                "statusAtUse", "ACTIVE",
                "selectedReason", reason);
    }

    private RuntimeContextCanarySkillSnapshot canaryRef(String skillId) {
        return new RuntimeContextCanarySkillSnapshot(
                skillId,
                5,
                "skill-hash-" + skillId,
                "candidate-" + skillId,
                "release-" + skillId,
                "project-1",
                "agent-1");
    }

    private RuntimeContextToolsetSnapshot toolsetRef() {
        return new RuntimeContextToolsetSnapshot(
                "toolset-1",
                "MCP",
                true,
                List.of(new RuntimeContextToolPolicySnapshot(
                        "query",
                        "MCP",
                        true,
                        false,
                        false,
                        false,
                        false,
                        RuntimeContextToolRisk.LOW,
                        "{}")));
    }

    private static final class InMemoryRepository implements IRuntimeContextBundleRepository {
        private RuntimeContextBundleSnapshot saved;
        private RuntimeException saveFailure;

        @Override
        public RuntimeContextBundleSnapshot save(RuntimeContextBundleSnapshot snapshot) {
            if (saveFailure != null) throw saveFailure;
            saved = snapshot;
            return snapshot;
        }

        @Override
        public Optional<RuntimeContextBundleSnapshot> find(String bundleId) {
            return saved != null && saved.bundleId().equals(bundleId)
                    ? Optional.of(saved)
                    : Optional.empty();
        }

        @Override
        public Optional<RuntimeContextBundleSnapshot> latestForSession(String sessionId, String projectId) {
            return Optional.ofNullable(saved);
        }

        @Override
        public Optional<RuntimeContextBundleSnapshot> latestCompletedForSession(
                String sessionId,
                String projectId,
                String actor) {
            return Optional.ofNullable(saved);
        }
    }
}
