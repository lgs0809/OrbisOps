package cn.lgs.orbisops.trigger.application.chatsession;

import cn.lgs.orbisops.domain.chatsession.model.ChatMessageSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsChatSessionMapperTest {

    private final OpsChatSessionMapper mapper = new OpsChatSessionMapper();

    @Test
    void sessionRoundTripPreservesBindingCasAndMetadata() {
        OpsChatSession source = OpsChatSession.builder()
                .sessionId("session-1")
                .userId("owner")
                .projectId("project-1")
                .agentId("agent-1")
                .agentBindingMode("PINNED_VERSION")
                .agentVersion(3)
                .agentDefinitionHash("a".repeat(64))
                .title("title")
                .mode("AGENT")
                .engine("GRAPH")
                .ragEnabled(true)
                .knowledgeBaseId("kb-1")
                .status("ACTIVE")
                .stateVersion(4L)
                .messageCount(2)
                .lastMessage("last")
                .metadata(Map.of("favorite", true))
                .build();

        ChatSessionSnapshot snapshot = mapper.snapshot(source);
        OpsChatSession view = mapper.view(snapshot);

        assertEquals("PINNED_VERSION", snapshot.agentBindingMode());
        assertEquals(4L, snapshot.stateVersion());
        assertEquals(3, view.getAgentVersion());
        assertEquals("kb-1", view.getKnowledgeBaseId());
        assertEquals(true, view.getMetadata().get("favorite"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.metadata().put("forged", true));
    }

    @Test
    void messageSnapshotMapsToTriggerView() {
        OpsChatMessageView view = mapper.view(new ChatMessageSnapshot(
                "message-1",
                "session-1",
                "owner",
                "USER",
                "hello",
                "2026-07-21 10:00:00",
                Map.of("source", "chat")));

        assertEquals("message-1", view.getMessageId());
        assertEquals("hello", view.getContent());
        assertEquals("chat", view.getMetadata().get("source"));
    }
}
