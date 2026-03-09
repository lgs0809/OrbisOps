package cn.lgs.orbisops.application.skill;

public record RestoreSkillCommand(SkillGovernanceCommand governance,
                                  int sourceVersion) {
    public RestoreSkillCommand {
        if (governance == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_COMMAND_REQUIRED");
        governance.requireApproval();
        if (sourceVersion <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
    }
}
