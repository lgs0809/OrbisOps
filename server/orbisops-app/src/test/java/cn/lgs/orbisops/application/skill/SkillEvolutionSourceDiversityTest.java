package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionOpportunityPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillEvolutionSourceDiversityTest {

    @Test
    void repeatedEventsFromOneSessionDoNotCreateCandidate() {
        SkillEvolutionSignalApplicationService signals =
                mock(SkillEvolutionSignalApplicationService.class);
        SkillExperienceApplicationService experiences =
                mock(SkillExperienceApplicationService.class);
        SkillPatchCandidateApplicationService candidates =
                mock(SkillPatchCandidateApplicationService.class);
        SkillReleaseApplicationService releases =
                mock(SkillReleaseApplicationService.class);
        SkillEvolutionAuthoringPort authoring =
                mock(SkillEvolutionAuthoringPort.class);
        SkillEvolutionSimilarityPort similarity =
                mock(SkillEvolutionSimilarityPort.class);
        SkillEvolutionPipelineAuditPort audit =
                mock(SkillEvolutionPipelineAuditPort.class);

        when(signals.record(any())).thenReturn(new SkillEvolutionSignalSnapshot(
                "signal-1", "idem-1", "p1", "a1", "run-3",
                "session-1", "SUCCESSFUL_DIAGNOSTIC_PATTERN", "{}",
                "PENDING", Instant.now()));
        when(signals.createHint(any())).thenReturn(new SkillEvolutionHintSnapshot(
                "hint-1", "signal-1", "p1", "run-3",
                "SUCCESSFUL_DIAGNOSTIC_PATTERN", "{}", "PENDING",
                Instant.now()));
        SkillExperienceObservation observation = new SkillExperienceObservation(
                "episode-3", "observation-3", "cluster-1", "p1", "a1",
                "run-3", "session-1", "SUCCESSFUL_DIAGNOSTIC_PATTERN",
                new SkillExperienceTaskTemplate(
                        "OPS_INVESTIGATION", "接口错误", "SUCCESS",
                        List.of("LOGS"), "SUCCEEDED"),
                "task-hash", List.of("QUERY_LOGS"), "trajectory-hash",
                "SUCCEEDED", List.of(), "根因和证据已验证".repeat(10),
                3, 1D);
        when(experiences.recordObservation(any())).thenReturn(
                new SkillExperienceRecordResult(
                        observation,
                        new SkillExperienceClusterSnapshot(
                                3, 3, 3, "ACCUMULATING", 3),
                        true));
        when(experiences.clusterEvidence(
                "p1", "a1", "cluster-1")).thenReturn(
                new SkillExperienceClusterEvidence(
                        3, 1, 0, 3,
                        List.of("SUCCESSFUL_DIAGNOSTIC_PATTERN")));

        SkillEvolutionApplicationService service =
                new SkillEvolutionApplicationService(
                        signals,
                        experiences,
                        candidates,
                        releases,
                        authoring,
                        similarity,
                        audit,
                        new SkillEvolutionPayloadCodec(),
                        new SkillEvolutionOpportunityPolicy(),
                        new SkillEvolutionPromotionSettings(3, 2, 2, 2, 12));
        SkillEvolutionInputSummary summary = new SkillEvolutionInputSummary(
                List.of("完成"),
                List.of("log query"),
                "排查接口错误",
                "因为日志证据显示同类错误，先查询日志然后验证根因。".repeat(4),
                true,
                true,
                true,
                List.of(new cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference(
                        "e1", "r1", "h1")),
                "context-hash");

        SkillEvolutionPipelineDecision decision = service.decide(
                new SkillEvolutionPipelineRequest(
                        "p1", "a1", "run-3", "session-1",
                        "RUN_COMPLETED", summary));

        assertTrue(decision.skipped());
        assertEquals(
                "SKIP_INSUFFICIENT_SOURCE_DIVERSITY",
                decision.reasonCode());
        verify(authoring, never()).author(any());
        verify(candidates, never()).create(any());
    }
}
