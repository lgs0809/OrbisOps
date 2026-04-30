package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBindingMode;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleStatus;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillLockType;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillLockedCandidateGovernanceTest {

    @Test
    void lockedSkillCanStillFormCandidateButCannotAutoPublish() {
        SkillPatchCandidatePort port = mock(SkillPatchCandidatePort.class);
        when(port.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SkillPatchCandidateApplicationService candidates = new SkillPatchCandidateApplicationService(port);
        SkillPatchCandidate candidate = candidates.createCandidate(Map.of(
                "projectId", "project-a",
                "targetSkillId", "skill-a",
                "baseSkillVersion", 3,
                "baseSkillHash", "hash-3",
                "patchType", "UPDATE",
                "riskLevel", "LOW",
                "targetMutationMode", "LOCKED",
                "changes", java.util.List.of(Map.of("path", "SKILL.md"))));
        SkillCatalogEntry locked = lockedEntry();

        assertEquals(SkillPatchCandidateStatus.CANDIDATE, candidate.status());
        assertEquals("skill-a", candidate.targetSkillId());
        assertEquals("LOCKED_SKIP", locked.autoPublishSkipReason());
    }

    private SkillCatalogEntry lockedEntry() {
        SkillGovernanceState state = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.LOCKED, SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.STABILITY_LOCK, "stable baseline", "admin", "approval-1",
                        LocalDateTime.parse("2026-08-02T00:00:00")), false);
        return new SkillCatalogEntry(
                1L, "skill-a", "project-a", "Skill A", "PROJECT", "", "desc", "content",
                3, state.legacyStatusProjection(), "admin", null, null, "EVOLVED",
                state.legacyUpdateModeProjection(), true, true, null,
                state.lock().reason(), state.lock().actor(), state.lock().lockedAt(),
                "hash-3", 3, "hash-3", 3, "package-3", "{}", "{}", state);
    }
}
