package cn.lgs.orbisops.domain.agenteval.model;

import java.time.Instant;

public record AgentEvalCaseExecution(
        String caseRunId,
        String evalRunId,
        String caseId,
        String projectId,
        String agentId,
        int agentVersion,
        AgentEvalCaseResult result,
        Instant startedAt,
        Instant finishedAt) {

    public AgentEvalCaseExecution {
        caseRunId = required(caseRunId, "AGENT_EVAL_CASE_RUN_ID_REQUIRED");
        evalRunId = required(evalRunId, "AGENT_EVAL_RUN_ID_REQUIRED");
        caseId = required(caseId, "AGENT_EVAL_CASE_ID_REQUIRED");
        projectId = required(projectId, "AGENT_EVAL_PROJECT_REQUIRED");
        agentId = required(agentId, "AGENT_EVAL_AGENT_REQUIRED");
        if (agentVersion <= 0) throw new IllegalArgumentException("AGENT_EVAL_VERSION_INVALID");
        if (result == null) throw new IllegalArgumentException("AGENT_EVAL_CASE_RESULT_REQUIRED");
        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("AGENT_EVAL_CASE_TIME_REQUIRED");
        }
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
