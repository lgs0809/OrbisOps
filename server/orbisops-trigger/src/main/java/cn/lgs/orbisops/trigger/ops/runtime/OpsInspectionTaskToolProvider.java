package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class OpsInspectionTaskToolProvider {

    private final OpsToolExecutionService toolExecutionService;

    public OpsInspectionTaskToolProvider() {
        this((OpsToolExecutionService) null);
    }

    @Autowired
    public OpsInspectionTaskToolProvider(
            ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider) {
        this(toolExecutionServiceProvider == null
                ? null
                : toolExecutionServiceProvider.getIfAvailable());
    }

    OpsInspectionTaskToolProvider(OpsToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    public ToolCallback build(String projectId, String actor, String runId, String defaultAgentId) {
        Function<InspectionTaskInput, String> function = input -> JSON.toJSONString(execute(projectId, actor, runId, defaultAgentId,
                input == null ? Map.of() : input.toMap()));
        return FunctionToolCallback.builder("ManageInspectionTask", function)
                .description("""
                        管理当前项目的巡检任务。支持 action=list/create/update/enable/disable/delete/runNow/listExecutions。
                        该工具只修改巡检配置或触发一次受控巡检执行，不会直接修改生产目标资源。
                        projectId 由平台运行时注入，模型不能跨项目操作巡检任务；所有写操作会进入统一 ToolsetRouter、ToolResultStore 和审计。
                        agentId 未显式指定时使用当前项目默认 ReAct Agent；显式指定 agentId 时按用户选择的拖拉拽固定 Workflow 执行。
                        create/update 参数：taskName、cronExpression、agentId、taskParam、description、rangeMinutes、promWindow、
                        includeRecentLogs、maxRounds、subAgentMaxIterations、nodeTimeoutSeconds、maxEvidenceItems、notifyChannel、notificationChannelId、notificationTarget。
                        update/enable/disable/delete/runNow/listExecutions 需要 id；listExecutions 可传 limit。
                        """)
                .inputType(InspectionTaskInput.class)
                .build();
    }

    private Map<String, Object> execute(String projectId, String actor, String runId, String defaultAgentId, Map<String, Object> input) {
        if (toolExecutionService == null) {
            throw new SecurityException("OpsToolExecutionService 未初始化，巡检任务工具不能绕过统一 ToolsetRouter。");
        }
        String toolName = toolName(input.get("action"));
        Map<String, Object> arguments = new LinkedHashMap<>(input);
        arguments.put("projectId", projectId);
        if (!StringUtils.hasText(value(arguments.get("agentId"))) && StringUtils.hasText(defaultAgentId)) {
            arguments.put("agentId", defaultAgentId);
        }
        if ("inspection_task_status".equals(toolName)) {
            arguments.put("status", statusFor(input.get("action"), input.get("status")));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("userId", actor);
        request.put("runId", value(runId));
        request.put("executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name());
        request.put("toolsetId", "inspection.task");
        request.put("toolName", toolName);
        request.put("arguments", arguments);
        return toolExecutionService.execute(request, actor);
    }

    private String toolName(Object actionValue) {
        String action = value(actionValue).toLowerCase().replace("-", "_");
        return switch (action) {
            case "", "list", "query" -> "inspection_task_list";
            case "create", "add" -> "inspection_task_create";
            case "update", "edit", "modify" -> "inspection_task_update";
            case "enable", "disable", "status" -> "inspection_task_status";
            case "delete", "remove" -> "inspection_task_delete";
            case "runnow", "run_now", "run" -> "inspection_task_run_now";
            case "listexecutions", "list_executions", "executions", "history" -> "inspection_task_executions";
            default -> throw new IllegalArgumentException("不支持的巡检任务 action：" + actionValue);
        };
    }

    private int statusFor(Object actionValue, Object statusValue) {
        String action = value(actionValue).toLowerCase();
        if ("enable".equals(action)) {
            return 1;
        }
        if ("disable".equals(action)) {
            return 0;
        }
        if (statusValue instanceof Number number) {
            return number.intValue() == 1 ? 1 : 0;
        }
        String status = value(statusValue);
        if (!StringUtils.hasText(status)) {
            return 0;
        }
        return "1".equals(status) || "true".equalsIgnoreCase(status) || "enabled".equalsIgnoreCase(status) ? 1 : 0;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static class InspectionTaskInput {
        private String action;
        private Long id;
        private String taskName;
        private String cronExpression;
        private String agentId;
        private Map<String, Object> taskParam;
        private String description;
        private Integer rangeMinutes;
        private String promWindow;
        private Boolean includeRecentLogs;
        private Integer maxRounds;
        private Integer subAgentMaxIterations;
        private Integer nodeTimeoutSeconds;
        private Integer maxEvidenceItems;
        private Boolean notifyChannel;
        private String notificationChannelId;
        private String notificationTarget;
        private Integer status;
        private Integer limit;

        Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            put(data, "action", action);
            put(data, "id", id);
            put(data, "taskName", taskName);
            put(data, "cronExpression", cronExpression);
            put(data, "agentId", agentId);
            put(data, "taskParam", taskParam);
            put(data, "description", description);
            put(data, "rangeMinutes", rangeMinutes);
            put(data, "promWindow", promWindow);
            put(data, "includeRecentLogs", includeRecentLogs);
            put(data, "maxRounds", maxRounds);
            put(data, "subAgentMaxIterations", subAgentMaxIterations);
            put(data, "nodeTimeoutSeconds", nodeTimeoutSeconds);
            put(data, "maxEvidenceItems", maxEvidenceItems);
            put(data, "notifyChannel", notifyChannel);
            put(data, "notificationChannelId", notificationChannelId);
            put(data, "notificationTarget", notificationTarget);
            put(data, "status", status);
            put(data, "limit", limit);
            return data;
        }

        private static void put(Map<String, Object> data, String key, Object value) {
            if (value != null) {
                data.put(key, value);
            }
        }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public Map<String, Object> getTaskParam() { return taskParam; }
        public void setTaskParam(Map<String, Object> taskParam) { this.taskParam = taskParam; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getRangeMinutes() { return rangeMinutes; }
        public void setRangeMinutes(Integer rangeMinutes) { this.rangeMinutes = rangeMinutes; }
        public String getPromWindow() { return promWindow; }
        public void setPromWindow(String promWindow) { this.promWindow = promWindow; }
        public Boolean getIncludeRecentLogs() { return includeRecentLogs; }
        public void setIncludeRecentLogs(Boolean includeRecentLogs) { this.includeRecentLogs = includeRecentLogs; }
        public Integer getMaxRounds() { return maxRounds; }
        public void setMaxRounds(Integer maxRounds) { this.maxRounds = maxRounds; }
        public Integer getSubAgentMaxIterations() { return subAgentMaxIterations; }
        public void setSubAgentMaxIterations(Integer subAgentMaxIterations) { this.subAgentMaxIterations = subAgentMaxIterations; }
        public Integer getNodeTimeoutSeconds() { return nodeTimeoutSeconds; }
        public void setNodeTimeoutSeconds(Integer nodeTimeoutSeconds) { this.nodeTimeoutSeconds = nodeTimeoutSeconds; }
        public Integer getMaxEvidenceItems() { return maxEvidenceItems; }
        public void setMaxEvidenceItems(Integer maxEvidenceItems) { this.maxEvidenceItems = maxEvidenceItems; }
        public Boolean getNotifyChannel() { return notifyChannel; }
        public void setNotifyChannel(Boolean notifyChannel) { this.notifyChannel = notifyChannel; }
        public String getNotificationChannelId() { return notificationChannelId; }
        public void setNotificationChannelId(String notificationChannelId) { this.notificationChannelId = notificationChannelId; }
        public String getNotificationTarget() { return notificationTarget; }
        public void setNotificationTarget(String notificationTarget) { this.notificationTarget = notificationTarget; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }
}
