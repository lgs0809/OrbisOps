package cn.lgs.orbisops.application.worksession;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ExecuteWorkSessionUseCase<I, O, E> {

    private final Execution<I, O, E> execution;
    private final Supplier<Map<String, Object>> capabilities;

    public <C> ExecuteWorkSessionUseCase(WorkSessionLifecyclePort<I, C, O, E> port) {
        WorkSessionExecutionProcessManager<I, C, O, E> processManager =
                new WorkSessionExecutionProcessManager<>(port);
        this.execution = processManager::execute;
        this.capabilities = port::capabilities;
    }

    public O execute(I request) {
        return execution.execute(request, null);
    }

    public O execute(I request, Consumer<E> eventSink) {
        return execution.execute(request, eventSink);
    }

    public Map<String, Object> capabilities() {
        return capabilities.get();
    }

    @FunctionalInterface
    private interface Execution<I, O, E> {
        O execute(I request, Consumer<E> eventSink);
    }
}
