package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fixed, non-agentic management path for inspection tasks and alert rules. */
@Service
public class OpsAutomationControlActionService {

    private final OpsToolExecutionService toolExecutionService;

    public OpsAutomationControlActionService(OpsToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    public OpsAgentChatResponse execute(OpsAgentChatRequest request, Consumer<OpsRuntimeEvent> eventSink) {
        if (request == null || !StringUtils.hasText(request.getProjectId())) {
            throw new IllegalArgumentException("AUTOMATION_PROJECT_REQUIRED");
        }
        Command command = isAlertRule(request.getQuery()) ? alertCommand(request) : inspectionCommand(request);
        Map<String, Object> toolRequest = new LinkedHashMap<>();
        toolRequest.put("projectId", request.getProjectId());
        toolRequest.put("sessionId", text(request.getSessionId()));
        toolRequest.put("runId", text(request.getRunId()));
        toolRequest.put("userId", text(request.getUserId()));
        toolRequest.put("executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name());
        toolRequest.put("toolsetId", command.toolsetId());
        toolRequest.put("toolName", command.toolName());
        toolRequest.put("arguments", command.arguments());
        Map<String, Object> envelope = toolExecutionService.execute(toolRequest, request.getUserId());
        boolean allowed = !Boolean.FALSE.equals(envelope.get("allowed"));
        String status = allowed ? "SUCCEEDED" : "BLOCKED";
        String output = humanOutput(command, envelope, allowed);
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType(allowed ? "AUTOMATION_CONTROL_ACTION_COMPLETED" : "AUTOMATION_CONTROL_ACTION_BLOCKED")
                .status(status)
                .summary(output)
                .payload(Map.of(
                        "toolsetId", command.toolsetId(),
                        "toolName", command.toolName(),
                        "resultId", text(envelope.get("resultId")),
                        "outputHash", text(envelope.get("outputHash"))))
                .build();
        if (eventSink != null) eventSink.accept(event);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("runId", text(request.getRunId()));
        metadata.put("fixedAction", true);
        metadata.put("controlStatus", status);
        metadata.put("toolsetId", command.toolsetId());
        metadata.put("toolName", command.toolName());
        metadata.put("resultId", text(envelope.get("resultId")));
        metadata.put("outputHash", text(envelope.get("outputHash")));
        metadata.put("reasonCode", text(envelope.get("reasonCode")));
        return OpsAgentChatResponse.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .agentId(text(request.getAgentDefinitionId()))
                .mode("FIXED_SERVICE")
                .engine("AUTOMATION_CONTROL")
                .content(output)
                .events(List.of(event))
                .metadata(metadata)
                .build();
    }

    private Command inspectionCommand(OpsAgentChatRequest request) {
        String query = text(request.getQuery());
        String action = action(query, true);
        Long id = extractId(query);
        Map<String, Object> args = baseArguments(request, action, id);
        if ("create".equals(action) || "update".equals(action)) {
            String name = extractName(query);
            if (!StringUtils.hasText(name) && "create".equals(action)) {
                name = "项目巡检-" + DateTimeFormatter.ofPattern("MMddHHmm").format(LocalDateTime.now());
            }
            if (StringUtils.hasText(name)) args.put("taskName", name);
            String cron = extractCron(query);
            if (StringUtils.hasText(cron)) args.put("cronExpression", cron);
            int range = extractMinutes(query, 15);
            Map<String, Object> taskParam = new LinkedHashMap<>();
            taskParam.put("projectId", request.getProjectId());
            taskParam.put("prompt", inspectionObjective(query));
            taskParam.put("rangeMinutes", range);
            taskParam.put("promWindow", range + "m");
            taskParam.put("includeRecentLogs", true);
            taskParam.put("maxRounds", 3);
            taskParam.put("subAgentMaxIterations", 3);
            taskParam.put("nodeTimeoutSeconds", 60);
            taskParam.put("maxEvidenceItems", 10);
            taskParam.put("notifyChannel", false);
            args.put("taskParam", taskParam);
            args.put("description", "由平台主助手创建：" + taskParam.get("prompt"));
        }
        String tool = switch (action) {
            case "create" -> "inspection_task_create";
            case "update" -> id == null ? "inspection_task_list" : "inspection_task_update";
            case "enable", "disable" -> id == null ? "inspection_task_list" : "inspection_task_status";
            case "delete" -> id == null ? "inspection_task_list" : "inspection_task_delete";
            case "runNow" -> id == null ? "inspection_task_list" : "inspection_task_run_now";
            case "history" -> id == null ? "inspection_task_list" : "inspection_task_executions";
            default -> "inspection_task_list";
        };
        if ("inspection_task_list".equals(tool) && !"list".equals(action)) args.put("missingIdForAction", action);
        return new Command("inspection.task", tool, action, args);
    }

    private Command alertCommand(OpsAgentChatRequest request) {
        String query = text(request.getQuery());
        String action = action(query, false);
        Long id = extractId(query);
        Map<String, Object> args = baseArguments(request, action, id);
        args.put("agentDefinitionId", text(request.getAgentDefinitionId()));
        if ("create".equals(action) || "update".equals(action)) {
            String name = extractName(query);
            if (!StringUtils.hasText(name) && "create".equals(action)) {
                name = "项目告警-" + DateTimeFormatter.ofPattern("MMddHHmm").format(LocalDateTime.now());
            }
            if (StringUtils.hasText(name)) args.put("ruleName", name);
            int range = extractMinutes(query, 15);
            args.put("sourceType", "ALERTMANAGER");
            args.put("alertNameRegex", ".*");
            args.put("severityRegex", severityRegex(query));
            args.put("serviceRegex", ".*");
            args.put("matchLabelsJson", "{}");
            args.put("rangeMinutes", range);
            args.put("promWindow", range + "m");
            args.put("includeRecentLogs", true);
            args.put("notifyChannel", false);
            args.put("subAgentMaxIterations", 3);
            args.put("nodeTimeoutSeconds", 60);
            args.put("maxEvidenceItems", 10);
            args.put("dedupWindowSeconds", 900);
            args.put("questionTemplate", "收到告警后结合告警、指标、日志和变更记录分析影响、根因及处置建议。");
        }
        String tool = switch (action) {
            case "create" -> "alert_trigger_create";
            case "update" -> id == null ? "alert_trigger_list" : "alert_trigger_update";
            case "enable", "disable" -> id == null ? "alert_trigger_list" : "alert_trigger_status";
            case "delete" -> id == null ? "alert_trigger_list" : "alert_trigger_delete";
            case "history" -> "alert_trigger_events";
            default -> "alert_trigger_list";
        };
        if ("alert_trigger_list".equals(tool) && !"list".equals(action)) args.put("missingIdForAction", action);
        return new Command("alert.trigger", tool, action, args);
    }

    private Map<String, Object> baseArguments(OpsAgentChatRequest request, String action, Long id) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("action", action);
        args.put("projectId", request.getProjectId());
        args.put("agentId", text(request.getAgentDefinitionId()));
        if (id != null) args.put("id", id);
        if ("enable".equals(action) || "disable".equals(action)) args.put("status", "enable".equals(action) ? 1 : 0);
        return args;
    }

    private String humanOutput(Command command, Map<String, Object> envelope, boolean allowed) {
        if (!allowed) {
            return "该操作被平台策略阻止：" + text(envelope.get("reasonCode")) + "。系统没有绕过限制。";
        }
        if (command.arguments().containsKey("missingIdForAction")) {
            return "需要先选择要操作的记录。我已返回当前项目可选项，不需要手工填写内部校验值。";
        }
        String subject = "alert.trigger".equals(command.toolsetId()) ? "告警触发规则" : "巡检任务";
        return switch (command.action()) {
            case "create" -> subject + "已创建。";
            case "update" -> subject + "已更新。";
            case "enable" -> subject + "已启用。";
            case "disable" -> subject + "已停用。";
            case "delete" -> subject + "已删除。";
            case "runNow" -> "巡检任务已提交执行。";
            case "history" -> subject + "的执行或触发记录已返回。\n" + text(envelope.get("preview"));
            default -> "当前项目的" + subject + "已返回。\n" + text(envelope.get("preview"));
        };
    }

    private String action(String query, boolean inspection) {
        String compact = query.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (compact.contains("记录") || compact.contains("历史") || compact.contains("事件")) return "history";
        if (compact.contains("修改") || compact.contains("调整") || compact.contains("更新") || compact.contains("改成") || compact.contains("改为")) return "update";
        if (compact.contains("新增") || compact.contains("创建") || compact.contains("添加") || compact.contains("设定") || compact.contains("设置")) return "create";
        if (inspection && (compact.contains("立即执行") || compact.contains("运行一次") || compact.contains("执行一次"))) return "runNow";
        if (compact.contains("停用") || compact.contains("禁用") || compact.contains("关闭")) return "disable";
        if (compact.contains("启用") || compact.contains("恢复") || compact.contains("打开")) return "enable";
        if (compact.contains("删除") || compact.contains("移除")) return "delete";
        return "list";
    }

    private boolean isAlertRule(String query) {
        String value = text(query).toLowerCase(Locale.ROOT);
        return value.contains("告警规则") || value.contains("告警触发") || value.contains("alertmanager");
    }

    private Long extractId(String query) {
        Matcher matcher = Pattern.compile("(?i)(?:id|任务|规则|编号)\\s*[=:：#]?\\s*(\\d+)").matcher(text(query));
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private String extractName(String query) {
        Matcher matcher = Pattern.compile("(?:任务名|规则名|名称|名字)(?:叫|为|是|[:：=])\\s*([^，,。；;\\n]{1,80})").matcher(text(query));
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractCron(String query) {
        int minutes = extractMinutes(query, -1);
        if (minutes > 0) return "0 */" + minutes + " * * * ?";
        Matcher matcher = Pattern.compile("每\\s*(\\d{1,2})?\\s*小时").matcher(text(query));
        if (!matcher.find()) return "";
        int hours = StringUtils.hasText(matcher.group(1)) ? Integer.parseInt(matcher.group(1)) : 1;
        return "0 0 */" + Math.max(1, hours) + " * * ?";
    }

    private int extractMinutes(String query, int fallback) {
        Matcher matcher = Pattern.compile("每\\s*(\\d{1,3})\\s*分钟").matcher(text(query));
        return matcher.find() ? Math.max(1, Integer.parseInt(matcher.group(1))) : fallback;
    }

    private String inspectionObjective(String query) {
        Matcher matcher = Pattern.compile("(?:检查|关注|监控)(.+)").matcher(text(query));
        return matcher.find() ? "检查" + matcher.group(1).trim() : "巡检当前项目接口错误率、实例在线状态、日志错误和慢 SQL 风险。";
    }

    private String severityRegex(String query) {
        String value = text(query).toLowerCase(Locale.ROOT);
        if (value.contains("critical") || value.contains("p0") || value.contains("严重")) return "(?i)critical|p0|严重|紧急";
        if (value.contains("info") || value.contains("p2") || value.contains("提示")) return "(?i)info|p2|提示";
        return "(?i)warning|p1|告警";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Command(String toolsetId, String toolName, String action, Map<String, Object> arguments) {
    }
}
