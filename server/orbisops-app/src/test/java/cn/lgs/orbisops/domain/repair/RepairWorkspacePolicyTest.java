package cn.lgs.orbisops.domain.repair;

import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceAggregate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.service.RepairWorkspacePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepairWorkspacePolicyTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";
    private final RepairWorkspacePolicy policy = new RepairWorkspacePolicy();

    @Test
    void normalizesDefaultsAndAllowsActiveCommittedVerifiedLifecycle() {
        RepairWorkspaceCandidate normalized = policy.normalize(new RepairWorkspaceCandidate(
                " project-1 ", " service-1 ", "", "", patch("module/src/App.java"), BASE));

        assertEquals("dev", normalized.environment());
        assertEquals("Agent 生成的代码修复候选", normalized.summary());
        assertEquals(BASE, normalized.baseCommit());
        RepairWorkspaceAggregate.rehydrate(
                "repair-1", "project-1", "service-1", RepairWorkspaceStatus.ACTIVE)
                .requireCleanupAllowed();
        RepairWorkspaceAggregate.rehydrate(
                "repair-1", "project-1", "service-1", RepairWorkspaceStatus.VERIFIED)
                .requireDeliverable();
    }

    @Test
    void rejectsBinaryOversizedEscapingAndForbiddenPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validatePatch("GIT binary patch", "."));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validatePatch(patch("../escape.txt"), "."));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validatePatch(patch("other/App.java"), "module"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validatePatch(patch("module/target/App.class"), "module"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validatePatch("x".repeat(RepairWorkspacePolicy.MAX_PATCH_BYTES + 1), "."));
    }

    @Test
    void requiresCommitDiffVerificationProofAndExactArtifactSnapshot() {
        RepairWorkspace active = workspace(RepairWorkspaceStatus.ACTIVE, "", "", "");
        RepairDiffSnapshot diff = new RepairDiffSnapshot(
                "repair-1", BASE, BASE, List.of("module/src/App.java"), "1 file changed",
                "a".repeat(64), 100);
        policy.requireCommitAllowed(active, diff);

        RepairWorkspace committed = active.committed(REPAIR, diff.changedFiles(), "later");
        assertEquals("repair workspace 缺少可信 testProofHash，不能 VERIFIED",
                assertThrows(IllegalStateException.class, () -> policy.requireVerification(
                        committed, diff, diff.diffHash(), diff.changedFiles(), "")).getMessage());
        assertThrows(IllegalStateException.class, () -> policy.requireVerification(
                committed, diff, "b".repeat(64), diff.changedFiles(), "proof"));
        policy.requireVerification(committed, diff, diff.diffHash(), diff.changedFiles(), "proof");

        RepairWorkspace verified = workspace(
                RepairWorkspaceStatus.VERIFIED, REPAIR, "/tmp/artifact.jar", "c".repeat(64));
        policy.requireArtifact(
                verified, "project-1", "service-1", "/tmp/artifact.jar", "c".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> policy.requireArtifact(
                verified, "project-1", "service-1", "/tmp/other.jar", "c".repeat(64)));
    }

    @Test
    void cleanupRejectsPreparingAndTesting() {
        assertThrows(IllegalStateException.class,
                () -> policy.requireCleanupAllowed(workspace(RepairWorkspaceStatus.PREPARING, "", "", "")));
        assertThrows(IllegalStateException.class,
                () -> policy.requireCleanupAllowed(workspace(RepairWorkspaceStatus.TESTING, "", "", "")));
    }

    private RepairWorkspace workspace(
            RepairWorkspaceStatus status,
            String verifiedCommit,
            String artifactPath,
            String artifactHash) {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, verifiedCommit,
                status, "fix", patch("module/src/App.java"), List.of("module/src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", artifactPath, artifactHash,
                artifactPath.isBlank() ? 0L : 10L, "alice", "now", "now");
    }

    private String patch(String path) {
        return """
                --- a/%s
                +++ b/%s
                @@ -1 +1 @@
                -old
                +new
                """.formatted(path, path);
    }
}
