package cn.lgs.orbisops.domain.agenteval.model;

import java.util.List;

public record AgentEvalRunResult(
        String evalRunId,
        String suiteId,
        String projectId,
        String agentId,
        int agentVersion,
        String definitionHash,
        int baselineVersion,
        String regressionStatus,
        String status,
        int totalCases,
        int passedCases,
        int failedCases,
        List<AgentEvalCaseExecution> caseExecutions,
        List<AgentEvalCaseResult> baselineResults) {

    public AgentEvalRunResult {
        evalRunId = required(evalRunId, "AGENT_EVAL_RUN_ID_REQUIRED");
        suiteId = required(suiteId, "AGENT_EVAL_SUITE_ID_REQUIRED");
        projectId = required(projectId, "AGENT_EVAL_PROJECT_REQUIRED");
        agentId = required(agentId, "AGENT_EVAL_AGENT_REQUIRED");
        definitionHash = required(definitionHash, "AGENT_EVAL_DEFINITION_HASH_REQUIRED");
        regressionStatus = required(regressionStatus, "AGENT_EVAL_REGRESSION_STATUS_REQUIRED");
        status = required(status, "AGENT_EVAL_STATUS_REQUIRED");
        totalCases = Math.max(0, totalCases);
        passedCases = Math.max(0, passedCases);
        failedCases = Math.max(0, failedCases);
        caseExecutions = caseExecutions == null ? List.of() : List.copyOf(caseExecutions);
        baselineResults = baselineResults == null ? List.of() : List.copyOf(baselineResults);
    }

    public boolean passed() {
        return "PASSED".equals(status)
                && "PASSED".equals(regressionStatus)
                && failedCases == 0;
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
