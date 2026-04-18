package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageRepairCleanupOutcome;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageCleanupAdapterTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";

    @Test
    void validatesWorkspaceIdentityAndReturnsTypedCleanupOutcome() {
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        when(workspaces.get("repair-1")).thenReturn(workspace("project-1", "service-1", "repo-1", BASE));
        when(workspaces.cleanup("repair-1", "alice")).thenReturn(
                new RepairCleanupResult("repair-1", "REMOVED", true, "/tmp/repair-1"));
        OpsChangePackageCleanupAdapter adapter = new OpsChangePackageCleanupAdapter(workspaces);

        ChangePackageRepairCleanupOutcome result = adapter.cleanupRepairWorkspace(
                current(), "repair-1", "alice");

        assertEquals("REMOVED", result.status());
        assertEquals(true, result.removed());
        verify(workspaces).cleanup("repair-1", "alice");
    }

    @Test
    void rejectsCrossProjectWorkspaceBeforeCleanup() {
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        when(workspaces.get("repair-1")).thenReturn(workspace("other", "service-1", "repo-1", BASE));
        OpsChangePackageCleanupAdapter adapter = new OpsChangePackageCleanupAdapter(workspaces);

        assertEquals("workspace projectId 与 ChangePackage 不一致",
                assertThrows(IllegalStateException.class,
                        () -> adapter.cleanupRepairWorkspace(current(), "repair-1", "alice")).getMessage());
    }

    private ChangePackageCurrent current() {
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.APPROVED, 1, "hash-1", 1, "hash-1");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("serviceId", "service-1");
        state.put("repositoryId", "repo-1");
        state.put("baseCommit", BASE);
        state.put("repairCommit", REPAIR);
        state.put("riskLevel", "HIGH");
        return new ChangePackageCurrent(
                1L, pointer, "session-1", "incident-1", "project-1", "agent-1", 1,
                ChangePackageType.GIT_BRANCH_REPAIR, ChangePackageCurrentState.fromSnapshot(state),
                new ChangePackageSnapshot(Map.of(
                        "packageId", "cp-1", "version", 1,
                        "packageHash", "hash-1", "projectId", "project-1"), "hash-1"),
                "", "creator", "approver", null, null, null);
    }

    private RepairWorkspace workspace(
            String projectId,
            String serviceId,
            String repositoryId,
            String baseCommit) {
        return new RepairWorkspace(
                "repair-1", projectId, serviceId, repositoryId, "prod", baseCommit, REPAIR,
                RepairWorkspaceStatus.VERIFIED, "fix", "patch", List.of("module/src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "", "", 0L,
                "alice", "now", "now");
    }
}
