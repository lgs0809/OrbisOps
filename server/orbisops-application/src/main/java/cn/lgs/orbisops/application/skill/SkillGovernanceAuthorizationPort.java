package cn.lgs.orbisops.application.skill;

public interface SkillGovernanceAuthorizationPort {

    void require(String actor,
                 SkillGovernancePermission permission,
                 String scope,
                 String projectId,
                 String skillId);
}
