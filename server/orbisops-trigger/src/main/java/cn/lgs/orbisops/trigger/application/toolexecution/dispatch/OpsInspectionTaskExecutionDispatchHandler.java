package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.api.dto.TaskScheduleRequestDTO;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.config.TaskScheduleApplicationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsInspectionTaskExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final ObjectProvider<TaskScheduleApplicationService> schedules;

    public OpsInspectionTaskExecutionDispatchHandler(ObjectProvider<TaskScheduleApplicationService> schedules) {
        this.schedules = schedules;
    }

    @Override
    public String handlerId() {
        return "inspection-task";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "inspection.task".equals(target.toolsetId())
                || "INSPECTION_TASK".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        TaskScheduleApplicationService service = schedules == null ? null : schedules.getIfAvailable();
        if (service == null) throw new IllegalStateException("Inspection task adapter 未初始化");
        Map<String, Object> arguments = request.arguments();
        String projectId = text(first(request.requestContext().get("projectId"), arguments.get("projectId")), "");
        return switch (target.toolName()) {
            case "inspection_task_list" -> Map.of("items", service.listSchedules(projectId));
            case "inspection_task_create" -> status(service.create(scheduleRequest(arguments, projectId, false)));
            case "inspection_task_update" -> status(service.update(scheduleRequest(arguments, projectId, true)));
            case "inspection_task_status" -> status(service.updateStatus(
                    longValue(arguments.get("id"), "巡检任务启停必须提供 id"),
                    intValue(arguments.get("status"), 0), projectId));
            case "inspection_task_delete" -> status(service.delete(
                    longValue(arguments.get("id"), "删除巡检任务必须提供 id"), projectId));
            case "inspection_task_run_now" -> Map.of(
                    "executionId", service.runNow(
                            longValue(arguments.get("id"), "立即执行巡检任务必须提供 id"), projectId),
                    "status", "SUBMITTED");
            case "inspection_task_executions" -> Map.of("items", service.listExecutions(
                    projectId,
                    longValue(arguments.get("id"), "查询巡检执行记录必须提供 id"),
                    intValue(arguments.get("limit"), 20)));
            default -> throw new IllegalArgumentException("未知巡检任务工具：" + target.toolName());
        };
    }

    private Map<String, Object> status(boolean ok) {
        return Map.of("status", ok ? "SUCCEEDED" : "FAILED");
    }

    private TaskScheduleRequestDTO scheduleRequest(
            Map<String, Object> arguments,
            String projectId,
            boolean requireId) {
        Long id = null;
        if (arguments.get("id") != null) {
            id = longValue(arguments.get("id"), "巡检任务 id 不合法");
        } else if (requireId) {
            throw new IllegalArgumentException("更新巡检任务必须提供 id");
        }
        Map<String, Object> taskParam = objectMap(arguments.get("taskParam"));
        Object promptValue = taskParam.isEmpty()
                ? first(arguments.get("prompt"), arguments.get("taskParam"))
                : first(taskParam.get("prompt"), arguments.get("prompt"));
        String prompt = text(promptValue, "巡检当前项目接口错误率、实例在线状态、日志错误和慢 SQL 风险。");
        int rangeMinutes = intValue(first(arguments.get("rangeMinutes"), taskParam.get("rangeMinutes")), 15);
        String promWindow = text(first(arguments.get("promWindow"), taskParam.get("promWindow")), "5m");
        boolean includeRecentLogs = bool(first(arguments.get("includeRecentLogs"), taskParam.get("includeRecentLogs")), true);
        int maxRounds = intValue(first(arguments.get("maxRounds"), taskParam.get("maxRounds")), 3);
        int subAgentMaxIterations = intValue(first(arguments.get("subAgentMaxIterations"), taskParam.get("subAgentMaxIterations")), 3);
        int nodeTimeoutSeconds = intValue(first(arguments.get("nodeTimeoutSeconds"), taskParam.get("nodeTimeoutSeconds")), 120);
        int maxEvidenceItems = intValue(first(arguments.get("maxEvidenceItems"), taskParam.get("maxEvidenceItems")), 12);
        boolean notifyChannel = bool(first(arguments.get("notifyChannel"), taskParam.get("notifyChannel")), false);
        return TaskScheduleRequestDTO.builder()
                .id(id)
                .projectId(projectId)
                .agentId(text(arguments.get("agentId"), "generic-ops-react-agent"))
                .taskName(text(arguments.get("taskName"), "项目运行状态巡检"))
                .description(text(arguments.get("description"), ""))
                .cronExpression(text(arguments.get("cronExpression"), "0 */30 * * * ?"))
                .taskParam(prompt)
                .status(intValue(arguments.get("status"), 1))
                .rangeMinutes(rangeMinutes)
                .promWindow(promWindow)
                .includeRecentLogs(includeRecentLogs)
                .maxRounds(maxRounds)
                .subAgentMaxIterations(subAgentMaxIterations)
                .nodeTimeoutSeconds(nodeTimeoutSeconds)
                .maxEvidenceItems(maxEvidenceItems)
                .notifyChannel(notifyChannel)
                .notificationChannelId(text(first(arguments.get("notificationChannelId"), taskParam.get("notificationChannelId")), ""))
                .notificationTarget(text(first(arguments.get("notificationTarget"), taskParam.get("notificationTarget")), ""))
                .build();
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
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
