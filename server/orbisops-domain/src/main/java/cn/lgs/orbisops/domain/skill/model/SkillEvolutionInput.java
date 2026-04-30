package cn.lgs.orbisops.domain.skill.model;

import java.util.List;
import java.util.Objects;

/** Neutral trace and message bundle for Skill Evolution analysis. */
public record SkillEvolutionInput(
        List<SkillEvolutionTraceEvent> trace,
        List<SkillEvolutionMessage> messages,
        String acceptedGoal,
        String episodeJson,
        String sourceHash) {

    public SkillEvolutionInput(List<SkillEvolutionTraceEvent> trace, List<SkillEvolutionMessage> messages) {
        this(trace, messages, "", "", "");
    }

    public SkillEvolutionInput {
        trace = trace == null ? List.of() : trace.stream().filter(Objects::nonNull).toList();
        messages = messages == null ? List.of() : messages.stream().filter(Objects::nonNull).toList();
        acceptedGoal = acceptedGoal == null ? "" : acceptedGoal;
        episodeJson = episodeJson == null ? "" : episodeJson;
        sourceHash = sourceHash == null ? "" : sourceHash;
    }
}
