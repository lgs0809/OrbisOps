package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillVerifierTournamentArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String TRIGGER_SKILL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/skill/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void verifierOrderMustBeStructuralThenBehaviorThenJudge() throws IOException {
        String tournament = read(APPLICATION + "SkillCandidateTournament.java");
        String policy = read(DOMAIN + "service/SkillCandidateSelectionPolicy.java");
        String version = read(DOMAIN + "model/SkillVerifierVersion.java");

        int structural = tournament.indexOf("structuralVerifier.verify");
        int hidden = tournament.indexOf("hiddenEvaluationSetPort.load");
        int behavior = tournament.indexOf("behaviorReplay.verify");
        int judge = tournament.indexOf("SkillModelJudgeEvaluation judge = judge(");

        assertAll(
                () -> assertTrue(structural >= 0),
                () -> assertTrue(hidden > structural),
                () -> assertTrue(behavior > hidden),
                () -> assertTrue(judge > behavior),
                () -> assertTrue(tournament.contains("if (!structural.passed())")),
                () -> assertTrue(tournament.contains("if (!behavior.admitted())")),
                () -> assertTrue(tournament.contains("SkillModelJudgeDisposition.UNAVAILABLE")),
                () -> assertTrue(policy.contains("filter(SkillCandidateVerification::eligible)")),
                () -> assertTrue(policy.contains("SkillCandidateVerification::patchComplexity")),
                () -> assertTrue(policy.contains("SkillCandidateVerification::candidateId")),
                () -> assertTrue(version.contains("structuralVersion")),
                () -> assertTrue(version.contains("behaviorVersion")),
                () -> assertTrue(version.contains("judgeVersion")),
                () -> assertFalse(policy.contains("SkillModelJudgePort")),
                () -> assertFalse(policy.contains("ChatModel")));
    }

    @Test
    void hiddenAndMutationCasesMustNeverReachAuthoringInput() throws IOException {
        String authoring = read(APPLICATION + "SkillEvolutionMultiCandidateAuthoringService.java");
        String legacyPort = read(APPLICATION + "SkillEvolutionAuthoringPort.java");
        String evolution = read(APPLICATION + "SkillEvolutionApplicationService.java");
        String hiddenSet = read(APPLICATION + "SkillHiddenEvaluationSet.java");

        assertAll(
                () -> assertTrue(hiddenSet.contains("hiddenCases")),
                () -> assertTrue(hiddenSet.contains("mutationCases")),
                () -> assertFalse(authoring.contains("SkillHiddenEvaluationSet")),
                () -> assertFalse(authoring.contains("hiddenCases")),
                () -> assertFalse(authoring.contains("mutationCases")),
                () -> assertFalse(legacyPort.contains("SkillHiddenEvaluationSet")),
                () -> assertFalse(legacyPort.contains("hiddenCases")),
                () -> assertFalse(legacyPort.contains("mutationCases")),
                () -> assertFalse(evolution.contains("SkillHiddenEvaluationSet")),
                () -> assertFalse(evolution.contains("hiddenCases")),
                () -> assertFalse(evolution.contains("mutationCases")));
    }

    @Test
    void tournamentMustRemainASeparateFailClosedBoundaryBeforeMigration() throws IOException {
        String tournament = read(APPLICATION + "SkillCandidateTournament.java");
        String evolution = read(APPLICATION + "SkillEvolutionApplicationService.java");

        assertAll(
                () -> assertTrue(tournament.contains("SKILL_TOURNAMENT_CANDIDATE_COUNT_INVALID")),
                () -> assertTrue(tournament.contains("SKILL_HIDDEN_EVAL_SET_MISSING")),
                () -> assertTrue(tournament.contains("SKILL_BEHAVIOR_VERSION_MISMATCH")),
                () -> assertFalse(evolution.contains("new SkillCandidateTournament(")),
                () -> assertFalse(evolution.contains("SkillModelJudgeDisposition.PASS")),
                () -> assertFalse(evolution.contains("defaultJudgePass")));
    }

    @Test
    void strictRolloutMustPreserveFrozenContextAndNonOverridableSafetyGates()
            throws IOException {
        String evolution = read(APPLICATION + "SkillEvolutionApplicationService.java");
        String coordinator = read(APPLICATION + "SkillCandidateTournamentRolloutCoordinator.java");
        String behavior = read(TRIGGER_SKILL + "OpsSkillCandidateBehaviorReplayAdapter.java");
        String judge = read(TRIGGER_SKILL + "OpsSkillTournamentModelJudgeAdapter.java");
        String hidden = read(INFRASTRUCTURE_REPOSITORY
                + "JdbcSkillHiddenEvaluationSuiteAdapter.java");

        assertAll(
                () -> assertFalse(evolution.contains("new SkillCandidateTournament(")),
                () -> assertFalse(evolution.contains("STRICT_PRIMARY")),
                () -> assertTrue(coordinator.contains("sameTarget(legacySimilar, strictSimilar)")),
                () -> assertTrue(coordinator.contains("SKIP_STRICT_TOURNAMENT_TARGET_DRIFT")),
                () -> assertTrue(coordinator.contains("strictSimilar.frozen()")),
                () -> assertTrue(behavior.contains(
                        "SkillBehaviorReplayExecutionMode.FROZEN_MOCK")),
                () -> assertFalse(behavior.contains(
                        "SkillBehaviorReplayExecutionMode.READ_ONLY")),
                () -> assertFalse(behavior.contains(
                        "SkillBehaviorReplayExecutionMode.SANDBOX_DRY_RUN")),
                () -> assertTrue(hidden.contains(
                        "load(\n            SkillCandidateTournamentContext context")),
                () -> assertTrue(hidden.contains("SKILL_HIDDEN_EVAL_CONTEXT_REQUIRED")),
                () -> assertTrue(hidden.contains("context.baseSkillHash()")),
                () -> assertTrue(hidden.contains("context.hiddenSuiteVersion()")),
                () -> assertFalse(judge.contains("SkillReleasePort")),
                () -> assertFalse(judge.contains("LandingPort")),
                () -> assertFalse(judge.contains("publish(")),
                () -> assertTrue(judge.contains(
                        "Structural 与 Behavior 硬门禁已经在你之前执行")));
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
