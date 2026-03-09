package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertRuleDefinition(
        Long id,
        String ruleName,
        int status,
        String sourceType,
        String alertNameRegex,
        String severityRegex,
        String serviceRegex,
        Map<String, String> matchLabels,
        String notificationChannelId,
        String notificationTarget,
        String webhookSecret,
        String projectId,
        String agentDefinitionId,
        String agentBindingMode,
        Integer agentVersion,
        String agentDefinitionHash,
        String questionTemplate,
        int rangeMinutes,
        String promWindow,
        boolean includeRecentLogs,
        boolean notifyChannel,
        int subAgentMaxIterations,
        int nodeTimeoutSeconds,
        int maxEvidenceItems,
        int dedupWindowSeconds,
        String createTime,
        String updateTime) {

    public AlertRuleDefinition {
        matchLabels = matchLabels == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(matchLabels));
        createTime = text(createTime);
        updateTime = text(updateTime);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
