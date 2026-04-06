package cn.lgs.orbisops.application.worksession;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Stage port used by the Application process manager. Implementations may call
 * engines and external systems, but cannot change the execution sequence.
 */
public interface WorkSessionLifecyclePort<I, C, O, E> {

    WorkSessionPreparation<C, O> prepare(I request, Consumer<E> eventSink);

    void execute(C context, Consumer<E> eventSink);

    O succeed(C context, Consumer<E> eventSink);

    default boolean isSuspension(RuntimeException error) { return false; }

    default O suspend(C context, RuntimeException error, Consumer<E> eventSink) { throw error; }

    boolean isCancellation(RuntimeException error);

    O cancel(C context, RuntimeException error, Consumer<E> eventSink);

    void fail(C context, RuntimeException error, Consumer<E> eventSink);

    Map<String, Object> capabilities();
}
