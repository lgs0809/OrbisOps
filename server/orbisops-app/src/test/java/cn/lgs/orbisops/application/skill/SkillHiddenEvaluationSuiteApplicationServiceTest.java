package cn.lgs.orbisops.application.skill;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillHiddenEvaluationSuiteApplicationServiceTest {

    private static final String BASE_HASH = "a".repeat(64);

    @Test
    void publishMustDelegateValidatedDraftAndRequireSnapshot() {
        RecordingPort port = new RecordingPort();
        SkillHiddenEvaluationSuiteSnapshot snapshot = snapshot("v1");
        port.published = snapshot;
        SkillHiddenEvaluationSuiteApplicationService service =
                new SkillHiddenEvaluationSuiteApplicationService(port);
        SkillHiddenEvaluationSuiteDraft draft = draft("v1");

        SkillHiddenEvaluationSuiteSnapshot result = service.publish(draft);

        assertSame(snapshot, result);
        assertSame(draft, port.lastDraft);
    }

    @Test
    void publishMustFailWhenPortReturnsNull() {
        RecordingPort port = new RecordingPort();
        SkillHiddenEvaluationSuiteApplicationService service =
                new SkillHiddenEvaluationSuiteApplicationService(port);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.publish(draft("v1")));

        assertEquals("SKILL_HIDDEN_SUITE_RESULT_REQUIRED", error.getMessage());
    }

    @Test
    void requireMustUseCompleteFrozenTarget() {
        RecordingPort port = new RecordingPort();
        SkillHiddenEvaluationSuiteSnapshot snapshot = snapshot("v7");
        port.found = Optional.of(snapshot);
        SkillHiddenEvaluationSuiteApplicationService service =
                new SkillHiddenEvaluationSuiteApplicationService(port);

        SkillHiddenEvaluationSuiteSnapshot result = service.require(
                "project-1", "skill-1", 7, BASE_HASH, "v7");

        assertSame(snapshot, result);
        assertEquals(new FindCall("project-1", "skill-1", 7, BASE_HASH, "v7"),
                port.lastFind);
    }

    @Test
    void requireMustFailClosedWhenSuiteDoesNotExist() {
        SkillHiddenEvaluationSuiteApplicationService service =
                new SkillHiddenEvaluationSuiteApplicationService(new RecordingPort());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.require(
                        "project-1", "skill-1", 7, BASE_HASH, "v1"));

        assertEquals("SKILL_HIDDEN_SUITE_NOT_FOUND:project-1:skill-1:7:v1",
                error.getMessage());
    }

    @Test
    void listMustClampLimitToOneAndTwoHundred() {
        RecordingPort port = new RecordingPort();
        port.listed = List.of(snapshot("v1"));
        SkillHiddenEvaluationSuiteApplicationService service =
                new SkillHiddenEvaluationSuiteApplicationService(port);

        service.list("project-1", "skill-1", 0);
        service.list("project-1", "skill-1", 999);

        assertEquals(List.of(1, 200), port.listLimits);
    }

    private SkillHiddenEvaluationSuiteDraft draft(String version) {
        return new SkillHiddenEvaluationSuiteDraft(
                "project-1",
                "skill-1",
                7,
                BASE_HASH,
                version,
                List.of(java.util.Map.of("caseId", "hidden-1")),
                List.of(java.util.Map.of("caseId", "mutation-1")),
                "alice");
    }

    private SkillHiddenEvaluationSuiteSnapshot snapshot(String version) {
        Instant now = Instant.parse("2026-08-03T01:00:00Z");
        return new SkillHiddenEvaluationSuiteSnapshot(
                "suite-" + version,
                "project-1",
                "skill-1",
                7,
                BASE_HASH,
                version,
                "b".repeat(64),
                "c".repeat(64),
                "ACTIVE",
                "alice",
                now,
                now);
    }

    private static final class RecordingPort implements SkillHiddenEvaluationSuitePort {
        private SkillHiddenEvaluationSuiteDraft lastDraft;
        private SkillHiddenEvaluationSuiteSnapshot published;
        private Optional<SkillHiddenEvaluationSuiteSnapshot> found = Optional.empty();
        private FindCall lastFind;
        private List<SkillHiddenEvaluationSuiteSnapshot> listed = List.of();
        private final List<Integer> listLimits = new ArrayList<>();

        @Override
        public SkillHiddenEvaluationSuiteSnapshot publish(
                SkillHiddenEvaluationSuiteDraft draft) {
            lastDraft = draft;
            return published;
        }

        @Override
        public Optional<SkillHiddenEvaluationSuiteSnapshot> find(
                String projectId,
                String skillId,
                long baseVersion,
                String baseSkillHash,
                String suiteVersion) {
            lastFind = new FindCall(
                    projectId, skillId, baseVersion, baseSkillHash, suiteVersion);
            return found;
        }

        @Override
        public List<SkillHiddenEvaluationSuiteSnapshot> list(
                String projectId,
                String skillId,
                int limit) {
            listLimits.add(limit);
            return listed;
        }
    }

    private record FindCall(
            String projectId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion) {
    }
}
