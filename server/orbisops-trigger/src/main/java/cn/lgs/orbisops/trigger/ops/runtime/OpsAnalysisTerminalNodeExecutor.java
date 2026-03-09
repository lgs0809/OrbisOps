package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsAnalysisReportComposer;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

/** Executes report, notification and unsupported Analysis terminal nodes. */
final class OpsAnalysisTerminalNodeExecutor {

    private final OpsAnalysisReportComposer reportComposer;
    private final OpsChannelNotificationService notificationService;
    private final OpsAnalysisRuntimeStateManager stateManager;
    private final OpsGraphRuntimeStateManager graphStateManager;
    private final OpsAnalysisNodeLifecycle lifecycle;

    OpsAnalysisTerminalNodeExecutor(OpsAnalysisReportComposer reportComposer,
                                    OpsChannelNotificationService notificationService,
                                    OpsAnalysisRuntimeStateManager stateManager,
                                    OpsGraphRuntimeStateManager graphStateManager,
                                    OpsAnalysisNodeLifecycle lifecycle) {
        this.reportComposer = reportComposer;
        this.notificationService = notificationService;
        this.stateManager = stateManager;
        this.graphStateManager = graphStateManager;
        this.lifecycle = lifecycle;
    }

    void executeReport(OpsAnalysisNodeExecutionContext context) {
        stateManager.mergeRuntimeNotes(context.response(), context.runtimeNotes());
        context.response().setAgentExecutionSteps(new ArrayList<>(context.steps()));
        reportComposer.composeFinalReport(context.response());
        String packageDecision = context.hooks().evaluateChangePackage(
                context.definition(),
                context.node(),
                context.runtimeRequest(),
                context.response(),
                context.events(),
                context.eventSink());
        String summary = "最终运维分析报告已生成。";
        lifecycle.record(context, "SUCCEEDED", summary);
        context.response().setAgentExecutionSteps(new ArrayList<>(context.steps()));
        context.output().put(OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true);
        context.output().put("response", context.response());
        if (StringUtils.hasText(packageDecision)) {
            context.output().put("changePackageDecision", packageDecision);
        }
        context.output().put("output", summary);
    }

    void executeNotify(OpsAnalysisNodeExecutionContext context) {
        String summary;
        if (graphStateManager.stateBoolean(
                context.graphState(), OpsGraphRuntimeStateManager.NOTIFICATION_HANDLED_KEY)) {
            summary = "通知已在报告节点处理，本节点跳过重复推送。";
            lifecycle.record(context, "SKIPPED", summary);
        } else {
            OpsChannelNotificationService.NotifyResult notifyResult = notificationService.notifyIfNeeded(
                    context.request(), context.response());
            String status = notifyResult.attempted()
                    ? (notifyResult.success() ? "SUCCEEDED" : "FAILED")
                    : "SKIPPED";
            summary = notifyResult.message();
            lifecycle.record(context, status, summary);
        }
        context.output().put(OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true);
        context.output().put(OpsGraphRuntimeStateManager.NOTIFICATION_HANDLED_KEY, true);
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    void executeUnsupported(OpsAnalysisNodeExecutionContext context) {
        String input = context.response().getMarkdownReport() == null
                ? ""
                : context.response().getMarkdownReport();
        context.output().put("output", input);
        lifecycle.record(
                context,
                "SKIPPED",
                "通用 Runtime 运维分析暂不执行节点类型：" + context.nodeType());
    }
}
