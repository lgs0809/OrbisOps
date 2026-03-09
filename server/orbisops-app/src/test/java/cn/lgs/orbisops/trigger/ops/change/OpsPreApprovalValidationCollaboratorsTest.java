package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageQueryPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsPreApprovalValidationCollaboratorsTest {

    @Test
    void structuredReaderKeepsFastJsonLegacyCompatibility() {
        OpsPreApprovalStructuredValueReader reader =
                new OpsPreApprovalStructuredValueReader();

        assertEquals("value", reader.object("{\"key\":\"value\"}").get("key"));
        assertEquals("op-1", reader.operations(
                "[{\"operationId\":\"op-1\",\"toolName\":\"validate\"}]")
                .get(0).get("operationId"));
        assertEquals("op-2", reader.operations(
                "{\"steps\":[{\"operationId\":\"op-2\"}]}")
                .get(0).get("operationId"));
        assertEquals(List.of("a", "b"), reader.stringList("[\"a\",\"b\"]"));
        assertEquals("mvn test", reader.firstString("[\"\",\"mvn test\"]"));
    }

    @Test
    void snapshotReaderLoadsOnlyCurrentVersionAndSupportsSnapshotAlias() {
        ChangePackageQueryPort port = mock(ChangePackageQueryPort.class);
        ChangePackageQueryService queryService = new ChangePackageQueryService(port);
        when(port.detail("cp-1")).thenReturn(Map.of(
                "packageId", "cp-1",
                "version", 2,
                "package_hash", "hash-2"));
        when(port.versions("cp-1")).thenReturn(List.of(
                Map.of("version", 1, "snapshot_json", "{\"packageType\":\"MANUAL_REQUIRED\"}"),
                Map.of("version", 2, "snapshot", Map.of(
                        "packageType", "MCP_OPERATION_PACKAGE",
                        "mcpStepsJson", "[{\"operationId\":\"op-2\",\"toolName\":\"restart_service_dry_run\"}]"))));

        OpsPreApprovalSnapshotReader.ValidationSnapshot snapshot =
                new OpsPreApprovalSnapshotReader(queryService).load("cp-1", 2);

        assertEquals(2, snapshot.version());
        assertEquals("hash-2", snapshot.packageHash());
        assertEquals("MCP_OPERATION_PACKAGE", snapshot.snapshot().get("packageType"));
        assertEquals("op-2", new OpsPreApprovalStructuredValueReader()
                .operations(snapshot.snapshot().get("mcpStepsJson"))
                .get(0).get("operationId"));
        assertThrows(IllegalStateException.class,
                () -> new OpsPreApprovalSnapshotReader(queryService).load("cp-1", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.snapshot().put("packageType", "CHANGED"));
    }

    @Test
    void proofServiceDelegatesVerificationAndBuildsStableReferences() {
        OpsTrustedProofService trustedProofService = mock(OpsTrustedProofService.class);
        when(trustedProofService.verifyTrustedProof(
                "project-1", "cp-1", 3, "hash-3", "HIGH",
                "MCP_DRY_RUN", "run-1"))
                .thenReturn(true);
        when(trustedProofService.recordTrustedProof(any(), eq("alice")))
                .thenReturn(Map.of(
                        "proofId", "proof-1",
                        "proofType", "MCP_DRY_RUN",
                        "externalRunId", "run-1",
                        "metadataJson", "{\"outputHash\":\"out-1\"}"));
        OpsPreApprovalProofService service =
                new OpsPreApprovalProofService(trustedProofService);
        Map<String, Object> snapshot = Map.of(
                "projectId", "project-1",
                "packageId", "cp-1",
                "version", 3,
                "packageHash", "hash-3",
                "riskLevel", "HIGH");

        assertTrue(service.trusted("MCP_DRY_RUN", snapshot, "run-1"));
        Map<String, Object> proof = service.record(
                snapshot,
                "HIGH",
                "MCP_DRY_RUN",
                "TOOL_EXECUTED",
                "run-1",
                Map.of("outputHash", "out-1"),
                "alice");
        Map<String, Object> ref = service.sourceRef(proof);

        assertEquals("MCP_DRY_RUN", ref.get("proofType"));
        assertEquals("proof-1", ref.get("proofId"));
        assertEquals("out-1", ref.get("outputHash"));
        verify(trustedProofService).recordTrustedProof(any(), eq("alice"));
    }

    @Test
    void reportFactoryFreezesFactsAndKeepsRevisionGuidance() {
        OpsPreApprovalValidationReportFactory factory =
                new OpsPreApprovalValidationReportFactory();
        OpsPreApprovalValidationReportFactory.ValidationResult failed = factory.fail(
                List.of(
                        "MISSING_TRUSTED_TEST_PROOF",
                        "MISSING_OPERATION_FIELD:effectType:op-1",
                        "POLICY_STALE_OR_UNKNOWN:op-1"),
                List.of(Map.of("proofType", "CI_DRY_RUN")));

        assertFalse(failed.passed());
        assertEquals("FAILED", failed.report().get("status"));
        @SuppressWarnings("unchecked")
        List<String> hints = (List<String>) failed.report().get("reviseHints");
        assertTrue(hints.stream().anyMatch(item -> item.contains("受控 Bash")));
        assertTrue(hints.stream().anyMatch(item -> item.contains("补齐 operation snapshot")));
        assertTrue(hints.stream().anyMatch(item -> item.contains("POLICY_STALE_OR_UNKNOWN")));
        assertThrows(UnsupportedOperationException.class,
                () -> failed.report().put("status", "PASSED"));
    }
}
