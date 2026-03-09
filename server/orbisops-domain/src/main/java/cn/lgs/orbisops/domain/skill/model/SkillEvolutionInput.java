package cn.lgs.orbisops.domain.skill.model;

import java.util.List;
import java.util.Objects;

/** Neutral trace and message bundle for Skill Evolution analysis. */
public record SkillEvolutionInput(
        List<SkillEvolutionTraceEvent> trace,
        List<SkillEvolutionMessage> messages) {

    public SkillEvolutionInput {
        trace = trace == null ? List.of() : trace.stream().filter(Objects::nonNull).toList();
        messages = messages == null ? List.of() : messages.stream().filter(Objects::nonNull).toList();
    }
}
