package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsAlertRuleMapper {

    public AlertRuleCandidate candidate(OpsAlertTriggerRule rule) {
        if (rule == null) throw new IllegalArgumentException("ALERT_RULE_REQUIRED");
        return new AlertRuleCandidate(
                rule.getId(),
                rule.getRuleName(),
                rule.getStatus(),
                rule.getSourceType(),
                rule.getAlertNameRegex(),
                rule.getSeverityRegex(),
                rule.getServiceRegex(),
                parseLabels(rule.getMatchLabelsJson()),
                rule.getNotificationChannelId(),
                rule.getNotificationTarget(),
                rule.getWebhookSecret(),
                rule.getProjectId(),
                rule.getAgentDefinitionId(),
                rule.getAgentBindingMode(),
                rule.getAgentVersion(),
                rule.getAgentDefinitionHash(),
                rule.getQuestionTemplate(),
                rule.getRangeMinutes(),
                rule.getPromWindow(),
                rule.getIncludeRecentLogs(),
                rule.getNotifyChannel(),
                rule.getSubAgentMaxIterations(),
                rule.getNodeTimeoutSeconds(),
                rule.getMaxEvidenceItems(),
                rule.getDedupWindowSeconds());
    }

    public OpsAlertTriggerRule view(AlertRuleDefinition rule) {
        if (rule == null) return null;
        return OpsAlertTriggerRule.builder()
                .id(rule.id())
                .ruleName(rule.ruleName())
                .status(rule.status())
                .sourceType(rule.sourceType())
                .alertNameRegex(rule.alertNameRegex())
                .severityRegex(rule.severityRegex())
                .serviceRegex(rule.serviceRegex())
                .matchLabelsJson(rule.matchLabels().isEmpty() ? "" : JSON.toJSONString(rule.matchLabels()))
                .notificationChannelId(rule.notificationChannelId())
                .notificationTarget(rule.notificationTarget())
                .webhookSecret(rule.webhookSecret())
                .projectId(rule.projectId())
                .agentDefinitionId(rule.agentDefinitionId())
                .agentBindingMode(rule.agentBindingMode())
                .agentVersion(rule.agentVersion())
                .agentDefinitionHash(rule.agentDefinitionHash())
                .questionTemplate(rule.questionTemplate())
                .rangeMinutes(rule.rangeMinutes())
                .promWindow(rule.promWindow())
                .includeRecentLogs(rule.includeRecentLogs())
                .notifyChannel(rule.notifyChannel())
                .subAgentMaxIterations(rule.subAgentMaxIterations())
                .nodeTimeoutSeconds(rule.nodeTimeoutSeconds())
                .maxEvidenceItems(rule.maxEvidenceItems())
                .dedupWindowSeconds(rule.dedupWindowSeconds())
                .createTime(rule.createTime())
                .updateTime(rule.updateTime())
                .build();
    }

    public List<OpsAlertTriggerRule> views(List<AlertRuleDefinition> rules) {
        return rules == null ? List.of() : rules.stream().map(this::view).toList();
    }

    private Map<String, String> parseLabels(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> result = JSON.parseObject(
                    json, new TypeReference<LinkedHashMap<String, String>>() { });
            return result == null ? Map.of() : new LinkedHashMap<>(result);
        } catch (Exception error) {
            throw new IllegalArgumentException("ALERT_RULE_MATCH_LABELS_JSON_INVALID", error);
        }
    }
}
