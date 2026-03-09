package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionAgentBindingMode;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChatSessionBoundariesTest {

    @Test
    void factoryMustApplyAgentSessionDefaultsAndCompactTitle() {
        OpsChatSessionFactory factory = new OpsChatSessionFactory();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .version(4)
                .definitionHash("hash-a")
                .build();
        OpsChatSessionCreateRequest request = OpsChatSessionCreateRequest.builder()
                .userId("user-a")
                .agentId("agent-a")
                .title("   ")
                .build();

        OpsChatSession session = factory.create(
                request,
                new OpsChatSessionAgentBinder.Binding(
                        "agent-a",
                        ChatSessionAgentBindingMode.LATEST_PUBLISHED,
                        definition));

        assertTrue(session.getSessionId().startsWith("chat-session-"));
        assertEquals("AGENT", session.getMode());
        assertEquals("GRAPH", session.getEngine());
        assertEquals("新会话", session.getTitle());
        assertEquals(4, session.getAgentVersion());
        assertEquals("hash-a", session.getAgentDefinitionHash());
    }

    @Test
    void binderMustResolvePinnedProjectAgentAndProjectRequestMetadata() {
        OpsAgentDefinitionQueryGateway gateway =
                mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .version(3)
                .definitionHash("hash-3")
                .build();
        when(gateway.resolveForProject(
                "agent-a", 3, false, "project-a"))
                .thenReturn(definition);
        OpsChatSessionAgentBinder binder =
                new OpsChatSessionAgentBinder(gateway);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("project-a")
                .agentDefinitionId("agent-a")
                .agentVersion(3)
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        "agentBindingMode", "PINNED_VERSION")))
                .build();

        OpsChatSessionAgentBinder.Binding binding = binder.resolve(request);

        assertEquals(ChatSessionAgentBindingMode.PINNED_VERSION, binding.mode());
        assertEquals(3, binding.definition().getVersion());
        assertEquals("hash-3", binding.definition().getDefinitionHash());
    }

    @Test
    void factoryMustNormalizeChatRequestIntoCreateRequest() {
        OpsChatSessionFactory factory = new OpsChatSessionFactory();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .userId("user-a")
                .query("  diagnose   payment   errors  ")
                .agentDefinition(OpsAgentDefinition.builder()
                        .agentId("agent-a")
                        .build())
                .build();

        OpsChatSessionCreateRequest createRequest = factory.createRequest(request);

        assertEquals("agent-a", createRequest.getAgentId());
        assertEquals("AGENT", createRequest.getMode());
        assertEquals("diagnose payment errors", createRequest.getTitle());
        assertEquals("LATEST_PUBLISHED", createRequest.getAgentBindingMode());
    }
}
