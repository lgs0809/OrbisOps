package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsPreparationContextBundleServiceTest {

    @Test
    void latestCompletedBundleBindsCanonicalIdentityForSessionPreparation() {
        OpsRuntimeContextBundleAdapter adapter = mock(OpsRuntimeContextBundleAdapter.class);
        when(adapter.latestCompletedBundleForSession("session-1", "project-1", "alice"))
                .thenReturn(Map.of(
                        "contextBundleId", "ctx-1",
                        "contextBundleHash", "ctx-hash",
                        "runId", "run-1"));
        OpsPreparationContextBundleService service = service(adapter);

        Map<String, Object> bound = service.bindLatestForSession(
                "session-1",
                Map.of("projectId", "project-1", "question", "investigate"),
                "alice");

        assertEquals("session-1", bound.get("sessionId"));
        assertEquals("ctx-1", bound.get("contextBundleId"));
        assertEquals("ctx-hash", bound.get("contextBundleHash"));
        assertEquals("run-1", bound.get("runId"));
        assertEquals("run-1", bound.get("sourceRunId"));
    }

    @Test
    void projectRunAndSessionScopeMustMatchAuthoritativeBundle() {
        OpsRuntimeContextBundleAdapter adapter = mock(OpsRuntimeContextBundleAdapter.class);
        when(adapter.requireBundle("ctx-1", "ctx-hash")).thenReturn(authoritativeBundle());
        OpsPreparationContextBundleService service = service(adapter);

        assertThrows(SecurityException.class, () -> service.requireForRequest(Map.of(
                "projectId", "project-2",
                "runId", "run-1",
                "sessionId", "session-1",
                "contextBundleId", "ctx-1",
                "contextBundleHash", "ctx-hash")));
        assertThrows(SecurityException.class, () -> service.requireForRequest(Map.of(
                "projectId", "project-1",
                "runId", "run-2",
                "sessionId", "session-1",
                "contextBundleId", "ctx-1",
                "contextBundleHash", "ctx-hash")));
        assertThrows(SecurityException.class, () -> service.requireForRequest(Map.of(
                "projectId", "project-1",
                "runId", "run-1",
                "sessionId", "session-2",
                "contextBundleId", "ctx-1",
                "contextBundleHash", "ctx-hash")));
    }

    @Test
    void revisionUsesOnlyAuthoritativeBundleValues() {
        OpsRuntimeContextBundleAdapter adapter = mock(OpsRuntimeContextBundleAdapter.class);
        when(adapter.requireBundle("ctx-1", "ctx-hash")).thenReturn(authoritativeBundle());
        OpsPreparationContextBundleService service = service(adapter);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", "project-1");
        request.put("runId", "run-1");
        request.put("sessionId", "session-1");
        request.put("contextBundleId", "ctx-1");
        request.put("contextBundleHash", "ctx-hash");
        request.put("memoryContextHash", "forged-memory-hash");
        request.put("usedSkillRefsHash", "forged-skill-hash");
        request.put("summary", "refined");

        Map<String, Object> revision = service.validateRevision(request);

        assertEquals("authoritative-memory-hash", revision.get("memoryContextHash"));
        assertEquals("authoritative-skill-hash", revision.get("usedSkillRefsHash"));
        assertEquals("toolset-hash", revision.get("toolsetBoundaryHash"));
        assertEquals("runtime-hash", revision.get("runtimeBoundaryHash"));
        assertEquals("approval-hash", revision.get("contextApprovalBoundaryHash"));
        assertEquals("refined", revision.get("summary"));
        verify(adapter).requireBundle("ctx-1", "ctx-hash");
    }

    @Test
    void missingFrozenAgentVersionFailsClosedBeforePreparation() {
        OpsRuntimeContextBundleAdapter adapter = mock(OpsRuntimeContextBundleAdapter.class);
        Map<String, Object> incomplete = new LinkedHashMap<>(authoritativeBundle());
        incomplete.remove("agentVersion");
        when(adapter.requireBundle("ctx-1", "ctx-hash")).thenReturn(incomplete);
        OpsPreparationContextBundleService service = service(adapter);
        Map<String, Object> request = Map.of(
                "projectId", "project-1",
                "runId", "run-1",
                "sessionId", "session-1",
                "contextBundleId", "ctx-1",
                "contextBundleHash", "ctx-hash");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.validateRevision(request));

        assertEquals(true, error.getMessage().startsWith("CONTEXT_BUNDLE_AGENT_VERSION_REQUIRED"));
    }

    @Test
    void skillReferencesAreProjectedFromAuthoritativeTypedItems() {
        OpsPreparationContextBundleService service = service(null);
        Map<String, Object> bundle = authoritativeBundle();

        assertEquals(List.of("skill-a"), service.skillRefValues(bundle, "skillId"));
        assertEquals(List.of(3), service.skillRefValues(bundle, "version"));
        assertEquals(List.of("skill-hash"), service.skillRefValues(bundle, "skillHash"));
    }

    private OpsPreparationContextBundleService service(OpsRuntimeContextBundleAdapter adapter) {
        return new OpsPreparationContextBundleService(() -> adapter);
    }

    private Map<String, Object> authoritativeBundle() {
        return Map.ofEntries(
                Map.entry("contextBundleId", "ctx-1"),
                Map.entry("contextBundleHash", "ctx-hash"),
                Map.entry("projectId", "project-1"),
                Map.entry("runId", "run-1"),
                Map.entry("sessionId", "session-1"),
                Map.entry("agentId", "agent-1"),
                Map.entry("agentVersion", 3),
                Map.entry("memoryContextRefs", List.of("project:project-1")),
                Map.entry("memoryContextHash", "authoritative-memory-hash"),
                Map.entry("usedSkillVersionRefs", List.of(Map.of(
                        "skillId", "skill-a",
                        "version", 3,
                        "skillHash", "skill-hash"))),
                Map.entry("usedSkillRefsHash", "authoritative-skill-hash"),
                Map.entry("toolsetRefs", List.of("toolset-a")),
                Map.entry("policyRefs", List.of("policy-a")),
                Map.entry("policyHash", "policy-hash"),
                Map.entry("toolsetBoundaryHash", "toolset-hash"),
                Map.entry("runtimeBoundaryHash", "runtime-hash"),
                Map.entry("approvalBoundaryHash", "approval-hash"));
    }
}
