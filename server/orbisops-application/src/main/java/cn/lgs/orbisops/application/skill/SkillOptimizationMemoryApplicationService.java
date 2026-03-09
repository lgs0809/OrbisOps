package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemory;

import java.util.List;

/** Use only from Skill optimization, routing evaluation and release regression workflows. */
public final class SkillOptimizationMemoryApplicationService {

    private static final int MAX_QUERY_LIMIT = 200;

    private final SkillOptimizationMemoryPort port;
    private final SkillOptimizationClockPort clock;

    public SkillOptimizationMemoryApplicationService(
            SkillOptimizationMemoryPort port,
            SkillOptimizationClockPort clock) {
        if (port == null || clock == null) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_MEMORY_DEPENDENCY_REQUIRED");
        }
        this.port = port;
        this.clock = clock;
    }

    public SkillOptimizationMemory record(SkillOptimizationMemoryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_MEMORY_COMMAND_REQUIRED");
        }
        return port.save(new SkillOptimizationMemory(
                command.memoryId(),
                command.skillId(),
                command.skillVersion(),
                command.type(),
                command.summary(),
                command.evidenceIds(),
                command.modelCompatibility(),
                command.environmentCompatibility(),
                command.effective(),
                clock.now()));
    }

    public List<SkillOptimizationMemory> memories(
            String skillId,
            int limit) {
        String id = required(skillId, "SKILL_OPTIMIZATION_MEMORY_SKILL_ID_REQUIRED");
        return List.copyOf(port.findBySkill(id, bounded(limit)));
    }

    private int bounded(int limit) {
        if (limit <= 0) return 20;
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
