package cn.lgs.orbisops.domain.investigation.model;

import java.util.List;

/** Framework-free SubAgent query decision. */
public record InvestigationSubAgentQueryDecision(
        boolean llmGenerated,
        String reason,
        Integer rangeMinutes,
        String promWindow,
        Boolean includeRecentLogs,
        String retrievalMode,
        String queryFocus,
        Boolean requireExactFilters,
        List<String> expectedEvidence) {
}
