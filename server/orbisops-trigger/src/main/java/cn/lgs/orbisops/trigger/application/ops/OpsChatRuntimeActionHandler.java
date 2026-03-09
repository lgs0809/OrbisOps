package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsMainAgentActionHandler;
import cn.lgs.orbisops.application.agent.OpsMainAgentActionType;
import cn.lgs.orbisops.application.agent.OpsMainAgentCommand;
import cn.lgs.orbisops.application.agent.OpsMainAgentOutcome;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/** Main-agent action adapter for built-in and pre-approval Work Session execution. */
@Service
public class OpsChatRuntimeActionHandler implements OpsMainAgentActionHandler {

    private final ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession;
    private final OpsChatSyncExecutionCoordinator syncExecutionCoordinator;
    private final OpsChatIncidentPromotionService incidentPromotionService;

    public OpsChatRuntimeActionHandler(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
            GraphEventApplicationService graphEvents) {
        this(executeWorkSession, graphEvents, OpsChatRuntimeSettings.defaults(), null);
    }

    OpsChatRuntimeActionHandler(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
            GraphEventApplicationService graphEvents,
            OpsChatRuntimeSettings settings) {
        this(executeWorkSession, graphEvents, settings, null);
    }

    @Autowired
    public OpsChatRuntimeActionHandler(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
            GraphEventApplicationService graphEvents,
            OpsChatRuntimeSettings settings,
            OpsChatIncidentPromotionService incidentPromotionService) {
        this.executeWorkSession = executeWorkSession;
        this.syncExecutionCoordinator = new OpsChatSyncExecutionCoordinator(
                executeWorkSession,
                graphEvents,
                settings);
        this.incidentPromotionService = incidentPromotionService;
    }

    @Override
    public Set<OpsMainAgentActionType> supportedActions() {
        return Set.of(OpsMainAgentActionType.AGENT);
    }

    @Override
    public OpsMainAgentOutcome handle(OpsMainAgentCommand command) {
        OpsChatActionPayload payload = requirePayload(command);
        OpsAgentChatResponse response = payload.synchronous()
                ? syncExecutionCoordinator.execute(
                        payload.request(),
                        completed -> promoteLateCompletion(payload.request(), completed))
                : executeWorkSession.execute(payload.request(), payload.eventSink());
        return OpsMainAgentOutcome.succeeded(response, Map.of(
                "handler", getClass().getSimpleName(),
                "actionType", command.actionType().name()));
    }

    private void promoteLateCompletion(
            OpsAgentChatRequest request,
            OpsAgentChatResponse response) {
        if (incidentPromotionService == null || request == null || response == null) return;
        incidentPromotionService.promote(request, response);
    }

    private OpsChatActionPayload requirePayload(OpsMainAgentCommand command) {
        if (!(command.payload() instanceof OpsChatActionPayload payload)) {
            throw new IllegalArgumentException("MAIN_AGENT_CHAT_PAYLOAD_REQUIRED");
        }
        return payload;
    }
}
