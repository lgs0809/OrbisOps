package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteApplicationService;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteDraft;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuitePort;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteSnapshot;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillHiddenEvaluationSuiteAdminControllerTest {

    private static final String BASE_HASH = "a".repeat(64);

    @Test
    void publishMustRequireAuthenticatedPrincipal() {
        RecordingPort port = new RecordingPort();
        OpsSkillHiddenEvaluationSuiteAdminController controller = controller(port);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.publish("skill-path", request(), null));

        assertEquals("SKILL_HIDDEN_SUITE_AUTHENTICATED_ACTOR_REQUIRED",
                error.getMessage());
        assertEquals(null, port.publishedDraft);
    }

    @Test
    void pathSkillIdMustBeAuthoritativeAndActorMustComeFromPrincipal() {
        RecordingPort port = new RecordingPort();
        port.publishResult = snapshot("skill-path");
        OpsSkillHiddenEvaluationSuiteAdminController controller = controller(port);
        Principal principal = () -> "alice";

        Map<String, Object> data = controller.publish(
                "skill-path", request(), principal).getData();

        assertEquals("skill-path", port.publishedDraft.skillId());
        assertEquals("alice", port.publishedDraft.actor());
        assertEquals("skill-path", data.get("skillId"));
    }

    @Test
    void responseMustExposeOnlySuiteMetadataAndHashes() {
        RecordingPort port = new RecordingPort();
        port.publishResult = snapshot("skill-1");
        OpsSkillHiddenEvaluationSuiteAdminController controller = controller(port);

        Map<String, Object> data = controller.publish(
                "skill-1", request(), () -> "alice").getData();

        assertTrue(data.containsKey("hiddenEvalHash"));
        assertTrue(data.containsKey("mutationEvalHash"));
        assertFalse(data.containsKey("hiddenCases"));
        assertFalse(data.containsKey("mutationCases"));
    }

    @Test
    void requiredTargetFieldsMustFailClosed() {
        OpsSkillHiddenEvaluationSuiteAdminController controller =
                controller(new RecordingPort());

        assertEquals("SKILL_HIDDEN_SUITE_PROJECT_ID_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.publish("skill-1",
                                new OpsSkillHiddenEvaluationSuiteAdminController.PublishRequest(
                                        "", 7, BASE_HASH, "v1",
                                        hidden(), mutation()),
                                () -> "alice")).getMessage());
        assertEquals("SKILL_HIDDEN_SUITE_BASE_VERSION_INVALID",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.publish("skill-1",
                                new OpsSkillHiddenEvaluationSuiteAdminController.PublishRequest(
                                        "project-1", 0, BASE_HASH, "v1",
                                        hidden(), mutation()),
                                () -> "alice")).getMessage());
        assertEquals("SKILL_HIDDEN_SUITE_BASE_HASH_INVALID",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.publish("skill-1",
                                new OpsSkillHiddenEvaluationSuiteAdminController.PublishRequest(
                                        "project-1", 7, "bad", "v1",
                                        hidden(), mutation()),
                                () -> "alice")).getMessage());
        assertEquals("SKILL_HIDDEN_SUITE_VERSION_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.publish("skill-1",
                                new OpsSkillHiddenEvaluationSuiteAdminController.PublishRequest(
                                        "project-1", 7, BASE_HASH, "",
                                        hidden(), mutation()),
                                () -> "alice")).getMessage());
    }

    @Test
    void getMustUsePathSkillAndCompleteTargetParameters() {
        RecordingPort port = new RecordingPort();
        port.findResult = Optional.of(snapshot("skill-path"));
        OpsSkillHiddenEvaluationSuiteAdminController controller = controller(port);

        Map<String, Object> data = controller.get(
                "skill-path", "v1", "project-1", 7L, BASE_HASH).getData();

        assertEquals("skill-path", data.get("skillId"));
        assertEquals(new FindCall(
                        "project-1", "skill-path", 7, BASE_HASH, "v1"),
                port.findCall);
        assertFalse(data.containsKey("hiddenCases"));
        assertFalse(data.containsKey("mutationCases"));
    }

    private OpsSkillHiddenEvaluationSuiteAdminController controller(
            SkillHiddenEvaluationSuitePort port) {
        return new OpsSkillHiddenEvaluationSuiteAdminController(
                new SkillHiddenEvaluationSuiteApplicationService(port));
    }

    private OpsSkillHiddenEvaluationSuiteAdminController.PublishRequest request() {
        return new OpsSkillHiddenEvaluationSuiteAdminController.PublishRequest(
                "project-1",
                7,
                BASE_HASH,
                "v1",
                hidden(),
                mutation());
    }

    private List<Map<String, Object>> hidden() {
        return List.of(Map.of(
                "caseId", "hidden-1",
                "targetSkillId", "forged-body-skill"));
    }

    private List<Map<String, Object>> mutation() {
        return List.of(Map.of("caseId", "mutation-1"));
    }

    private SkillHiddenEvaluationSuiteSnapshot snapshot(String skillId) {
        Instant now = Instant.parse("2026-08-03T01:00:00Z");
        return new SkillHiddenEvaluationSuiteSnapshot(
                "suite-v1",
                "project-1",
                skillId,
                7,
                BASE_HASH,
                "v1",
                "b".repeat(64),
                "c".repeat(64),
                "ACTIVE",
                "alice",
                now,
                now);
    }

    private static final class RecordingPort implements SkillHiddenEvaluationSuitePort {
        private SkillHiddenEvaluationSuiteDraft publishedDraft;
        private SkillHiddenEvaluationSuiteSnapshot publishResult;
        private Optional<SkillHiddenEvaluationSuiteSnapshot> findResult = Optional.empty();
        private FindCall findCall;

        @Override
        public SkillHiddenEvaluationSuiteSnapshot publish(
                SkillHiddenEvaluationSuiteDraft draft) {
            publishedDraft = draft;
            return publishResult;
        }

        @Override
        public Optional<SkillHiddenEvaluationSuiteSnapshot> find(
                String projectId,
                String skillId,
                long baseVersion,
                String baseSkillHash,
                String suiteVersion) {
            findCall = new FindCall(
                    projectId, skillId, baseVersion, baseSkillHash, suiteVersion);
            return findResult;
        }

        @Override
        public List<SkillHiddenEvaluationSuiteSnapshot> list(
                String projectId,
                String skillId,
                int limit) {
            return List.of();
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
