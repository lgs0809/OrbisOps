package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationPlan;
import cn.lgs.orbisops.application.changepackage.ChangePackageRevisionPlan;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChangePackagePreparationServiceTest {

    @Test
    void prepareReturnsCandidatePlanWithoutPersistingChangePackage() {
        OpsChangePackagePreparationService service = service(
                defaultAgent(),
                defaultBundle());

        ChangePackagePreparationPlan plan = service.prepare(withRuntimeBundle(Map.of(
                "projectId", "demo-project",
                "question", "最近支付失败增加")), "alice");
        Map<String, Object> result = plan.snapshotInput();

        assertEquals("demo-project", plan.projectId());
        assertEquals("current-work-session-agent",
                result.get("preparationAgentId"));
        assertNotEquals(ChangePackageType.MCP_OPERATION_PACKAGE.name(), result.get("packageType"));
        assertTrue(String.valueOf(result.get("evidence")).contains("NOT_SUPPORTED"));
    }

    @Test
    void requestCannotOverrideFrozenWorkSessionAgent() {
        OpsChangePackagePreparationService service = service(
                defaultAgent(),
                defaultBundle());

        assertThrows(SecurityException.class, () -> service.prepare(withRuntimeBundle(Map.of(
                "projectId", "demo-project",
                "preparationGraphId", "custom-prep")), "alice"));
    }

    @Test
    void reviseReturnsValidatedCandidatePatchInsteadOfCallingPersistence() {
        OpsChangePackagePreparationService service = service(
                defaultAgent(),
                defaultBundle());

        ChangePackageRevisionPlan revision = service.revise("cp-1", withRuntimeBundle(Map.of(
                "projectId", "demo-project",
                "summary", "refined")), "alice");

        assertEquals("refined", revision.changes().get("summary"));
        assertEquals("ctx-test", revision.changes().get("contextBundleId"));
        assertEquals("ctx-hash", revision.changes().get("contextBundleHash"));
    }

    @Test
    void prepareAndRevisionRejectMalformedFrozenBusinessCriteriaBeforeProducingPlan() {
        OpsChangePackagePreparationService service = service(defaultAgent(), defaultBundle());
        Map<String, Object> criteria = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("kind", "OBSERVABILITY_SLO_V1"), Map.entry("serviceId", "orders"),
                Map.entry("environment", "test"), Map.entry("expectedVersion", "v2"),
                Map.entry("baselineVersion", "v1"), Map.entry("resourceIdentity", "service://orders/test"),
                Map.entry("routeDefinition", "/orders/id"), Map.entry("collectionDefinition", Map.of("description", "raw scrapes")),
                Map.entry("changeKind", "RELEASE")));
        var request = withRuntimeBundle(Map.of("projectId", "demo-project", "verificationCriteria", List.of(criteria)));
        assertEquals("CHANGE_VERIFICATION_CRITERIA_STRING_REQUIRED:collectionDefinition",
                assertThrows(IllegalArgumentException.class, () -> service.prepare(request, "alice")).getMessage());
        assertEquals("CHANGE_VERIFICATION_CRITERIA_STRING_REQUIRED:collectionDefinition",
                assertThrows(IllegalArgumentException.class, () -> service.revise("cp-1", request, "alice")).getMessage());
        criteria.put("collectionDefinition", "raw-scrapes-v1");
        assertEquals(List.of(criteria), service.prepare(request, "alice").snapshotInput().get("verificationCriteria"));
        assertEquals(List.of(criteria), service.revise("cp-1", request, "alice").changes().get("verificationCriteria"));
    }

    @Test
    void forgedContextBundleHashIsRejectedBeforePlanGeneration() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsRuntimeContextBundleAdapter> bundleProvider =
                mock(ObjectProvider.class);
        OpsRuntimeContextBundleAdapter bundleService =
                mock(OpsRuntimeContextBundleAdapter.class);
        when(bundleProvider.getIfAvailable()).thenReturn(bundleService);
        when(bundleService.requireBundle("ctx-test", "forged"))
                .thenThrow(new SecurityException("hash mismatch"));
        OpsChangePackagePreparationService service = service(
                defaultAgent(),
                bundleProvider,
                evidenceProvider());

        assertThrows(SecurityException.class, () -> service.prepare(Map.of(
                "projectId", "demo-project",
                "contextBundleId", "ctx-test",
                "contextBundleHash", "forged"), "alice"));
    }

    @SuppressWarnings("unchecked")
    private OpsChangePackagePreparationService service(
            OpsAgentDefinition definition,
            Map<String, Object> bundle) {
        Map<String, Object> authoritative = new LinkedHashMap<>(bundle);
        authoritative.putIfAbsent("projectId", "demo-project");
        authoritative.putIfAbsent("runId", "run-1");
        authoritative.putIfAbsent("sessionId", "session-1");
        authoritative.putIfAbsent("agentId", definition.getAgentId());
        authoritative.putIfAbsent("agentVersion", definition.getVersion());
        ObjectProvider<OpsRuntimeContextBundleAdapter> bundleProvider =
                mock(ObjectProvider.class);
        OpsRuntimeContextBundleAdapter bundleService =
                mock(OpsRuntimeContextBundleAdapter.class);
        when(bundleProvider.getIfAvailable()).thenReturn(bundleService);
        when(bundleService.requireBundle(
                eq(String.valueOf(bundle.get("contextBundleId"))),
                eq(String.valueOf(bundle.get("contextBundleHash")))))
                .thenReturn(authoritative);
        return service(definition, bundleProvider, evidenceProvider());
    }

    @SuppressWarnings("unchecked")
    private OpsChangePackagePreparationService service(
            OpsAgentDefinition definition,
            ObjectProvider<OpsRuntimeContextBundleAdapter> bundleProvider,
            ObjectProvider<OpsEvidenceStore> evidenceProvider) {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve(definition.getAgentId())).thenReturn(definition);
        ObjectProvider<ProjectDefinitionApplicationService> projectProvider =
                mock(ObjectProvider.class);
        ObjectProvider<OpsConfigAuditService> auditProvider =
                mock(ObjectProvider.class);
        when(projectProvider.getIfAvailable()).thenReturn(null);
        when(auditProvider.getIfAvailable()).thenReturn(null);
        return new OpsChangePackagePreparationService(
                registry,
                projectProvider,
                auditProvider,
                bundleProvider,
                evidenceProvider,
                null);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OpsEvidenceStore> evidenceProvider() {
        ObjectProvider<OpsEvidenceStore> evidenceProvider =
                mock(ObjectProvider.class);
        OpsEvidenceStore evidenceStore = mock(OpsEvidenceStore.class);
        when(evidenceProvider.getIfAvailable()).thenReturn(evidenceStore);
        when(evidenceStore.listForRun(any(), any(), anyInt()))
                .thenReturn(List.of());
        return evidenceProvider;
    }

    private Map<String, Object> withRuntimeBundle(Map<String, Object> request) {
        Map<String, Object> data = new LinkedHashMap<>(request);
        Map<String, Object> bundle = defaultBundle();
        data.put("contextBundleId", bundle.get("contextBundleId"));
        data.put("contextBundleHash", bundle.get("contextBundleHash"));
        data.putIfAbsent("runId", "run-1");
        data.putIfAbsent("sessionId", "session-1");
        return data;
    }

    private Map<String, Object> defaultBundle() {
        return Map.of(
                "contextBundleId", "ctx-test",
                "contextBundleHash", "ctx-hash",
                "memoryContextRefs", List.of("session:s1", "project:demo-project"),
                "memoryContextHash", "mem-hash",
                "compressedMemorySummary", "summary",
                "memoryInjectionVersion", "context-bundle-v1",
                "usedSkillVersionRefs", List.of(Map.of(
                        "skillId", "historical-skill-1",
                        "version", 1,
                        "skillHash", "skill-hash",
                        "scope", "PROJECT",
                        "statusAtUse", "ACTIVE")),
                "usedSkillRefsHash", "skill-refs-hash",
                "toolsetBoundaryHash", "toolset-hash",
                "runtimeBoundaryHash", "runtime-hash");
    }

    private OpsAgentDefinition defaultAgent() {
        return OpsAgentDefinition.builder()
                .agentId("current-work-session-agent")
                .version(1)
                .name("current")
                .build();
    }
}
