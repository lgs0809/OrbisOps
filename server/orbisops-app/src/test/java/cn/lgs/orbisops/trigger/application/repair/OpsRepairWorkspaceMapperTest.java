package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairVerificationResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCapabilities;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpsRepairWorkspaceMapperTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";
    private static final String HASH = "a".repeat(64);
    private final OpsRepairWorkspaceMapper mapper = new OpsRepairWorkspaceMapper();

    @Test
    void mapsRequestAndWorkspaceDtoWithoutBusinessRules() {
        OpsRepairWorkspaceRequestDTO request = OpsRepairWorkspaceRequestDTO.builder()
                .projectId("project-1").serviceId("service-1").environment("prod")
                .summary("fix").unifiedDiff("patch").baseCommit(BASE).build();

        assertEquals("project-1", mapper.candidate(request).projectId());
        assertEquals("VERIFIED", mapper.view(workspace()).getStatus());
        assertEquals(List.of("module/src/App.java"), mapper.view(workspace()).getChangedFiles());
        assertNull(mapper.candidate(null));
        assertNull(mapper.view((RepairWorkspace) null));
    }

    @Test
    void mapsCapabilitiesDiffCommitVerificationArtifactAndCleanupContracts() {
        RepairDiffSnapshot diff = new RepairDiffSnapshot(
                "repair-1", BASE, REPAIR, List.of("module/src/App.java"),
                "1 file changed", HASH, 100);
        Map<String, Object> capabilities = mapper.capabilities(new RepairWorkspaceCapabilities(
                true, "GIT_WORKTREE", "docker", false, false,
                List.of("MAVEN_VERIFY"), 262144, 40));
        Map<String, Object> commit = mapper.view(new RepairCommitResult(
                diff, REPAIR, RepairWorkspaceStatus.COMMITTED, "alice"));
        Map<String, Object> verification = mapper.view(new RepairVerificationResult(
                diff, REPAIR, "proof-1", RepairWorkspaceStatus.VERIFIED, "alice"));
        Map<String, Object> artifact = mapper.view(new RepairArtifactValidation(
                "repair-1", "/tmp/app.jar", HASH, 10L, BASE, REPAIR,
                diff.changedFiles(), "MAVEN_VERIFY", 0));
        Map<String, Object> cleanup = mapper.view(new RepairCleanupResult(
                "repair-1", "REMOVED", true, "/tmp/repair-1"));

        assertEquals("GIT_WORKTREE", capabilities.get("isolation"));
        assertEquals(REPAIR, commit.get("repairCommit"));
        assertEquals("proof-1", verification.get("testProofHash"));
        assertEquals(10L, artifact.get("artifactSize"));
        assertEquals(true, cleanup.get("removed"));
        assertFalse(mapper.views(null).iterator().hasNext());
    }

    private RepairWorkspace workspace() {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, REPAIR,
                RepairWorkspaceStatus.VERIFIED, "fix", "patch", List.of("module/src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "/tmp/app.jar", HASH, 10L,
                "alice", "now", "now");
    }
}
