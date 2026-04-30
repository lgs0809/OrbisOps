package cn.lgs.orbisops.domain.skill.model;

public record SkillBehaviorFrozenSnapshot(
        String modelId,
        String modelVersion,
        double temperature,
        long seed,
        String inputHash,
        String toolSnapshotHash,
        String mcpSchemaHash,
        String baselineSkillHash,
        String candidateSkillHash,
        String verifierVersion
) {

    public SkillBehaviorFrozenSnapshot {
        modelId = required(modelId, "SKILL_REPLAY_MODEL_ID_REQUIRED");
        modelVersion = required(modelVersion, "SKILL_REPLAY_MODEL_VERSION_REQUIRED");
        if (!Double.isFinite(temperature) || temperature < 0D || temperature > 2D) {
            throw new IllegalArgumentException("SKILL_REPLAY_TEMPERATURE_INVALID");
        }
        inputHash = required(inputHash, "SKILL_REPLAY_INPUT_HASH_REQUIRED");
        toolSnapshotHash = required(toolSnapshotHash, "SKILL_REPLAY_TOOL_SNAPSHOT_HASH_REQUIRED");
        mcpSchemaHash = required(mcpSchemaHash, "SKILL_REPLAY_MCP_SCHEMA_HASH_REQUIRED");
        baselineSkillHash = required(baselineSkillHash, "SKILL_REPLAY_BASELINE_HASH_REQUIRED");
        candidateSkillHash = required(candidateSkillHash, "SKILL_REPLAY_CANDIDATE_HASH_REQUIRED");
        verifierVersion = required(verifierVersion, "SKILL_REPLAY_VERIFIER_VERSION_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
