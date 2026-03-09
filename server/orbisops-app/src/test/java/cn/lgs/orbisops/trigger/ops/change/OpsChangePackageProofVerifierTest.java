package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageCanonicalHasher;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OpsChangePackageProofVerifierTest {

    @Test
    void highRiskPackageDoesNotRequireGenericSandboxOrDryRunProof() {
        OpsChangePackageProofVerifier verifier = new OpsChangePackageProofVerifier(null, null);
        Map<String, Object> snapshot = highRiskSnapshot();
        snapshot.put("dryRunResultJson", JSON.toJSONString(Map.of(
                "status", "PASSED",
                "trusted", true,
                "source", "CI_PROVIDER",
                "dryRunId", "self-reported")));
        Fixture fixture = fixture(snapshot, ChangePackageType.MCP_OPERATION_PACKAGE, "HIGH");

        assertDoesNotThrow(() -> verifier.verifyPackageBeforeApprove(
                fixture.current(), fixture.version()));
    }

    @Test
    void highRiskPackageDoesNotConsultGenericTrustedProofStore() {
        OpsTrustedProofService trustedProofService = mock(OpsTrustedProofService.class);
        Fixture fixture = fixture(highRiskSnapshot(), ChangePackageType.MCP_OPERATION_PACKAGE, "HIGH");
        OpsChangePackageProofVerifier verifier =
                new OpsChangePackageProofVerifier(null, trustedProofService);

        assertDoesNotThrow(() -> verifier.verifyPackageBeforeApprove(
                fixture.current(), fixture.version()));
        org.mockito.Mockito.verifyNoInteractions(trustedProofService);
    }

    @Test
    void mediumTargetWriteCanApproveWithoutGenericValidationProof() {
        Fixture fixture = fixture(
                targetWriteSnapshot("MEDIUM"),
                ChangePackageType.MCP_OPERATION_PACKAGE,
                "MEDIUM");
        OpsChangePackageProofVerifier verifier = new OpsChangePackageProofVerifier(null, null);

        assertDoesNotThrow(() -> verifier.verifyPackageBeforeApprove(
                fixture.current(), fixture.version()));
    }

    @Test
    void operationMissingRiskLevelIsRejectedBeforeApprove() {
        Map<String, Object> snapshot = highRiskSnapshot();
        snapshot.put("mcpSteps", List.of(Map.of(
                "operationId", "op-1",
                "mcpId", "mcp-1",
                "toolName", "apply_config",
                "effectType", "MUTATE_TARGET_RESOURCE",
                "effectScope", "PRODUCTION",
                "targetEnvironment", "prod",
                "resourceScope", "order-service",
                "arguments", Map.of("k", "v"))));
        Fixture fixture = fixture(snapshot, ChangePackageType.MCP_OPERATION_PACKAGE, "HIGH");
        OpsChangePackageProofVerifier verifier = new OpsChangePackageProofVerifier(null, null);

        assertThrows(IllegalStateException.class,
                () -> verifier.verifyPackageBeforeApprove(fixture.current(), fixture.version()));
    }

    @Test
    void persistedLandingPlanJsonReadOnlyOperationCanApproveWithoutValidationProof() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-read");
        operation.put("mcpId", "prometheus-mcp");
        operation.put("adapterType", "MCP");
        operation.put("toolName", "prometheus_query");
        operation.put("effectType", "READ_EXTERNAL_STATE");
        operation.put("effectScope", "TARGET_RESOURCE_READ");
        operation.put("mutability", "READ_ONLY");
        operation.put("riskLevel", "LOW");
        operation.put("targetEnvironment", "prod");
        operation.put("resourceScope", "demo-project/prometheus");
        operation.put("arguments", Map.of("query", "up"));
        operation.put("writesTargetResource", false);
        operation.put("requiresChangePackage", false);
        operation.put("requiresApproval", false);
        operation.put("readOnly", true);
        ChangePackageCanonicalHasher.applyOperationHashes(operation);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("landingPlanJson", JSON.toJSONString(Map.of(
                "preferredPlan", Map.of("steps", List.of(operation)))));
        Fixture fixture = fixture(snapshot, ChangePackageType.MCP_OPERATION_PACKAGE, "MEDIUM");
        OpsChangePackageProofVerifier verifier = new OpsChangePackageProofVerifier(null, null);

        assertDoesNotThrow(() -> verifier.verifyPackageBeforeApprove(
                fixture.current(), fixture.version()));
    }

    private Map<String, Object> highRiskSnapshot() {
        return targetWriteSnapshot("HIGH");
    }

    private Map<String, Object> targetWriteSnapshot(String riskLevel) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("mcpId", "mcp-1");
        operation.put("adapterType", "MCP");
        operation.put("toolName", "apply_config");
        operation.put("effectType", "MUTATE_TARGET_RESOURCE");
        operation.put("effectScope", "PRODUCTION");
        operation.put("mutability", "PROD_MUTATING");
        operation.put("riskLevel", riskLevel);
        operation.put("targetEnvironment", "prod");
        operation.put("resourceScope", "order-service");
        operation.put("arguments", Map.of("k", "v"));
        operation.put("writesTargetResource", true);
        operation.put("requiresChangePackage", true);
        operation.put("requiresApproval", true);
        operation.put("readOnly", false);
        operation.put("preconditions", Map.of("expectedValues", Map.of("version", "1")));
        operation.put("postCheck", Map.of("expectedValues", Map.of("version", "2")));
        operation.put("rollbackPlan", Map.of("summary", "restore version 1"));
        operation.put("rollbackPrecondition", Map.of("expectedValues", Map.of("version", "2")));
        operation.put("manualFallback", Map.of("owner", "ops", "action", "manual restore"));
        ChangePackageCanonicalHasher.applyOperationHashes(operation);
        return new LinkedHashMap<>(Map.of("mcpSteps", List.of(operation)));
    }

    private Fixture fixture(Map<String, Object> values,
                            ChangePackageType type,
                            String riskLevel) {
        Map<String, Object> snapshotValues = new LinkedHashMap<>(values);
        snapshotValues.putIfAbsent("packageId", "cp-1");
        snapshotValues.putIfAbsent("projectId", "project-1");
        snapshotValues.put("packageType", type.name());
        snapshotValues.put("version", 1);
        snapshotValues.put("riskLevel", riskLevel);
        ChangePackageSnapshot snapshot = ChangePackageSnapshot.seal(snapshotValues);
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1",
                ChangePackageStatus.READY_FOR_REVIEW,
                1,
                snapshot.packageHash(),
                0,
                "");
        ChangePackageCurrent current = new ChangePackageCurrent(
                1,
                pointer,
                "session-1",
                "",
                "project-1",
                "agent-1",
                1,
                type,
                snapshot.currentState(),
                null,
                "",
                "alice",
                "",
                null,
                null,
                null);
        ChangePackageVersion version = new ChangePackageVersion(
                1,
                "cp-1",
                1,
                snapshot.packageHash(),
                ChangePackageStatus.READY_FOR_REVIEW.name(),
                snapshot,
                "",
                "alice",
                null);
        return new Fixture(current, version);
    }

    private record Fixture(ChangePackageCurrent current,
                           ChangePackageVersion version) {
    }
}
