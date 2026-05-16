package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;

import java.util.Map;
import java.util.function.Consumer;

/** Stable facade for one controlled Work Session lifecycle. */
public class OpsWorkSessionLifecycleCoordinator {

    private final OpsWorkSessionPreparationCoordinator preparationCoordinator;
    private final OpsWorkSessionEngineExecutor engineExecutor;
    private final OpsWorkSessionTerminalCoordinator terminalCoordinator;
    private final OpsWorkSessionCapabilityPresenter capabilityPresenter;
    private final OpsWorkSessionLeaseHeartbeat leaseHeartbeat;

    OpsWorkSessionLifecycleCoordinator(OpsWorkSessionRuntimeAssembly runtimeAssembly) {
        this.preparationCoordinator =
                new OpsWorkSessionPreparationCoordinator(runtimeAssembly);
        this.engineExecutor = new OpsWorkSessionEngineExecutor(runtimeAssembly);
        this.terminalCoordinator =
                new OpsWorkSessionTerminalCoordinator(runtimeAssembly);
        this.capabilityPresenter =
                new OpsWorkSessionCapabilityPresenter(runtimeAssembly);
        this.leaseHeartbeat = new OpsWorkSessionLeaseHeartbeat(runtimeAssembly.workSessionRunService());
    }

    public OpsAgentChatResponse execute(OpsAgentChatRequest request) {
        return execute(request, null);
    }

    public OpsAgentChatResponse execute(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink) {
        OpsWorkSessionRuntimeContext context = prepareWorkSession(
                request,
                eventSink);
        if (context.terminal()) return context.terminalResponse();
        try {
            executeWorkSessionEngine(context);
            return succeedWorkSession(context);
        } catch (OpsWorkflowApprovalPendingException pending) {
            return terminalCoordinator.waitingApproval(context, pending);
        } catch (OpsRunCanceledException error) {
            return cancelWorkSession(context, error);
        } catch (RuntimeException error) {
            failWorkSession(context, error);
            throw error;
        }
    }

    public OpsWorkSessionRuntimeContext prepareWorkSession(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink) {
        return preparationCoordinator.prepare(request, eventSink);
    }

    public void executeWorkSessionEngine(
            OpsWorkSessionRuntimeContext context) {
        try (OpsWorkSessionLeaseHeartbeat.Scope heartbeat = leaseHeartbeat.start(context.request())) {
            try {
                engineExecutor.execute(context);
            } catch (OpsWorkflowApprovalPendingException pending) {
                heartbeat.suspendForApproval();
                throw pending;
            } catch (RuntimeException error) {
                heartbeat.assertHealthy();
                throw error;
            }
            heartbeat.assertHealthy();
        }
    }

    public OpsAgentChatResponse succeedWorkSession(
            OpsWorkSessionRuntimeContext context) {
        return terminalCoordinator.success(context);
    }

    public boolean isWorkSessionSuspension(RuntimeException error) {
        return error instanceof OpsWorkflowApprovalPendingException;
    }

    public OpsAgentChatResponse suspendWorkSession(OpsWorkSessionRuntimeContext context, RuntimeException error) {
        if (!(error instanceof OpsWorkflowApprovalPendingException pending)) throw error;
        return terminalCoordinator.waitingApproval(context, pending);
    }

    public boolean isWorkSessionCancellation(RuntimeException error) {
        return error instanceof OpsRunCanceledException;
    }

    public OpsAgentChatResponse cancelWorkSession(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error) {
        return terminalCoordinator.canceled(context, error);
    }

    public void failWorkSession(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error) {
        terminalCoordinator.failed(context, error);
    }

    public Map<String, Object> runtimeCapabilities() {
        return capabilityPresenter.capabilities();
    }
}
