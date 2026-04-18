package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageSnapshotFactory;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackagePreparationProofAdapterTest {

    private static final String OUTPUT_HASH = "a".repeat(64);

    @Test
    void recordsOwningPrepareMcpDryRunFromServerOwnedTrustedEvidence() {
        OpsTrustedProofService proofs = mock(OpsTrustedProofService.class);
        ObjectProvider<OpsTrustedProofService> provider = provider(proofs);
        OpsChangePackagePreparationProofAdapter adapter = new OpsChangePackagePreparationProofAdapter(provider);
        ChangePackageSnapshot snapshot = snapshot("PRE_APPROVAL_WORKFLOW:mcp.service-control:restart_service_dry_run");

        adapter.recordExecutionProofs(
                "cp-1", 1, snapshot.packageHash(), "demo-project", snapshot, "alice");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(proofs).recordTrustedProof(payload.capture(), eq("alice"));
        Map<String, Object> recorded = payload.getValue();
        assertEquals("cp-1", recorded.get("packageId"));
        assertEquals(1, recorded.get("packageVersion"));
        assertEquals(snapshot.packageHash(), recorded.get("packageHash"));
        assertEquals("HIGH", recorded.get("riskLevel"));
        assertEquals("MCP_DRY_RUN", recorded.get("proofType"));
        assertEquals("TOOL_EXECUTED", recorded.get("source"));
        assertEquals("tool-result-1", recorded.get("externalRunId"));
        assertEquals(OUTPUT_HASH, recorded.get("outputHash"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) recorded.get("metadata");
        assertEquals("service-control", metadata.get("mcpId"));
        assertEquals("restart_service_dry_run", metadata.get("toolName"));
        assertEquals("evidence-1", metadata.get("sourceEvidenceId"));
        assertTrue(String.valueOf(recorded.get("proofId")).startsWith("proof-"));
    }

    @Test
    void doesNotTrustSpoofedOrNonExecutionEvidence() {
        OpsTrustedProofService proofs = mock(OpsTrustedProofService.class);
        OpsChangePackagePreparationProofAdapter adapter =
                new OpsChangePackagePreparationProofAdapter(provider(proofs));
        ChangePackageSnapshot snapshot = snapshot("USER_INPUT");

        adapter.recordExecutionProofs(
                "cp-1", 1, snapshot.packageHash(), "demo-project", snapshot, "alice");

        verify(proofs, never()).recordTrustedProof(org.mockito.ArgumentMatchers.any(), eq("alice"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OpsTrustedProofService> provider(OpsTrustedProofService proofs) {
        ObjectProvider<OpsTrustedProofService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(proofs);
        return provider;
    }

    private ChangePackageSnapshot snapshot(String evidenceSource) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("projectId", "demo-project");
        source.put("packageType", "MCP_OPERATION_PACKAGE");
        source.put("status", "READY_FOR_REVIEW");
        source.put("riskLevel", "HIGH");
        source.put("contextBundleId", "bundle-1");
        source.put("contextBundleHash", "bundle-hash");
        source.put("usedSkillVersionRefs", List.of());
        source.put("usedSkillRefsHash", "skill-refs-hash");
        source.put("toolsetBoundaryHash", "toolset-boundary-hash");
        source.put("runtimeBoundaryHash", "runtime-boundary-hash");
        source.put("approvalBoundary", Map.of("projectId", "demo-project"));
        source.put("mcpSteps", List.of(Map.ofEntries(
                Map.entry("operationId", "validate-restart"),
                Map.entry("mcpId", "service-control"),
                Map.entry("toolName", "restart_service_dry_run"),
                Map.entry("remoteToolName", "restart_service_dry_run"),
                Map.entry("effectType", "DRY_RUN"),
                Map.entry("effectScope", "VALIDATION_SANDBOX"),
                Map.entry("mutability", "READ_ONLY"),
                Map.entry("riskLevel", "MEDIUM"),
                Map.entry("writesTargetResource", false))));
        source.put("evidence", Map.of("trustedEvidence", List.of(Map.ofEntries(
                Map.entry("evidenceId", "evidence-1"),
                Map.entry("runId", "run-1"),
                Map.entry("sourceType", "MCP"),
                Map.entry("verified", true),
                Map.entry("toolResultId", "tool-result-1"),
                Map.entry("outputHash", OUTPUT_HASH),
                Map.entry("fullOutputRef", "db:tool-result-1"),
                Map.entry("metadata", Map.of(
                        "source", evidenceSource,
                        "toolName", "restart_service_dry_run"))))));
        return new ChangePackageSnapshotFactory().create("cp-1", 1, source, "alice");
    }
}
