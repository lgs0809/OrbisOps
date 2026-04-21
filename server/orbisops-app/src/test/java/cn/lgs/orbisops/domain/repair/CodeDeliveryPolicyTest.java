package cn.lgs.orbisops.domain.repair;

import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCandidate;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.service.CodeDeliveryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodeDeliveryPolicyTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";
    private final CodeDeliveryPolicy policy = new CodeDeliveryPolicy();

    @Test
    void normalizesLocalAndGithubDefaults() {
        CodeDeliveryCandidate local = policy.normalize(null);
        CodeDeliveryCandidate github = policy.normalize(
                new CodeDeliveryCandidate(CodeDeliveryMode.GITHUB_PR, " review ", ""));

        assertEquals(CodeDeliveryMode.LOCAL_BRANCH, local.mode());
        assertEquals("review", github.title());
        assertEquals("main", github.baseBranch());
    }

    @Test
    void createsStableReviewBranchAndTitleFallback() {
        RepairWorkspace workspace = workspace(RepairWorkspaceStatus.VERIFIED);

        assertEquals("ops-repair/service-1/3456789abcde", policy.branchName(workspace));
        assertEquals("fix payment", policy.title(
                new CodeDeliveryCandidate(CodeDeliveryMode.LOCAL_BRANCH, "", ""), workspace));
    }

    @Test
    void rejectsUnverifiedWorkspaceInvalidBranchTitleAndProvider() {
        assertThrows(IllegalStateException.class,
                () -> policy.requireDeliverable(workspace(RepairWorkspaceStatus.COMMITTED)));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(new CodeDeliveryCandidate(
                        CodeDeliveryMode.GITHUB_PR, "", "../main")));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(new CodeDeliveryCandidate(
                        CodeDeliveryMode.LOCAL_BRANCH, "x".repeat(241), "")));
        assertEquals("GitHub PR Provider 未配置",
                assertThrows(IllegalStateException.class,
                        () -> policy.requireProvider(CodeDeliveryMode.GITHUB_PR, false)).getMessage());
    }

    private RepairWorkspace workspace(RepairWorkspaceStatus status) {
        return new RepairWorkspace(
                "repair_0123456789abcde", "project-1", "service-1", "repo-1", "prod",
                BASE, REPAIR, status, "fix payment", "patch", List.of("module/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "", "", 0L,
                "alice", "now", "now");
    }
}
