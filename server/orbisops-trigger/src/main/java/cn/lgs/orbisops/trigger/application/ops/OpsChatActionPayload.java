package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsMainAgentPayload;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;

import java.util.function.Consumer;

public record OpsChatActionPayload(
        OpsAgentChatRequest request,
        Consumer<OpsRuntimeEvent> eventSink,
        boolean synchronous) implements OpsMainAgentPayload {
}
