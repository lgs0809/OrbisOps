package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;

public record KnowledgeGlobalAuthorizationCommand(
        String projectId,
        String globalKbId,
        KnowledgeStatus status,
        String enabledBy) {

    public KnowledgeGlobalAuthorizationCommand {
        projectId = required(projectId, "KNOWLEDGE_PROJECT_ID_REQUIRED");
        globalKbId = required(globalKbId, "KNOWLEDGE_BASE_ID_REQUIRED");
        status = status == null ? KnowledgeStatus.ENABLED : status;
        enabledBy = required(enabledBy, "KNOWLEDGE_ENABLED_BY_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
