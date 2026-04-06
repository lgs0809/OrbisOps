package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.run.WorkSessionRunControlPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;

import java.util.Map;

/** Trigger adapter exposing the existing typed durable Run application boundary. */
public final class OpsWorkSessionRunControlAdapter
        implements WorkSessionRunControlPort<Map<String, Object>, OpsAgentChatRequest> {

    private final OpsWorkSessionRunAdapter runAdapter;

    public OpsWorkSessionRunControlAdapter(OpsWorkSessionRunAdapter runAdapter) {
        if (runAdapter == null) {
            throw new IllegalArgumentException("WORK_SESSION_RUN_ADAPTER_REQUIRED");
        }
        this.runAdapter = runAdapter;
    }

    @Override
    public Map<String, Object> get(String runId, String projectId) {
        return runAdapter.get(runId, projectId);
    }

    @Override
    public void assertActorCanRead(String runId, String projectId, String actor) {
        runAdapter.assertActorCanRead(runId, projectId, actor);
    }

    @Override
    public OpsAgentChatRequest resumeRequest(String runId, String projectId, String actor) {
        return runAdapter.resumeRequest(runId, projectId, actor);
    }

    @Override
    public OpsAgentChatRequest approvalResumeRequest(String runId, String projectId, String actor) {
        return runAdapter.approvalResumeRequest(runId, projectId, actor);
    }

    @Override
    public boolean requestCancel(String runId, String projectId, String actor, String reason) {
        return runAdapter.requestCancel(runId, projectId, actor, reason);
    }

    @Override
    public boolean requestCancelForActor(String runId, String actor, String reason) {
        return runAdapter.requestCancelForActor(runId, actor, reason);
    }
}
