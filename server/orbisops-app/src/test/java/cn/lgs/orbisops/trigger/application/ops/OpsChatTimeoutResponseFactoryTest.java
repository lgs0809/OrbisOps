package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChatTimeoutResponseFactoryTest {

    @Test
    void shouldProjectDurableResumeHandleWithoutCancelingWorkSession() {
        OpsChatTimeoutResponseFactory factory = new OpsChatTimeoutResponseFactory(
                new OpsChatRuntimeSettings(12L));

        OpsAgentChatResponse response = factory.create(OpsAgentChatRequest.builder()
                .runId("  run-12  ")
                .sessionId("session-1")
                .userId("alice")
                .agentDefinitionId("ops-agent")
                .mode(null)
                .build());

        assertAll(
                () -> assertEquals("session-1", response.getSessionId()),
                () -> assertEquals("alice", response.getUserId()),
                () -> assertEquals("ops-agent", response.getAgentId()),
                () -> assertEquals("AGENT", response.getMode()),
                () -> assertEquals("SYNC_TIMEOUT_GUARD", response.getEngine()),
                () -> assertTrue(response.getContent().contains("12 秒")),
                () -> assertTrue(response.getContent().contains("没有被取消")),
                () -> assertEquals("run-12", response.getMetadata().get("runId")),
                () -> assertEquals("TIMEOUT", response.getMetadata().get("status")),
                () -> assertEquals(12L, response.getMetadata().get("timeoutSeconds")),
                () -> assertEquals(true, response.getMetadata().get("agenticWorkSessionStarted")),
                () -> assertEquals(2, response.getEvents().size()),
                () -> assertEquals("WORK_SESSION_TIMEOUT", response.getEvents().get(0).getEventType()),
                () -> assertEquals("DONE", response.getEvents().get(1).getEventType()));
    }
}
