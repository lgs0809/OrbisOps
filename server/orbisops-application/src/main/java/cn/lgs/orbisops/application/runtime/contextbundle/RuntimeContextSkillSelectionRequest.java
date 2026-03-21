package cn.lgs.orbisops.application.runtime.contextbundle;

import java.util.List;

public record RuntimeContextSkillSelectionRequest(
        String projectId,
        String agentId,
        List<String> requestedSkillIds,
        String query,
        int limit) {

    public RuntimeContextSkillSelectionRequest {
        projectId = text(projectId);
        agentId = text(agentId);
        requestedSkillIds = requestedSkillIds == null ? List.of() : List.copyOf(requestedSkillIds);
        query = text(query);
        limit = Math.max(1, limit);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
