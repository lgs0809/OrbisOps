package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertRuleCandidate(
        Long id,
        String ruleName,
        Integer status,
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
        Integer rangeMinutes,
        String promWindow,
        Boolean includeRecentLogs,
        Boolean notifyChannel,
        Integer subAgentMaxIterations,
        Integer nodeTimeoutSeconds,
        Integer maxEvidenceItems,
        Integer dedupWindowSeconds) {

    public AlertRuleCandidate {
        matchLabels = matchLabels == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(matchLabels));
    }
}
