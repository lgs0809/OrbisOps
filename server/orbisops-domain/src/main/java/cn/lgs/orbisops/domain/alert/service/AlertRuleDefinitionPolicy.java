package cn.lgs.orbisops.domain.alert.service;

import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.domain.alert.model.AlertRuleStatus;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class AlertRuleDefinitionPolicy {

    public AlertRuleDefinition normalize(
            AlertRuleCandidate candidate,
            AlertAgentResolution resolution,
            String existingWebhookSecret) {
        if (candidate == null) throw new IllegalArgumentException("ALERT_RULE_REQUIRED");
        if (resolution == null) throw new IllegalArgumentException("ALERT_AGENT_RESOLUTION_REQUIRED");
        String ruleName = required(candidate.ruleName(), "触发规则名称不能为空");
        String projectId = required(candidate.projectId(), "告警触发规则必须选择 projectId");
        String agentId = required(candidate.agentDefinitionId(), "告警触发规则必须选择执行 Agent");
        String bindingMode = bindingMode(candidate.agentBindingMode());
        if ("PINNED_VERSION".equals(bindingMode)
                && (candidate.agentVersion() == null || candidate.agentVersion() <= 0)) {
            throw new IllegalArgumentException("PINNED_VERSION 告警规则必须选择 Agent 版本");
        }
        if ("PINNED_VERSION".equals(bindingMode)
                && text(candidate.agentDefinitionHash()).length() > 0
                && !candidate.agentDefinitionHash().trim().equals(resolution.definitionHash())) {
            throw new SecurityException("ALERT_AGENT_DEFINITION_HASH_MISMATCH");
        }
        boolean notify = Boolean.TRUE.equals(candidate.notifyChannel());
        String channelId = text(candidate.notificationChannelId());
        String target = text(candidate.notificationTarget());
        if (notify && channelId.isBlank()) {
            throw new IllegalArgumentException("启用告警通知时必须选择 Channel");
        }
        if (notify && target.isBlank()) {
            throw new IllegalArgumentException("启用告警通知时必须填写通知目标");
        }
        validateRegex(candidate.alertNameRegex(), "alertNameRegex");
        validateRegex(candidate.severityRegex(), "severityRegex");
        validateRegex(candidate.serviceRegex(), "serviceRegex");
        Map<String, String> labels = normalizeLabels(candidate.matchLabels());
        String webhookSecret = text(candidate.webhookSecret());
        if (candidate.id() != null && webhookSecret.contains("******")) {
            webhookSecret = text(existingWebhookSecret);
        }
        return new AlertRuleDefinition(
                candidate.id(),
                ruleName,
                AlertRuleStatus.require(candidate.status()).code(),
                defaultText(candidate.sourceType(), "ALERTMANAGER").toUpperCase(Locale.ROOT),
                text(candidate.alertNameRegex()),
                text(candidate.severityRegex()),
                text(candidate.serviceRegex()),
                labels,
                channelId,
                target,
                webhookSecret,
                projectId,
                agentId,
                bindingMode,
                resolution.version(),
                resolution.definitionHash(),
                text(candidate.questionTemplate()),
                bounded(candidate.rangeMinutes(), 30, 1, 1440),
                defaultText(candidate.promWindow(), "5m"),
                !Boolean.FALSE.equals(candidate.includeRecentLogs()),
                notify,
                bounded(candidate.subAgentMaxIterations(), 5, 1, 10),
                bounded(candidate.nodeTimeoutSeconds(), 120, 1, 300),
                bounded(candidate.maxEvidenceItems(), 20, 1, 50),
                bounded(candidate.dedupWindowSeconds(), 300, 30, 86400),
                "",
                "");
    }

    public int status(Integer status) {
        return AlertRuleStatus.require(status).code();
    }

    public int eventLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private Map<String, String> normalizeLabels(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) return result;
        source.forEach((key, value) -> {
            String normalizedKey = text(key);
            String normalizedValue = text(value);
            if (!normalizedKey.isBlank()) {
                if (normalizedValue.startsWith("~")) {
                    validateRegex(normalizedValue.substring(1), "matchLabels." + normalizedKey);
                }
                result.put(normalizedKey, normalizedValue);
            }
        });
        return result;
    }

    private void validateRegex(String regex, String field) {
        String value = text(regex);
        if (value.isBlank()) return;
        try {
            Pattern.compile(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("告警触发规则正则非法 " + field + ":" + value, error);
        }
    }

    private String bindingMode(String value) {
        String mode = defaultText(value, "LATEST_PUBLISHED").toUpperCase(Locale.ROOT);
        if (!"LATEST_PUBLISHED".equals(mode) && !"PINNED_VERSION".equals(mode)) {
            throw new IllegalArgumentException("Agent 绑定模式只允许 LATEST_PUBLISHED 或 PINNED_VERSION");
        }
        return mode;
    }

    private int bounded(Integer value, int fallback, int min, int max) {
        int result = value == null ? fallback : value;
        return Math.max(min, Math.min(max, result));
    }

    private String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String defaultText(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
