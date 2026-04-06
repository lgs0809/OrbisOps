package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.WorkSessionLifecyclePort;
import cn.lgs.orbisops.application.worksession.WorkSessionPreparation;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRuntimeContext;
import cn.lgs.orbisops.trigger.ops.runtime.UnifiedAgentRuntime;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Trigger anti-corruption adapter for the Application Work Session lifecycle port.
 * The Application process manager owns ordering; the lifecycle coordinator owns
 * runtime preparation, engine execution and terminal adaptation.
 */
@Component
public class OpsWorkSessionExecutionAdapter implements WorkSessionLifecyclePort<
        OpsAgentChatRequest, OpsWorkSessionRuntimeContext, OpsAgentChatResponse, OpsRuntimeEvent> {

    private final UnifiedAgentRuntime runtime;

    public OpsWorkSessionExecutionAdapter(UnifiedAgentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public WorkSessionPreparation<OpsWorkSessionRuntimeContext, OpsAgentChatResponse> prepare(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink) {
        if (request == null) throw new IllegalArgumentException("WORK_SESSION_REQUEST_REQUIRED");
        OpsWorkSessionRuntimeContext context =
                runtime.prepareWorkSession(request, eventSink);
        return context.terminal()
                ? WorkSessionPreparation.terminal(context.terminalResponse())
                : WorkSessionPreparation.ready(context);
    }

    @Override
    public void execute(
            OpsWorkSessionRuntimeContext context,
            Consumer<OpsRuntimeEvent> eventSink) {
        runtime.executeWorkSessionEngine(context);
    }

    @Override
    public OpsAgentChatResponse succeed(
            OpsWorkSessionRuntimeContext context,
            Consumer<OpsRuntimeEvent> eventSink) {
        return runtime.succeedWorkSession(context);
    }

    @Override
    public boolean isSuspension(RuntimeException error) {
        return runtime.isWorkSessionSuspension(error);
    }

    @Override
    public OpsAgentChatResponse suspend(OpsWorkSessionRuntimeContext context, RuntimeException error,
                                       Consumer<OpsRuntimeEvent> eventSink) {
        return runtime.suspendWorkSession(context, error);
    }

    @Override
    public boolean isCancellation(RuntimeException error) {
        return runtime.isWorkSessionCancellation(error);
    }

    @Override
    public OpsAgentChatResponse cancel(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error,
            Consumer<OpsRuntimeEvent> eventSink) {
        return runtime.cancelWorkSession(context, error);
    }

    @Override
    public void fail(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error,
            Consumer<OpsRuntimeEvent> eventSink) {
        runtime.failWorkSession(context, error);
    }

    @Override
    public Map<String, Object> capabilities() {
        return runtime.capabilities();
    }
}
