package cn.lgs.orbisops.application.skill;

public interface SkillProjectValidationPort {

    void requireExisting(String projectId);

    default java.util.List<String> configuredSkillIds(String projectId) { return java.util.List.of(); }
}
