package cn.lgs.orbisops.application.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outbound boundary for structured Skill candidate authoring. */
public interface SkillEvolutionAuthoringPort {

    default boolean available() { return true; }

    SkillEvolutionAuthoredCandidate author(Map<String, Object> input);
    default SkillEvolutionAuthoredCandidate author(Map<String,Object> input,SkillAuthoringProgressPort progress) {return author(input);}

    /** Compatibility bridge: legacy authoring adapters are invoked once per explicit direction. */
    default List<SkillEvolutionAuthoredCandidate> authorCandidates(
            Map<String, Object> input,
            int candidateBudget) {
        int budget = Math.max(1, Math.min(4, candidateBudget));
        List<SkillEvolutionAuthoredCandidate> result = new ArrayList<>();
        List<SkillAuthoringDirection> directions = requestedDirections(input, budget);
        for (int index = 0; index < budget; index++) {
            Map<String, Object> request = new LinkedHashMap<>(input == null ? Map.of() : input);
            request.put("candidateDirection", directions.get(index).name());
            request.put("candidateIndex", index);
            request.put("candidateBudget", budget);
            SkillEvolutionAuthoredCandidate candidate = author(Map.copyOf(request));
            result.add(candidate == null
                    ? SkillEvolutionAuthoredCandidate.from(Map.of())
                    : candidate);
        }
        return List.copyOf(result);
    }

    private List<SkillAuthoringDirection> requestedDirections(
            Map<String, Object> input,
            int budget) {
        List<SkillAuthoringDirection> result = new ArrayList<>();
        Object configured = input == null ? null : input.get("candidateDirections");
        if (configured instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                try {
                    SkillAuthoringDirection direction = SkillAuthoringDirection.valueOf(
                            String.valueOf(value).trim());
                    if (!result.contains(direction)) result.add(direction);
                } catch (RuntimeException ignored) {
                    // Invalid directions remain outside the trusted authoring request.
                }
            }
        }
        if (budget == 1 && result.isEmpty()) return List.of(SkillAuthoringDirection.MAINTENANCE_REVIEW);
        for (SkillAuthoringDirection direction : defaultDirections()) {
            if (!result.contains(direction)) result.add(direction);
        }
        return result.stream().limit(budget).toList();
    }

    private List<SkillAuthoringDirection> defaultDirections() {
        return List.of(
                SkillAuthoringDirection.MINIMAL_PATCH,
                SkillAuthoringDirection.ROUTING_PATCH,
                SkillAuthoringDirection.PROCEDURE_PATCH,
                SkillAuthoringDirection.EVIDENCE_PATCH);
    }
}
