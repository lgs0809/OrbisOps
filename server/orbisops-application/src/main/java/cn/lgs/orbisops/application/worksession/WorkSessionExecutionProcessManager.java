package cn.lgs.orbisops.application.worksession;

import java.util.function.Consumer;

/** Application-owned Template Method for one Work Session execution. */
public final class WorkSessionExecutionProcessManager<I, C, O, E> {

    private final WorkSessionLifecyclePort<I, C, O, E> port;

    public WorkSessionExecutionProcessManager(WorkSessionLifecyclePort<I, C, O, E> port) {
        if (port == null) throw new IllegalArgumentException("WORK_SESSION_LIFECYCLE_PORT_REQUIRED");
        this.port = port;
    }

    public O execute(I request, Consumer<E> eventSink) {
        WorkSessionPreparation<C, O> preparation = port.prepare(request, eventSink);
        if (preparation == null) {
            throw new IllegalStateException("WORK_SESSION_PREPARATION_RESULT_REQUIRED");
        }
        if (preparation.terminal()) {
            return preparation.terminalResponse();
        }
        C context = preparation.context();
        if (context == null) {
            throw new IllegalStateException("WORK_SESSION_CONTEXT_REQUIRED");
        }
        try {
            port.execute(context, eventSink);
            return port.succeed(context, eventSink);
        } catch (RuntimeException error) {
            if (port.isSuspension(error)) {
                return port.suspend(context, error, eventSink);
            }
            if (port.isCancellation(error)) {
                return port.cancel(context, error, eventSink);
            }
            try {
                port.fail(context, error, eventSink);
            } catch (RuntimeException finalizationError) {
                error.addSuppressed(finalizationError);
            }
            throw error;
        }
    }
}
