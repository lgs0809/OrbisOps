package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Map;
import java.util.function.Consumer;

/** Public microkernel facade used by chat, workflow, alert, inspection and landing callers. */
public final class UnifiedAgentRuntime {

    private final OpsWorkSessionLifecycleCoordinator lifecycle;

    public UnifiedAgentRuntime(OpsWorkSessionLifecycleCoordinator lifecycle) {
        if (lifecycle == null) throw new IllegalArgumentException("WORK_SESSION_LIFECYCLE_REQUIRED");
        this.lifecycle = lifecycle;
    }

    public OpsAgentChatResponse execute(OpsAgentChatRequest request) {
        return lifecycle.execute(request);
    }

    public OpsAgentChatResponse execute(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink) {
        return lifecycle.execute(request, eventSink);
    }

    public OpsWorkSessionRuntimeContext prepareWorkSession(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink) {
        return lifecycle.prepareWorkSession(request, eventSink);
    }

    public void executeWorkSessionEngine(OpsWorkSessionRuntimeContext context) {
        lifecycle.executeWorkSessionEngine(context);
    }

    public OpsAgentChatResponse succeedWorkSession(OpsWorkSessionRuntimeContext context) {
        return lifecycle.succeedWorkSession(context);
    }

    public boolean isWorkSessionSuspension(RuntimeException error) {
        return lifecycle.isWorkSessionSuspension(error);
    }

    public OpsAgentChatResponse suspendWorkSession(OpsWorkSessionRuntimeContext context, RuntimeException error) {
        return lifecycle.suspendWorkSession(context, error);
    }

    public boolean isWorkSessionCancellation(RuntimeException error) {
        return lifecycle.isWorkSessionCancellation(error);
    }

    public OpsAgentChatResponse cancelWorkSession(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error) {
        return lifecycle.cancelWorkSession(context, error);
    }

    public void failWorkSession(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error) {
        lifecycle.failWorkSession(context, error);
    }

    public Map<String, Object> capabilities() {
        return lifecycle.runtimeCapabilities();
    }
}
