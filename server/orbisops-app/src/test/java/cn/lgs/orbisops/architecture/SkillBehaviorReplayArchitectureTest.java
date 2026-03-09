package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBehaviorReplayArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/";

    @Test
    void replayMustHaveThreeFrozenArmsAndDeterministicAdmission() throws IOException {
        String arms = read(DOMAIN + "model/SkillBehaviorReplayArm.java");
        String snapshot = read(DOMAIN + "model/SkillBehaviorFrozenSnapshot.java");
        String policy = read(DOMAIN + "service/SkillBehaviorAdmissionPolicy.java");

        assertAll(
                () -> assertTrue(arms.contains("NO_SKILL")),
                () -> assertTrue(arms.contains("BASELINE")),
                () -> assertTrue(arms.contains("CANDIDATE")),
                () -> assertTrue(snapshot.contains("modelVersion")),
                () -> assertTrue(snapshot.contains("temperature")),
                () -> assertTrue(snapshot.contains("seed")),
                () -> assertTrue(snapshot.contains("toolSnapshotHash")),
                () -> assertTrue(snapshot.contains("mcpSchemaHash")),
                () -> assertTrue(snapshot.contains("verifierVersion")),
                () -> assertTrue(policy.contains("SKILL_REPLAY_SAFETY_REGRESSION")),
                () -> assertTrue(policy.contains("SKILL_REPLAY_SUCCESS_DELTA_NOT_MET")),
                () -> assertTrue(policy.contains("SKILL_REPLAY_EVIDENCE_REGRESSION")),
                () -> assertTrue(policy.contains("SKILL_REPLAY_COST_BUDGET_EXCEEDED")),
                () -> assertFalse(policy.contains("ModelJudge")),
                () -> assertFalse(policy.contains("ChatModel")));
    }

    @Test
    void replayToolsMustUseUnifiedPreApprovalChainAndNeverLandingAuthority() throws IOException {
        String service = read(APPLICATION + "SkillBehaviorReplayApplicationService.java");
        String port = read(APPLICATION + "SkillBehaviorReplayPort.java");
        String adapter = read(TRIGGER + "OpsSkillBehaviorToolExecutionAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("SkillBehaviorToolExecutionPort tools")),
                () -> assertTrue(service.contains("SKILL_REPLAY_PRODUCTION_WRITE_FORBIDDEN")),
                () -> assertTrue(service.contains("SKILL_REPLAY_READ_ONLY_TOOL_REQUIRED")),
                () -> assertTrue(service.contains("SKILL_REPLAY_CHANGE_PACKAGE_PROPOSAL_REQUIRED")),
                () -> assertTrue(adapter.contains("ToolExecutionApplicationService")),
                () -> assertTrue(adapter.contains("ToolExecutionScope.PRE_APPROVAL_WORKFLOW")),
                () -> assertTrue(adapter.contains("Map.of()));")),
                () -> assertFalse(adapter.contains("APPROVED_LANDING")),
                () -> assertFalse(adapter.contains("ChangePackageLanding")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("new Thread")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
