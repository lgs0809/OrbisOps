package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class InspectionToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "inspection";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "inspection.task",
                        "巡检任务",
                        "创建、调整、启停和手动触发项目巡检任务",
                        "INSPECTION_TASK",
                        false,
                        List.of(
                                tools.read("inspection_task_list", "列出巡检任务", "INSPECTION_TASK"),
                                tools.inspection("inspection_task_create", "创建巡检任务"),
                                tools.inspection("inspection_task_update", "更新巡检任务"),
                                tools.inspection("inspection_task_status", "启停巡检任务"),
                                tools.inspection("inspection_task_delete", "删除巡检任务"),
                                tools.inspection("inspection_task_run_now", "立即执行巡检任务"),
                                tools.read("inspection_task_executions", "查询巡检执行记录", "INSPECTION_TASK"))),
                definitions.toolset(
                        "alert.trigger",
                        "告警触发规则",
                        "创建、调整、启停和删除 Alertmanager 告警触发规则",
                        "ALERT_TRIGGER",
                        false,
                        List.of(
                                tools.read("alert_trigger_list", "列出告警触发规则", "ALERT_TRIGGER"),
                                tools.alertTrigger("alert_trigger_create", "创建告警触发规则"),
                                tools.alertTrigger("alert_trigger_update", "更新告警触发规则"),
                                tools.alertTrigger("alert_trigger_status", "启停告警触发规则"),
                                tools.alertTrigger("alert_trigger_delete", "删除告警触发规则"),
                                tools.read("alert_trigger_events", "查询告警触发事件", "ALERT_TRIGGER"))));
    }
}
