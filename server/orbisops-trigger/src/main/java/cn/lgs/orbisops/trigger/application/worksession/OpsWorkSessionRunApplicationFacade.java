package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.run.WorkSessionResumeAccepted;
import cn.lgs.orbisops.application.worksession.run.WorkSessionResumeSchedulerPort;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunControlUseCase;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger-facing facade for durable Work Session Run query, resume and cancellation controls. */
public final class OpsWorkSessionRunApplicationFacade {

    private final WorkSessionRunControlUseCase<Map<String, Object>, OpsAgentChatRequest> control;
    private final GraphEventApplicationService graphEvents;

    public OpsWorkSessionRunApplicationFacade(
            OpsWorkSessionRunAdapter runAdapter,
            OpsRunCancellationRegistry cancellationRegistry,
            WorkSessionResumeSchedulerPort<OpsAgentChatRequest> resumeScheduler,
            GraphEventApplicationService graphEvents) {
        if (graphEvents == null) {
            throw new IllegalArgumentException("WORK_SESSION_GRAPH_EVENTS_REQUIRED");
        }
        this.control = new WorkSessionRunControlUseCase<>(
                new OpsWorkSessionRunControlAdapter(runAdapter),
                new OpsWorkSessionLocalCancellationAdapter(cancellationRegistry),
                resumeScheduler);
        this.graphEvents = graphEvents;
    }

    public List<GraphEvent> events(
            String runId,
            long afterSequence,
            int limit,
            String projectId) {
        control.get(runId, projectId);
        return graphEvents.list(runId, afterSequence, limit);
    }

    public List<GraphEvent> eventsForActor(
            String runId,
            long afterSequence,
            int limit,
            String projectId,
            String actor) {
        control.assertReadable(runId, projectId, actor);
        return graphEvents.list(runId, afterSequence, limit);
    }

    public Map<String, Object> run(String runId, String projectId) {
        return control.get(runId, projectId);
    }

    public Map<String, Object> runForActor(
            String runId,
            String projectId,
            String actor) {
        return control.getForActor(runId, projectId, actor);
    }

    public Map<String, Object> resume(
            String runId,
            String projectId,
            String actor) {
        return resumeView(control.resume(runId, projectId, actor));
    }

    public void assertCanResumeApproval(
            String runId,
            String projectId,
            String actor) {
        control.assertCanResumeApproval(runId, projectId, actor);
    }

    public Map<String, Object> resumeApproval(
            String runId,
            String projectId,
            String actor) {
        return resumeView(control.resumeApproval(runId, projectId, actor));
    }

    private Map<String, Object> resumeView(WorkSessionResumeAccepted accepted) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", accepted.runId());
        result.put("projectId", accepted.projectId());
        result.put("status", accepted.status());
        result.put("message", accepted.message());
        return result;
    }

    public boolean cancelLocal(String runId) {
        return control.cancelLocal(runId);
    }

    public boolean requestCancel(
            String runId,
            String projectId,
            String actor,
            String reason) {
        return control.requestCancel(runId, projectId, actor, reason);
    }

    public boolean requestCancelForActor(
            String runId,
            String actor,
            String reason) {
        return control.requestCancelForActor(runId, actor, reason);
    }
}
