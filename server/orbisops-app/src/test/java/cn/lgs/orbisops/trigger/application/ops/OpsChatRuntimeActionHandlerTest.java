package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsMainAgentActionType;
import cn.lgs.orbisops.application.agent.OpsMainAgentCommand;
import cn.lgs.orbisops.application.agent.OpsMainAgentOutcome;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatRuntimeActionHandlerTest {

    @SuppressWarnings("unchecked")
    @Test
    void timedOutSynchronousChatMustPromoteIncidentAfterBackgroundCompletion() throws Exception {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                mock(ExecuteWorkSessionUseCase.class);
        GraphEventApplicationService graphEvents = mock(GraphEventApplicationService.class);
        OpsChatIncidentPromotionService promotionService = mock(OpsChatIncidentPromotionService.class);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-late-promotion")
                .sessionId("session-late-promotion")
                .projectId("demo-project")
                .userId("user-1")
                .query("证据够的话正式记录下来")
                .build();
        OpsAgentChatResponse completed = OpsAgentChatResponse.builder().content("completed").build();
        CountDownLatch releaseExecution = new CountDownLatch(1);
        when(workSession.execute(request)).thenAnswer(invocation -> {
            assertTrue(releaseExecution.await(5, TimeUnit.SECONDS));
            return completed;
        });
        OpsChatRuntimeActionHandler handler = new OpsChatRuntimeActionHandler(
                workSession,
                graphEvents,
                new OpsChatRuntimeSettings(1L),
                promotionService);
        OpsMainAgentCommand command = new OpsMainAgentCommand(
                OpsMainAgentActionType.AGENT,
                request.getRunId(),
                request.getSessionId(),
                request.getProjectId(),
                request.getUserId(),
                request.getQuery(),
                new OpsChatActionPayload(request, null, true),
                Map.of());

        OpsMainAgentOutcome timeoutOutcome = handler.handle(command);

        OpsAgentChatResponse timeout = (OpsAgentChatResponse) timeoutOutcome.result();
        assertEquals("TIMEOUT", String.valueOf(timeout.getMetadata().get("status")));
        releaseExecution.countDown();
        verify(promotionService, org.mockito.Mockito.timeout(2000)).promote(request, completed);
    }
}
