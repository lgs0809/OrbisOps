package cn.lgs.orbisops.domain.agenteval.model;

import java.time.Instant;

public record AgentEvalRunStart(
        String evalRunId,
        String suiteId,
        String projectId,
        String agentId,
        int agentVersion,
        String definitionHash,
        int baselineVersion,
        String baselineDefinitionHash,
        int totalCases,
        String createdBy,
        Instant startedAt) {

    public AgentEvalRunStart {
        evalRunId = required(evalRunId, "AGENT_EVAL_RUN_ID_REQUIRED");
        suiteId = required(suiteId, "AGENT_EVAL_SUITE_ID_REQUIRED");
        projectId = required(projectId, "AGENT_EVAL_PROJECT_REQUIRED");
        agentId = required(agentId, "AGENT_EVAL_AGENT_REQUIRED");
        if (agentVersion <= 0) throw new IllegalArgumentException("AGENT_EVAL_VERSION_INVALID");
        definitionHash = required(definitionHash, "AGENT_EVAL_DEFINITION_HASH_REQUIRED");
        baselineVersion = Math.max(0, baselineVersion);
        baselineDefinitionHash = text(baselineDefinitionHash);
        if (totalCases <= 0) throw new IllegalArgumentException("AGENT_EVAL_CASES_MISSING");
        createdBy = text(createdBy);
        if (startedAt == null) throw new IllegalArgumentException("AGENT_EVAL_TIME_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
