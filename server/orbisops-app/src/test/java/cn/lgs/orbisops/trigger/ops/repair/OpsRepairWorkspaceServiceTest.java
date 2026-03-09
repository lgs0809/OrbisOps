package cn.lgs.orbisops.trigger.ops.repair;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCapabilities;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import cn.lgs.orbisops.trigger.application.repair.OpsRepairWorkspaceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRepairWorkspaceServiceTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void compatibilityAclMapsRequestWorkspaceCapabilitiesAndDiff() {
        RepairWorkspaceApplicationService application = mock(RepairWorkspaceApplicationService.class);
        RepairWorkspace workspace = workspace();
        when(application.createAndVerify(any(), eq("admin"))).thenReturn(workspace);
        when(application.find("repair-1")).thenReturn(Optional.of(workspace));
        when(application.capabilities()).thenReturn(new RepairWorkspaceCapabilities(
                true, "GIT_WORKTREE", "docker", false, false,
                List.of("MAVEN_VERIFY"), 262144, 40));
        when(application.computeDiff("repair-1")).thenReturn(new RepairDiffSnapshot(
                "repair-1", BASE, BASE, List.of("module/src/App.java"), "1 file changed",
                "a".repeat(64), 100));
        OpsRepairWorkspaceService facade =
                new OpsRepairWorkspaceService(application, new OpsRepairWorkspaceMapper());
        OpsRepairWorkspaceRequestDTO request = OpsRepairWorkspaceRequestDTO.builder()
                .projectId("project-1").serviceId("service-1").environment("prod")
                .summary("fix").unifiedDiff(patch()).baseCommit(BASE).build();

        assertEquals("repair-1", facade.createAndVerify(request, "admin").getWorkspaceId());
        assertEquals("VERIFIED", facade.get("repair-1").orElseThrow().getStatus());
        assertEquals(true, facade.capabilities().get("enabled"));
        assertEquals("a".repeat(64), facade.computeRepairDiff("repair-1").get("diffHash"));
        ArgumentCaptor<RepairWorkspaceCandidate> candidate = ArgumentCaptor.forClass(RepairWorkspaceCandidate.class);
        verify(application).createAndVerify(candidate.capture(), eq("admin"));
        assertEquals("project-1", candidate.getValue().projectId());
    }

    @Test
    void compatibilityAclMapsWriterLeaseAndDelegatesLegacyMethods() {
        RepairWorkspaceApplicationService application = mock(RepairWorkspaceApplicationService.class);
        when(application.claimWriter("repair-1", "run-1")).thenReturn(
                new RepairWriterLease("repair-1", "project-1", "run-1", "claim-1", 3, "later", 4));
        when(application.worktreePath("repair-1")).thenReturn(Path.of("/tmp/repair-1"));
        OpsRepairWorkspaceService facade =
                new OpsRepairWorkspaceService(application, new OpsRepairWorkspaceMapper());

        OpsRepairWorkspaceService.WriterLease lease = facade.claimWriter("repair-1", "run-1");
        facade.markDirty("repair-1", "run-1");
        facade.markTesting("repair-1", "run-1");
        facade.recordTestOutcome("repair-1", "run-1", true);
        facade.releaseWriter("repair-1", "run-1");
        facade.deleteWorktree("repair-1");

        assertEquals(3, lease.fencingToken());
        assertEquals("claim-1", lease.leaseToken());
        assertEquals(Path.of("/tmp/repair-1"), facade.worktreePath("repair-1"));
        verify(application).markDirty("repair-1", "run-1");
        verify(application).deleteWorktree("repair-1");
    }

    private RepairWorkspace workspace() {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE,
                "89abcdef0123456789abcdef0123456789abcdef", RepairWorkspaceStatus.VERIFIED,
                "fix", patch(), List.of("module/src/App.java"), "MAVEN_VERIFY", "mvn test", 0,
                "ok", "", "", 0L, "admin", "now", "now");
    }

    private String patch() {
        return """
                --- a/module/src/App.java
                +++ b/module/src/App.java
                @@ -1 +1 @@
                -old
                +new
                """;
    }
}
