package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractOpsEngineAdapter implements OpsEngineAdapter {

    private final String key;

    protected AbstractOpsEngineAdapter(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public final String execute(OpsAgentDefinition definition,
                                OpsAgentChatRequest request,
                                OpsRuntimeExecutionPlan plan,
                                OpsAgentRuntimeSupport runtime,
                                List<OpsRuntimeEvent> events,
                                Consumer<OpsRuntimeEvent> eventSink) {
        try {
            return doExecute(definition, request, plan, runtime, events, eventSink);
        } catch (RuntimeException e) {
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("ADAPTER_FAILED")
                    .status("FAILED")
                    .summary(key + " adapter 执行失败：" + e.getMessage())
                    .build());
            throw e;
        }
    }

    protected abstract String doExecute(OpsAgentDefinition definition,
                                        OpsAgentChatRequest request,
                                        OpsRuntimeExecutionPlan plan,
                                        OpsAgentRuntimeSupport runtime,
                                        List<OpsRuntimeEvent> events,
                                        Consumer<OpsRuntimeEvent> eventSink);

    protected void record(List<OpsRuntimeEvent> events, Consumer<OpsRuntimeEvent> eventSink, OpsRuntimeEvent event) {
        if (events != null) {
            events.add(event);
        }
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

}
