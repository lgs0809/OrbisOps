package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationOutcome;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationWritebackUseCase;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsPreApprovalValidationServiceTest {

    @Test
    void highRiskMcpPackageWithoutExplicitValidationStepMovesReadyForReview() {
        Fixture fixture = packageFixture("READY_FOR_REVIEW");
        OpsTrustedProofService proofService = mock(OpsTrustedProofService.class);
        OpsPreApprovalValidationService service = new OpsPreApprovalValidationService(
                fixture.queryService(), fixture.writebackUseCase(), provider(null), provider(proofService));

        Map<String, Object> result = service.validate("cp-1", 1, "alice");

        assertEquals("READY_FOR_REVIEW", result.get("status"));
        verify(fixture.writebackUseCase()).writeBack(any(ChangePackageCommands.ValidationWriteback.class));
    }

    @Test
    void explicitDryRunValidationStepCanUseTrustedProof() {
        Fixture fixture = packageFixture("READY_FOR_REVIEW", snapshotWithExplicitValidationStep());
        OpsTrustedProofService proofService = mock(OpsTrustedProofService.class);
        when(proofService.verifyTrustedProof("project-1", "cp-1", 1, "hash-1",
                "HIGH", "MCP_DRY_RUN", "")).thenReturn(true);
        OpsPreApprovalValidationService service = new OpsPreApprovalValidationService(
                fixture.queryService(), fixture.writebackUseCase(), provider(null), provider(proofService));

        Map<String, Object> result = service.validate("cp-1", 1, "alice");

        assertEquals("READY_FOR_REVIEW", result.get("status"));
        verify(fixture.writebackUseCase()).writeBack(any(ChangePackageCommands.ValidationWriteback.class));
    }

    @Test
    void explicitDryRunValidationStepFailsClosedWhenNoValidationExecutorOrProofExists() {
        Fixture fixture = packageFixture("VALIDATION_FAILED", snapshotWithExplicitValidationStep());
        OpsTrustedProofService proofService = mock(OpsTrustedProofService.class);
        OpsPreApprovalValidationService service = new OpsPreApprovalValidationService(
                fixture.queryService(), fixture.writebackUseCase(), provider(null), provider(proofService));

        Map<String, Object> result = service.validate("cp-1", 1, "alice");

        assertEquals("VALIDATION_FAILED", result.get("status"));
        verify(fixture.writebackUseCase()).writeBack(any(ChangePackageCommands.ValidationWriteback.class));
    }

    @Test
    void gitRepairValidationRunsControlledBashAndRecordsTrustedProof() {
        ChangePackageQueryPort queryPort = mock(ChangePackageQueryPort.class);
        ChangePackageQueryService queryService = new ChangePackageQueryService(queryPort);
        ChangePackageValidationWritebackUseCase writebackUseCase = mock(ChangePackageValidationWritebackUseCase.class);
        OpsRepairWorkspaceService repairWorkspaceService = mock(OpsRepairWorkspaceService.class);
        OpsTrustedProofService proofService = mock(OpsTrustedProofService.class);
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        Map<String, Object> snapshot = gitRepairSnapshot();
        Map<String, Object> gitCurrent = Map.of(
                "packageId", "cp-git",
                "package_id", "cp-git",
                "projectId", "project-1",
                "project_id", "project-1",
                "version", 1,
                "packageHash", "hash-git",
                "package_hash", "hash-git",
                "status", "VALIDATING");
        when(queryPort.detail("cp-git")).thenReturn(gitCurrent,
                Map.of("packageId", "cp-git", "status", "READY_FOR_REVIEW"));
        when(queryPort.versions("cp-git")).thenReturn(List.of(Map.of(
                "packageId", "cp-git",
                "version", 1,
                "packageHash", "hash-git",
                "snapshotJson", JSON.toJSONString(snapshot))));
        when(toolExecutionService.execute(any(), eq("alice"))).thenReturn(Map.of(
                "resultId", "tool-result-1",
                "outputHash", "out-hash",
                "status", "SUCCEEDED"));
        when(proofService.verifyTrustedProof("project-1", "cp-git", 1, "hash-git",
                "MEDIUM", "CONTROLLED_BASH_TEST", "out-hash")).thenReturn(true);
        when(repairWorkspaceService.verifyRepairWorkspace("ws-1", "diff-hash", List.of("src/App.java"), "out-hash", "ops-agent"))
                .thenReturn(Map.of("status", "VERIFIED"));
        when(writebackUseCase.executionToken("cp-git", 1, "hash-git")).thenReturn("validation-token-git");
        when(writebackUseCase.writeBack(any(ChangePackageCommands.ValidationWriteback.class)))
                .thenReturn(validationOutcome(
                        "cp-git",
                        "hash-git",
                        ChangePackageStatus.READY_FOR_REVIEW,
                        true));
        OpsPreApprovalValidationService service = new OpsPreApprovalValidationService(
                queryService,
                writebackUseCase,
                provider(repairWorkspaceService),
                provider(proofService),
                provider(toolExecutionService),
                provider(null));

        Map<String, Object> result = service.validate("cp-git", 1, "alice");

        assertEquals("READY_FOR_REVIEW", result.get("status"));
        verify(toolExecutionService).execute(any(), eq("alice"));
        verify(proofService, atLeastOnce()).recordTrustedProof(any(), eq("alice"));
        verify(repairWorkspaceService).verifyRepairWorkspace("ws-1", "diff-hash", List.of("src/App.java"), "out-hash", "ops-agent");
        verify(writebackUseCase).writeBack(any(ChangePackageCommands.ValidationWriteback.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NO_ACTION_REQUIRED", "MANUAL_REQUIRED", "NEEDS_HUMAN_DESIGN"})
    void humanOnlyTypeCannotHideRetainedExecutableSteps(String type) {
        Map<String, Object> source = new LinkedHashMap<>(snapshotWithExplicitValidationStep());
        source.put("packageType", type);
        Fixture fixture = packageFixture("VALIDATION_FAILED", source);
        var service = new OpsPreApprovalValidationService(fixture.queryService(), fixture.writebackUseCase(),
                provider(null), provider(null));
        service.validate("cp-1", 1, "alice");
        var capture = org.mockito.ArgumentCaptor.forClass(ChangePackageCommands.ValidationWriteback.class);
        verify(fixture.writebackUseCase()).writeBack(capture.capture());
        org.junit.jupiter.api.Assertions.assertFalse(capture.getValue().passed());
        org.junit.jupiter.api.Assertions.assertTrue(capture.getValue().validationReport().toString()
                .contains("HUMAN_ONLY_PACKAGE_HAS_EXECUTABLE_OPERATIONS"));
    }

    @Test void emptyTopLevelStepsCannotHideExecutableLandingPlan() {
        Map<String, Object> source = new LinkedHashMap<>(snapshot());
        Object steps = source.get("mcpSteps");
        source.put("packageType", "NEEDS_HUMAN_DESIGN");
        source.put("mcpSteps", List.of());
        source.put("landingPlan", Map.of("steps", List.of(), "operations", steps));
        Fixture fixture = packageFixture("VALIDATION_FAILED", source);
        var service = new OpsPreApprovalValidationService(fixture.queryService(), fixture.writebackUseCase(),
                provider(null), provider(null));
        service.validate("cp-1", 1, "alice");
        var capture = org.mockito.ArgumentCaptor.forClass(ChangePackageCommands.ValidationWriteback.class);
        verify(fixture.writebackUseCase()).writeBack(capture.capture());
        org.junit.jupiter.api.Assertions.assertFalse(capture.getValue().passed());
    }

    @Test void genuinelyEmptyNoActionPackageStillValidatesWithoutToolExecution() {
        Map<String, Object> source = new LinkedHashMap<>(snapshot());
        source.put("packageType", "NO_ACTION_REQUIRED");
        source.put("mcpSteps", List.of());
        Fixture fixture = packageFixture("READY_FOR_REVIEW", source);
        var service = new OpsPreApprovalValidationService(fixture.queryService(), fixture.writebackUseCase(),
                provider(null), provider(null));
        service.validate("cp-1", 1, "alice");
        var capture = org.mockito.ArgumentCaptor.forClass(ChangePackageCommands.ValidationWriteback.class);
        verify(fixture.writebackUseCase()).writeBack(capture.capture());
        org.junit.jupiter.api.Assertions.assertTrue(capture.getValue().passed());
    }

    private Fixture packageFixture(String finalStatus) {
        return packageFixture(finalStatus, snapshot());
    }

    private Fixture packageFixture(String finalStatus, Map<String, Object> packageSnapshot) {
        ChangePackageQueryPort port = mock(ChangePackageQueryPort.class);
        ChangePackageQueryService queryService = new ChangePackageQueryService(port);
        ChangePackageValidationWritebackUseCase writebackUseCase = mock(ChangePackageValidationWritebackUseCase.class);
        Map<String, Object> current = Map.of(
                "packageId", "cp-1",
                "package_id", "cp-1",
                "projectId", "project-1",
                "project_id", "project-1",
                "version", 1,
                "packageHash", "hash-1",
                "package_hash", "hash-1",
                "status", "VALIDATING");
        Map<String, Object> completed = Map.of(
                "packageId", "cp-1",
                "status", finalStatus);
        when(port.detail("cp-1")).thenReturn(current, completed);
        when(port.versions("cp-1")).thenReturn(List.of(Map.of(
                "packageId", "cp-1",
                "version", 1,
                "packageHash", "hash-1",
                "snapshotJson", JSON.toJSONString(packageSnapshot))));
        when(writebackUseCase.executionToken("cp-1", 1, "hash-1")).thenReturn("validation-token-1");
        ChangePackageStatus status = ChangePackageStatus.require(finalStatus);
        when(writebackUseCase.writeBack(any(ChangePackageCommands.ValidationWriteback.class)))
                .thenReturn(validationOutcome(
                        "cp-1",
                        "hash-1",
                        status,
                        status == ChangePackageStatus.READY_FOR_REVIEW));
        return new Fixture(port, queryService, writebackUseCase);
    }

    private record Fixture(ChangePackageQueryPort port,
                           ChangePackageQueryService queryService,
                           ChangePackageValidationWritebackUseCase writebackUseCase) {
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("adapterType", "MCP");
        operation.put("mcpId", "mcp-1");
        operation.put("toolName", "apply_config");
        operation.put("arguments", Map.of("dataId", "application-prod.yml"));
        operation.put("resourceScope", "demo-project/app-config");
        operation.put("targetEnvironment", "prod");
        operation.put("riskLevel", "HIGH");
        operation.put("effectType", "MUTATE_TARGET_RESOURCE");
        operation.put("effectScope", "PRODUCTION");
        operation.put("mutability", "PROD_MUTATING");
        operation.put("writesTargetResource", true);
        operation.put("requiresChangePackage", true);
        operation.put("requiresApproval", true);
        operation.put("readOnly", false);
        return Map.of(
                "packageId", "cp-1",
                "projectId", "project-1",
                "version", 1,
                "packageHash", "hash-1",
                "packageType", "MCP_OPERATION_PACKAGE",
                "riskLevel", "HIGH",
                "mcpSteps", List.of(operation));
    }

    private Map<String, Object> snapshotWithExplicitValidationStep() {
        Map<String, Object> base = new LinkedHashMap<>(snapshot());
        @SuppressWarnings("unchecked")
        Map<String, Object> targetWrite = new LinkedHashMap<>(((List<Map<String, Object>>) base.get("mcpSteps")).get(0));
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("operationId", "op-validate");
        validation.put("adapterType", "MCP");
        validation.put("mcpId", "mcp-1");
        validation.put("toolName", "validate_config");
        validation.put("arguments", Map.of("dataId", "application-prod.yml"));
        validation.put("resourceScope", "demo-project/app-config");
        validation.put("targetEnvironment", "prod");
        validation.put("riskLevel", "HIGH");
        validation.put("effectType", "DRY_RUN");
        validation.put("effectScope", "TARGET_RESOURCE_READ");
        validation.put("mutability", "READ_ONLY");
        validation.put("writesTargetResource", false);
        validation.put("requiresChangePackage", false);
        validation.put("requiresApproval", false);
        validation.put("readOnly", true);
        base.put("mcpSteps", List.of(targetWrite, validation));
        return base;
    }

    private Map<String, Object> gitRepairSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("packageId", "cp-git");
        data.put("projectId", "project-1");
        data.put("version", 1);
        data.put("packageHash", "hash-git");
        data.put("packageType", ChangePackageType.GIT_BRANCH_REPAIR.name());
        data.put("riskLevel", "MEDIUM");
        data.put("repairWorkspaceId", "ws-1");
        data.put("repairCommit", "repair-commit");
        data.put("diffHash", "diff-hash");
        data.put("changedFiles", List.of("src/App.java"));
        data.put("testCommand", "mvn test");
        return data;
    }

    private ChangePackageValidationOutcome validationOutcome(
            String packageId,
            String packageHash,
            ChangePackageStatus status,
            boolean passed) {
        return new ChangePackageValidationOutcome(
                packageId,
                status,
                1,
                packageHash,
                passed,
                passed ? "READY_FOR_REVIEW" : "VALIDATION_FAILED");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
