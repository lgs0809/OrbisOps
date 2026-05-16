package cn.lgs.orbisops.trigger.ops.runtime;

/** Executes the selected runtime plan against the prepared Work Session context. */
final class OpsWorkSessionEngineExecutor {

    private final OpsWorkSessionRequestControl requestControl;
    private final OpsRuntimeEngineDispatcher engineDispatcher;

    OpsWorkSessionEngineExecutor(OpsWorkSessionRuntimeAssembly assembly) {
        this.requestControl = assembly.requestControl();
        this.engineDispatcher = assembly.engineDispatcher();
    }

    void execute(OpsWorkSessionRuntimeContext context) {
        requirePrepared(context);
        requestControl.assertNotCanceled(context.request());
        context.executed(engineDispatcher.executePlan(
                context.definition(),
                context.request(),
                context.plan(),
                context.events(),
                context.eventSink()));
    }

    private void requirePrepared(OpsWorkSessionRuntimeContext context) {
        if (context == null
                || context.definition() == null
                || context.plan() == null) {
            throw new IllegalStateException("WORK_SESSION_NOT_PREPARED");
        }
    }
}
