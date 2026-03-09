package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.function.Consumer;

public interface OpsAgentRuntimeSupport {

    String runChatEngine(OpsAgentDefinition definition,
                         OpsAgentChatRequest request,
                         OpsRuntimeExecutionPlan plan,
                         List<OpsRuntimeEvent> events,
                         Consumer<OpsRuntimeEvent> eventSink);

    String runGraphEngine(OpsAgentDefinition definition,
                          OpsAgentChatRequest request,
                          OpsRuntimeExecutionPlan plan,
                          List<OpsRuntimeEvent> events,
                          Consumer<OpsRuntimeEvent> eventSink);

    String runAgentScopeEngine(OpsAgentDefinition definition,
                               OpsAgentChatRequest request,
                               OpsRuntimeExecutionPlan plan,
                               List<OpsRuntimeEvent> events,
                               Consumer<OpsRuntimeEvent> eventSink);

}
