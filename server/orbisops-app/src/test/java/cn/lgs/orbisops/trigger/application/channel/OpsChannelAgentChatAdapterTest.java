package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelAgentChatPort;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelAgentChatAdapterTest {

    private OpsChatApplicationService chat;
    private OpsAgentDefinitionQueryGateway agents;
    private OpsChannelAgentChatAdapter adapter;

    @BeforeEach
    void setUp() {
        chat = mock(OpsChatApplicationService.class);
        agents = mock(OpsAgentDefinitionQueryGateway.class);
        adapter = new OpsChannelAgentChatAdapter(chat, agents);
        when(agents.resolveForProject("agent-1", 3, false, "project-1"))
                .thenReturn(definition("agent-hash"));
        when(chat.chat(any(), any())).thenReturn(OpsAgentChatResponse.builder().content("answer").build());
    }

    @Test
    void mappedIdentityPropagatesTrustedPrincipalAndBindingMetadata() {
        ChannelIdentityRecord identity = new ChannelIdentityRecord(
                "mapping-1", "channel-1", "project-1", "sender-1", "user-1", "alice",
                ChannelStatus.ACTIVE, 1, "admin", null, null);

        ChannelAgentChatPort.ChatResult result = adapter.chat(command(
                identity, ChannelAgentChatPort.IdentityStatus.MAPPED));

        ArgumentCaptor<OpsAgentChatRequest> request = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(chat).chat(request.capture(), eq("user-1"));
        assertEquals("answer", result.content());
        assertEquals("user-1", result.userId());
        assertEquals("WORKFLOW", request.getValue().getMetadata().get("executionType"));
        assertEquals("MAPPED", request.getValue().getMetadata().get("channelIdentityStatus"));
        Object principal = request.getValue().getMetadata().get(OpsTrustedRequestMetadata.AUTH_PRINCIPAL);
        assertTrue(principal instanceof AdminAuthService.AuthPrincipal);
        assertEquals(AdminAuthService.SCOPE_USER, ((AdminAuthService.AuthPrincipal) principal).scope());
    }

    @Test
    void invalidIdentityCannotReachChannelChatAdapter() {
        SecurityException failure = assertThrows(SecurityException.class,
                () -> command(null, ChannelAgentChatPort.IdentityStatus.INVALID));

        assertEquals("CHANNEL_INVALID_IDENTITY_MUST_NOT_ENTER_RUNTIME", failure.getMessage());
        verify(chat, never()).chat(any(), any());
    }

    @Test
    void observeOnlyUnknownSetsServerOwnedRuntimeDowngradeWithoutTrustedPrincipal() {
        ChannelAgentChatPort.ChatResult result = adapter.chat(command(
                null, ChannelAgentChatPort.IdentityStatus.UNMAPPED));

        ArgumentCaptor<OpsAgentChatRequest> request = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(chat).chat(request.capture(), eq("channel:channel-1:sender-1"));
        assertEquals("channel:channel-1:sender-1", result.userId());
        assertEquals("UNMAPPED", request.getValue().getMetadata().get("channelIdentityStatus"));
        assertEquals("OBSERVE_ONLY_UNKNOWN", request.getValue().getMetadata().get("channelRuntimeAccess"));
        assertTrue(request.getValue().getTrustedObserveOnly());
        assertTrue(!request.getValue().getMetadata().containsKey(OpsTrustedRequestMetadata.AUTH_PRINCIPAL));
    }

    @Test
    void definitionHashMismatchStopsBeforeChatExecution() {
        when(agents.resolveForProject("agent-1", 3, false, "project-1"))
                .thenReturn(definition("other-hash"));

        SecurityException failure = assertThrows(SecurityException.class,
                () -> adapter.chat(command(null, ChannelAgentChatPort.IdentityStatus.UNMAPPED)));

        assertEquals("CHANNEL_EXECUTION_DEFINITION_HASH_MISMATCH", failure.getMessage());
        verify(chat, never()).chat(any(), any());
    }

    private ChannelAgentChatPort.ChatCommand command(ChannelIdentityRecord identity,
                                                     ChannelAgentChatPort.IdentityStatus status) {
        return new ChannelAgentChatPort.ChatCommand(
                "project-1", "channel-1", "GENERIC_WEBHOOK", "run-1", "session-1",
                ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash", message(), identity, status,
                status == ChannelAgentChatPort.IdentityStatus.MAPPED
                        ? ChannelAgentChatPort.RuntimeAccess.AUTHENTICATED
                        : ChannelAgentChatPort.RuntimeAccess.OBSERVE_ONLY_UNKNOWN);
    }

    private ChannelModels.InboundMessage message() {
        return new ChannelModels.InboundMessage(
                "external-1", "conversation-1", "sender-1", "investigate", 100,
                Map.of(), "TEXT", List.of(), null);
    }

    private OpsAgentDefinition definition(String hash) {
        return OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .version(3)
                .definitionHash(hash)
                .engine("GRAPH")
                .name("Agent")
                .build();
    }
}
