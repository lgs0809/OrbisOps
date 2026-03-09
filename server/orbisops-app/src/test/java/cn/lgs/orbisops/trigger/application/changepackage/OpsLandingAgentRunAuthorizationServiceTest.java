package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLandingAgentRunAuthorizationServiceTest {

    @Test
    void approvalBindsPackageIdentityButDoesNotCreatePerToolLandingAcl() {
        ChangePackageSnapshot snapshot = new ChangePackageSnapshot(Map.of(
                "packageId", "cp-1",
                "projectId", "project-1",
                "version", 2,
                "packageHash", "hash-2",
                "packageType", ChangePackageType.MANUAL_REQUIRED.name(),
                "riskLevel", "HIGH",
                "targetEnvironment", "prod",
                "artifactDigest", "sha256:artifact-2",
                "preparationAgentSnapshot", Map.of(
                        "agentId", "agent-1",
                        "agentVersion", 7,
                        "definitionHash", "definition-hash-7",
                        "systemPromptHash", "prompt-hash-7",
                        "modelProfile", "model-1",
                        "toolBindingSnapshot", List.of("deploy_release"),
                        "mcpBindingSnapshot", List.of("deployment-tools"),
                        "skillBindingSnapshot", List.of(),
                        "knowledgeBindingSnapshot", List.of())), "hash-2");
        ChangePackageCurrent current = new ChangePackageCurrent(
                1L,
                new ChangePackagePointer("cp-1", ChangePackageStatus.APPROVED, 2, "hash-2", 2, "hash-2"),
                "prepare-session-1",
                "incident-1",
                "project-1",
                "agent-1",
                7,
                ChangePackageType.MANUAL_REQUIRED,
                ChangePackageCurrentState.fromSnapshot(snapshot.toMap()),
                snapshot,
                "",
                "creator",
                "approver",
                null,
                null,
                Instant.now());
        ChangePackageVersion version = new ChangePackageVersion(
                2L, "cp-1", 2, "hash-2", ChangePackageStatus.APPROVED.name(),
                snapshot, "approved", "creator", null);
        ChangePackageLandingOperation operation = new ChangePackageLandingOperation(
                "op-1", "op-hash-1", "MCP", "deployment-tools", "deploy_release",
                "order-service", "DEPLOY", Map.of("targetEnvironment", "prod"));
        ChangePackageLandingPlan plan = new ChangePackageLandingPlan(
                "cp-1", "project-1", 2, "hash-2", snapshot.toMap(), List.of(operation));
        ChangePackageLandingRequest request = new ChangePackageLandingRequest(
                2, "hash-2", "", Map.of("version", 2, "packageHash", "hash-2"));

        var command = new OpsLandingAgentRunAuthorizationService().authorize(
                current, version, plan, request, "landing-run-1", "alice");

        assertEquals("cp-1", command.approvedPackage().packageId());
        assertEquals(2, command.approvedPackage().packageVersion());
        assertEquals("hash-2", command.approvedPackage().packageHash());
        assertEquals("project-1", command.approvedPackage().projectId());
        assertEquals("prod", command.approvedPackage().targetEnvironment());
        assertEquals("sha256:artifact-2", command.approvedPackage().artifactDigest());
        assertEquals(cn.lgs.orbisops.trigger.ops.runtime.OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_ID,
                command.landingRuntimeId());
        assertEquals("landing-run-1", command.workSessionId());
        assertTrue(command.instruction().contains("task book"));
        assertTrue(command.instruction().contains("NEEDS_REPLAN"));
    }
}
