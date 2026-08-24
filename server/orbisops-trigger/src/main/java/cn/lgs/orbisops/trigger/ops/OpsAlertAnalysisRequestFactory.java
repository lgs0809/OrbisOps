package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookProtocolService.AlertView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/** Alert rule and webhook view to version-bound analysis request boundary. */
final class OpsAlertAnalysisRequestFactory {

    OpsAgentRunRequestDTO build(
            OpsAgentDefinitionQueryGateway agentDefinitions,
            OpsAlertTriggerRule rule,
            AlertView alert,
            String eventType,
            long occurrenceCount,
            String triggerSource) {
        OpsAgentDefinition agent = resolveAgent(agentDefinitions, rule);
        return OpsAgentRunRequestDTO.builder()
                .projectId(rule.getProjectId())
                .requestedBy("alertmanager:rule-"
                        + Optional.ofNullable(rule.getId()).orElse(0L))
                .rangeMinutes(rule.getRangeMinutes())
                .promWindow(rule.getPromWindow())
                .includeRecentLogs(rule.getIncludeRecentLogs())
                .question(renderQuestion(agent, rule, alert, eventType, occurrenceCount))
                .agentDefinitionId(agent.getAgentId())
                .agentVersion(agent.getVersion())
                .agentDefinitionSnapshotJson(JSON.toJSONString(agent))
                .executionStyle(executionStyle(agent))
                .subAgentMaxIterations(rule.getSubAgentMaxIterations())
                .nodeTimeoutSeconds(rule.getNodeTimeoutSeconds())
                .maxEvidenceItems(rule.getMaxEvidenceItems())
                .notifyChannel(rule.getNotifyChannel())
                .notificationChannelId(rule.getNotificationChannelId())
                .notificationTarget(rule.getNotificationTarget())
                .triggerSource(triggerSource)
                .triggerEventId(alert.fingerprint())
                .build();
    }

    private OpsAgentDefinition resolveAgent(
            OpsAgentDefinitionQueryGateway agentDefinitions,
            OpsAlertTriggerRule rule) {
        if (agentDefinitions == null) {
            throw new IllegalStateException("ALERT_AGENT_REGISTRY_UNAVAILABLE");
        }
        String mode = bindingMode(rule.getAgentBindingMode());
        Integer version = "PINNED_VERSION".equals(mode)
                ? rule.getAgentVersion()
                : null;
        if ("PINNED_VERSION".equals(mode)
                && (version == null || version <= 0)) {
            throw new IllegalArgumentException(
                    "PINNED_VERSION 告警规则必须选择 Agent 版本");
        }
        OpsAgentDefinition resolved = agentDefinitions.resolveForProject(
                rule.getAgentDefinitionId(),
                version,
                false,
                rule.getProjectId());
        if (resolved == null
                || resolved.getVersion() == null
                || resolved.getVersion() <= 0
                || !StringUtils.hasText(resolved.getDefinitionHash())) {
            throw new IllegalStateException("ALERT_AGENT_VERSION_INCOMPLETE");
        }
        if ("PINNED_VERSION".equals(mode)
                && StringUtils.hasText(rule.getAgentDefinitionHash())
                && !rule.getAgentDefinitionHash().equals(
                        resolved.getDefinitionHash())) {
            throw new SecurityException(
                    "ALERT_AGENT_DEFINITION_HASH_MISMATCH");
        }
        return resolved;
    }

    private String executionStyle(OpsAgentDefinition agent) {
        if (agent == null) return "REACT";
        return AgentDefinitionKind.SPECIALIZED_WORKFLOW == AgentDefinitionKind.parse(agent.getDefinitionKind())
                ? "WORKFLOW"
                : "REACT";
    }

    private String bindingMode(String value) {
        String mode = firstText(value, "LATEST_PUBLISHED").toUpperCase();
        if (!List.of("LATEST_PUBLISHED", "PINNED_VERSION").contains(mode)) {
            throw new IllegalArgumentException(
                    "Agent 绑定模式只允许 LATEST_PUBLISHED 或 PINNED_VERSION");
        }
        return mode;
    }

    private String renderQuestion(
            OpsAgentDefinition agent,
            OpsAlertTriggerRule rule,
            AlertView alert,
            String eventType,
            long occurrenceCount) {
        String template = StringUtils.hasText(rule.getQuestionTemplate())
                ? rule.getQuestionTemplate()
                : """
                系统收到生产告警，请自动分析根因、影响面和处置建议。
                告警：${alertName}
                级别：${severity}
                服务：${service}
                生命周期：${alertStatus} / ${eventType}
                聚合出现次数：${occurrenceCount}
                摘要：${summary}
                描述：${description}
                标签：${labels}
                注解：${annotations}
                """;
        if ("WORKFLOW".equals(executionStyle(agent)) && template.stripLeading().startsWith("{")) {
            return new OpsAlertWorkflowInputRenderer().render(template, rule, alert, eventType, occurrenceCount);
        }
        return template
                .replace("${alertName}", value(alert.alertName()))
                .replace("${severity}", value(alert.severity()))
                .replace("${service}", value(alert.service()))
                .replace("${alertStatus}", value(alert.status()))
                .replace("${eventType}", value(eventType))
                .replace("${occurrenceCount}",
                        String.valueOf(Math.max(1, occurrenceCount)))
                .replace("${summary}", value(alert.annotation("summary")))
                .replace("${description}",
                        value(alert.annotation("description")))
                .replace("${labels}", JSON.toJSONString(alert.labels()))
                .replace("${annotations}",
                        JSON.toJSONString(alert.annotations()));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
