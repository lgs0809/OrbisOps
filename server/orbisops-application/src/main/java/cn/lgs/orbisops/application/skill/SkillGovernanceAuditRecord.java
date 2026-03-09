package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;

import java.time.LocalDateTime;

public record SkillGovernanceAuditRecord(String action,
                                         String scope,
                                         String projectId,
                                         String skillId,
                                         String actor,
                                         String reason,
                                         String approvalId,
                                         SkillGovernanceState before,
                                         SkillGovernanceState after,
                                         LocalDateTime occurredAt) {
}
