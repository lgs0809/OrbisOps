package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRuntimeResult;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.runtime.ApprovedLandingAgentCommand;
import cn.lgs.orbisops.trigger.ops.runtime.OpsApprovedLandingAgentRunCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageLandingRuntimeAdapterTest {

    @Test
    void unavailableLandingRuntimeFailsExecutionWithoutForcingReplan() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsApprovedLandingAgentRunCoordinator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        OpsChangePackageLandingRuntimeAdapter adapter = new OpsChangePackageLandingRuntimeAdapter(provider);
        ChangePackageSnapshot snapshot = snapshot(ChangePackageType.CONFIG_PACKAGE);
        ChangePackageCurrent current = current(snapshot, ChangePackageType.CONFIG_PACKAGE);

        ChangePackageLandingRuntimeResult result = adapter.execute(
                current,
                version(snapshot),
                new ChangePackageLandingPlan("cp-1", "project-1", 1, "hash-1", snapshot.toMap(), java.util.List.of()),
                new ChangePackageLandingRequest(1, "hash-1", "", Map.of("version", 1, "packageHash", "hash-1")),
                "lr-1",
                "alice");

        assertEquals(ChangePackageStatus.LANDING_FAILED, result.status());
        assertEquals("LANDING_FAILED", result.eventType());
        assertEquals("LANDING_AGENT_RUNTIME_UNAVAILABLE", result.reasonCode());
    }

    @Test
    void authorizesAndStartsTypedLandingAgentRun() {
        OpsApprovedLandingAgentRunCoordinator coordinator =
                mock(OpsApprovedLandingAgentRunCoordinator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsApprovedLandingAgentRunCoordinator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(coordinator);
        OpsChangePackageLandingRuntimeAdapter adapter = new OpsChangePackageLandingRuntimeAdapter(provider);
        ChangePackageSnapshot snapshot = snapshot();
        ChangePackageCurrent current = current(snapshot);
        ChangePackageVersion version = version(snapshot);
        ChangePackageLandingPlan plan = new ChangePackageLandingPlan(
                "cp-1", "project-1", 1, "hash-1", snapshot.toMap(), java.util.List.of());
        ChangePackageLandingRequest request = new ChangePackageLandingRequest(
                1, "hash-1", "", Map.of("version", 1, "packageHash", "hash-1"));
        when(coordinator.execute(org.mockito.ArgumentMatchers.any(
                ApprovedLandingAgentCommand.class)))
                .thenReturn(Map.of(
                        "status", "LANDED",
                        "summary", "done",
                        "executedProductionAction", true));

        ChangePackageLandingRuntimeResult result = adapter.execute(
                current, version, plan, request, "lr-1", "alice");

        assertEquals(ChangePackageStatus.LANDED, result.status());
        assertEquals("LANDING_SUCCEEDED", result.eventType());
        assertEquals("done", result.summary());
        assertEquals(true, result.executedProductionAction());
        assertEquals("LANDED", result.payload().get("status"));
        verify(coordinator).execute(org.mockito.ArgumentMatchers.argThat(command ->
                command.landingRunId().equals("lr-1")
                        && command.landingRuntimeId().equals(
                        cn.lgs.orbisops.trigger.ops.runtime.OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_ID)
                        && command.landingRuntimeVersion()
                        == cn.lgs.orbisops.trigger.ops.runtime.OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_VERSION
                        && command.approvedPackage().packageHash().equals("hash-1")));
    }

    private ChangePackageSnapshot snapshot() {
        return snapshot(ChangePackageType.NO_ACTION_REQUIRED);
    }

    private ChangePackageSnapshot snapshot(ChangePackageType packageType) {
        return new ChangePackageSnapshot(Map.ofEntries(
                Map.entry("packageId", "cp-1"),
                Map.entry("projectId", "project-1"),
                Map.entry("version", 1),
                Map.entry("packageHash", "hash-1"),
                Map.entry("packageType", packageType.name()),
                Map.entry("riskLevel", "LOW"),
                Map.entry("targetEnvironment", "prod"),
                Map.entry("preparationAgentSnapshot", Map.of(
                        "agentId", "agent-1",
                        "agentVersion", 1,
                        "definitionHash", "definition-hash-1",
                        "systemPromptHash", "prompt-hash-1",
                        "modelProfile", "model-1",
                        "toolBindingSnapshot", java.util.List.of(),
                        "mcpBindingSnapshot", java.util.List.of(),
                        "skillBindingSnapshot", java.util.List.of(),
                        "knowledgeBindingSnapshot", java.util.List.of()))),
                "hash-1");
    }

    private ChangePackageCurrent current(ChangePackageSnapshot snapshot) {
        return current(snapshot, ChangePackageType.NO_ACTION_REQUIRED);
    }

    private ChangePackageCurrent current(ChangePackageSnapshot snapshot, ChangePackageType packageType) {
        return new ChangePackageCurrent(
                1L,
                new ChangePackagePointer(
                        "cp-1", ChangePackageStatus.APPROVED, 1, "hash-1", 1, "hash-1"),
                "session-1",
                "incident-1",
                "project-1",
                "agent-1",
                1,
                packageType,
                ChangePackageCurrentState.fromSnapshot(snapshot.toMap()),
                snapshot,
                "",
                "creator",
                "approver",
                null,
                null,
                Instant.now());
    }

    private ChangePackageVersion version(ChangePackageSnapshot snapshot) {
        return new ChangePackageVersion(
                1L,
                "cp-1",
                1,
                "hash-1",
                ChangePackageStatus.APPROVED.name(),
                snapshot,
                "approved",
                "creator",
                null);
    }
}
