package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Mutable execution state owned by one Application-managed Work Session lifecycle. */
public final class OpsWorkSessionRuntimeContext {

    private final OpsAgentChatRequest request;
    private final Consumer<OpsRuntimeEvent> eventSink;
    private final List<OpsRuntimeEvent> events;
    private final long startedNanos;
    private OpsAgentDefinition definition;
    private OpsRuntimeExecutionPlan plan;
    private String mode;
    private String engine;
    private String output;
    private OpsAgentChatResponse terminalResponse;

    public OpsWorkSessionRuntimeContext(OpsAgentChatRequest request,
                                        Consumer<OpsRuntimeEvent> eventSink,
                                        long startedNanos) {
        if (request == null) throw new IllegalArgumentException("WORK_SESSION_REQUEST_REQUIRED");
        this.request = request;
        this.eventSink = eventSink;
        this.events = new ArrayList<>();
        this.startedNanos = startedNanos;
    }

    public OpsAgentChatRequest request() {
        return request;
    }

    public Consumer<OpsRuntimeEvent> eventSink() {
        return eventSink;
    }

    public List<OpsRuntimeEvent> events() {
        return events;
    }

    public long startedNanos() {
        return startedNanos;
    }

    public OpsAgentDefinition definition() {
        return definition;
    }

    public OpsRuntimeExecutionPlan plan() {
        return plan;
    }

    public String mode() {
        return mode;
    }

    public String engine() {
        return engine;
    }

    public String output() {
        return output;
    }

    public OpsAgentChatResponse terminalResponse() {
        return terminalResponse;
    }

    public boolean terminal() {
        return terminalResponse != null;
    }

    public void planned(OpsAgentDefinition definition, OpsRuntimeExecutionPlan plan) {
        if (definition == null) throw new IllegalArgumentException("WORK_SESSION_DEFINITION_REQUIRED");
        if (plan == null) throw new IllegalArgumentException("WORK_SESSION_PLAN_REQUIRED");
        this.definition = definition;
        this.plan = plan;
        this.mode = plan.getMode();
        this.engine = plan.getEngine();
    }

    public void executed(String output) {
        if (output == null) throw new IllegalStateException("WORK_SESSION_OUTPUT_REQUIRED");
        this.output = output;
    }

    public void completeTerminal(OpsAgentChatResponse response) {
        if (response == null) throw new IllegalStateException("WORK_SESSION_TERMINAL_RESPONSE_REQUIRED");
        this.terminalResponse = response;
    }
}
