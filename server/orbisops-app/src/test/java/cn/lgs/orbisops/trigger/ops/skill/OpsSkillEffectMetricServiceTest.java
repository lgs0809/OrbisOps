package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEffectMetricApplicationService;
import cn.lgs.orbisops.application.skill.SkillEffectMetricPort;
import cn.lgs.orbisops.domain.skill.model.SkillEffectThresholds;
import cn.lgs.orbisops.domain.skill.service.SkillEffectDegradationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsSkillEffectMetricServiceTest {

    private SkillEffectMetricPort metricPort;
    private OpsSkillEffectMetricService service;

    @BeforeEach
    void setUp() {
        metricPort = mock(SkillEffectMetricPort.class);
        service = new OpsSkillEffectMetricService(
                new SkillEffectMetricApplicationService(
                        metricPort,
                        new SkillEffectDegradationPolicy(),
                        new SkillEffectThresholds(
                                10,
                                0.25D,
                                0.30D,
                                0.20D,
                                0.15D,
                                0.15D,
                                0.50D)));
    }

    @Test
    void detectsCandidateRegressionAgainstPublishedBaseline() {
        assertEquals("SUCCESS_RATE_REGRESSION", service.degradationReason(
                metrics(20, 12, 17, 42, 0, 0, 0),
                metrics(20, 18, 18, 40, 0, 0, 0)));
    }

    @Test
    void acceptsCandidateThatDoesNotRegressAndStaysInsideAbsoluteLimits() {
        assertEquals("", service.degradationReason(
                metrics(20, 17, 17, 42, 2, 2, 1),
                metrics(20, 16, 16, 40, 2, 2, 1)));
    }

    @Test
    void blocksAbsoluteSafetyRegressionWithoutBaseline() {
        assertEquals("BLOCKED_TOOL_RATE_DEGRADED", service.degradationReason(
                metrics(20, 18, 18, 40, 6, 0, 0),
                Map.of()));
    }

    @Test
    void delegatesOnlyNewPostRunFactsToThePersistencePort() {
        Map<String, Object> before = Map.of(
                "changePackageApproved",
                false);
        Map<String, Object> after = Map.of(
                "changePackageApproved",
                true,
                "landingSucceeded",
                true);

        service.applyOutcomeDelta(
                "p1",
                "diagnosis",
                3,
                before,
                after);

        verify(metricPort).applyOutcomeDelta(
                "p1",
                "diagnosis",
                3,
                before,
                after);
    }

    private Map<String, Object> metrics(
            int used,
            int success,
            int evidence,
            int toolCalls,
            int blocked,
            int replan,
            int negative) {
        return Map.of(
                "used_run_count", used,
                "successful_run_count", success,
                "evidence_sufficient_count", evidence,
                "tool_call_count", toolCalls,
                "blocked_tool_call_count", blocked,
                "needs_replan_count", replan,
                "user_negative_feedback_count", negative);
    }
}
