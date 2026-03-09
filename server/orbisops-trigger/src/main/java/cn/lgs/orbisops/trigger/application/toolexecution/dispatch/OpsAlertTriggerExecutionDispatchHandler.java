package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class OpsAlertTriggerExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final AlertRuleManagementApplicationService rules;
    private final AlertEventApplicationService events;
    private final OpsAlertRuleMapper ruleMapper;
    private final OpsAlertEventMapper eventMapper;

    public OpsAlertTriggerExecutionDispatchHandler(
            AlertRuleManagementApplicationService rules,
            AlertEventApplicationService events,
            OpsAlertRuleMapper ruleMapper,
            OpsAlertEventMapper eventMapper) {
        this.rules = rules;
        this.events = events;
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    public String handlerId() {
        return "alert-trigger";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "alert.trigger".equals(target.toolsetId())
                || "ALERT_TRIGGER".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        Map<String, Object> arguments = request.arguments();
        String projectId = text(first(request.requestContext().get("projectId"), arguments.get("projectId")), "");
        return switch (target.toolName()) {
            case "alert_trigger_list" -> Map.of("items", listRules().stream()
                    .filter(rule -> !StringUtils.hasText(projectId) || projectId.equals(rule.getProjectId()))
                    .toList());
            case "alert_trigger_create" -> Map.of(
                    "status", "SUCCEEDED",
                    "rule", ruleMapper.view(rules.save(
                            ruleMapper.candidate(rule(arguments, projectId, false)), request.actor())));
            case "alert_trigger_update" -> {
                Long id = longValue(arguments.get("id"), "更新告警规则必须提供 id");
                requireProject(id, projectId);
                yield Map.of(
                        "status", "SUCCEEDED",
                        "rule", ruleMapper.view(rules.save(
                                ruleMapper.candidate(rule(arguments, projectId, true)), request.actor())));
            }
            case "alert_trigger_status" -> {
                Long id = longValue(arguments.get("id"), "启停告警规则必须提供 id");
                requireProject(id, projectId);
                yield status(rules.updateStatus(id, intValue(arguments.get("status"), 0), request.actor()));
            }
            case "alert_trigger_delete" -> {
                Long id = longValue(arguments.get("id"), "删除告警规则必须提供 id");
                requireProject(id, projectId);
                yield status(rules.delete(id, request.actor()));
            }
            case "alert_trigger_events" -> Map.of("items", eventMapper.views(
                            events.list(intValue(arguments.get("limit"), 50))).stream()
                    .filter(event -> !StringUtils.hasText(projectId) || listRules().stream()
                            .anyMatch(rule -> rule.getId() != null
                                    && rule.getId().equals(event.getRuleId())
                                    && projectId.equals(rule.getProjectId())))
                    .toList());
            default -> throw new IllegalArgumentException("未知告警触发规则工具：" + target.toolName());
        };
    }

    private Map<String, Object> status(boolean ok) {
        return Map.of("status", ok ? "SUCCEEDED" : "FAILED");
    }

    private OpsAlertTriggerRule rule(Map<String, Object> arguments, String projectId, boolean requireId) {
        Long id = null;
        if (arguments.get("id") != null) {
            id = longValue(arguments.get("id"), "告警规则 id 不合法");
        } else if (requireId) {
            throw new IllegalArgumentException("更新告警规则必须提供 id");
        }
        int rangeMinutes = intValue(arguments.get("rangeMinutes"), 15);
        return OpsAlertTriggerRule.builder()
                .id(id)
                .projectId(projectId)
                .ruleName(text(arguments.get("ruleName"), "项目告警触发"))
                .status(intValue(arguments.get("status"), 1))
                .sourceType(text(arguments.get("sourceType"), "ALERTMANAGER"))
                .alertNameRegex(text(arguments.get("alertNameRegex"), ".*"))
                .severityRegex(text(arguments.get("severityRegex"), ".*"))
                .serviceRegex(text(arguments.get("serviceRegex"), ".*"))
                .matchLabelsJson(text(arguments.get("matchLabelsJson"), "{}"))
                .notificationChannelId(text(arguments.get("notificationChannelId"), ""))
                .notificationTarget(text(arguments.get("notificationTarget"), ""))
                .webhookSecret(text(arguments.get("webhookSecret"), ""))
                .agentDefinitionId(text(arguments.get("agentDefinitionId"),
                        text(arguments.get("agentId"), "generic-ops-react-agent")))
                .questionTemplate(text(arguments.get("questionTemplate"),
                        "收到告警 {{alertName}}，请分析 {{service}} 在最近 " + rangeMinutes
                                + " 分钟内的指标、日志和变更风险，给出处置建议。"))
                .rangeMinutes(rangeMinutes)
                .promWindow(text(arguments.get("promWindow"), rangeMinutes + "m"))
                .includeRecentLogs(bool(arguments.get("includeRecentLogs"), true))
                .notifyChannel(bool(arguments.get("notifyChannel"), false))
                .subAgentMaxIterations(intValue(arguments.get("subAgentMaxIterations"), 3))
                .nodeTimeoutSeconds(intValue(arguments.get("nodeTimeoutSeconds"), 60))
                .maxEvidenceItems(intValue(arguments.get("maxEvidenceItems"), 10))
                .dedupWindowSeconds(intValue(arguments.get("dedupWindowSeconds"), 900))
                .build();
    }

    private void requireProject(Long id, String projectId) {
        OpsAlertTriggerRule rule = listRules().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("告警规则不存在：" + id));
        if (StringUtils.hasText(projectId) && !projectId.equals(rule.getProjectId())) {
            throw new SecurityException("ALERT_RULE_PROJECT_MISMATCH：不能跨项目修改告警规则");
        }
    }

    private java.util.List<OpsAlertTriggerRule> listRules() {
        return ruleMapper.views(rules.list());
    }

    private Object first(Object first, Object second) {
        return first != null ? first : second;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Long longValue(Object value, String message) {
        try {
            if (value == null) throw new NumberFormatException("null");
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message, error);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean flag) return flag;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
