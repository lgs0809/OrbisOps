package cn.lgs.orbisops.domain.skill.model;

/** Neutral conversation message used by Skill Evolution input analysis. */
public record SkillEvolutionMessage(
        String role,
        String content) {

    public SkillEvolutionMessage {
        role = role == null ? "" : role.trim();
        content = content == null ? "" : content;
    }
}
