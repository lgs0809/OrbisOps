package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.chatsession.ChatSessionMemoryCatalog;
import cn.lgs.orbisops.application.chatsession.ChatSessionStoreApplicationService;
import cn.lgs.orbisops.application.chatsession.ChatSessionTransactionPort;
import cn.lgs.orbisops.domain.chatsession.adapter.repository.IChatSessionRepository;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatSessionServiceTest {

    @Test
    void shouldBindCurrentAgentVersionToNewChatSession() {
        OpsMemoryFacade memoryFacade = mock(OpsMemoryFacade.class);
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve("ops-agent", null, false)).thenReturn(definition(7));
        OpsChatSessionService service = service(null, memoryFacade, registry, true);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .userId("u1")
                .agentDefinitionId("ops-agent")
                .query("检查最近错误")
                .build();

        OpsChatSession session = service.ensureForChat(request);

        assertNotNull(session.getSessionId());
        assertEquals(7, session.getAgentVersion());
        assertEquals("LATEST_PUBLISHED", session.getAgentBindingMode());
        assertEquals(7, request.getAgentVersion());
        assertEquals("ops-agent", request.getAgentDefinitionId());
    }

    @Test
    void shouldKeepExplicitAgentVersionOnNewChatSession() {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve("ops-agent", 3, false)).thenReturn(definition(3));
        OpsChatSessionService service = service(
                null, mock(OpsMemoryFacade.class), registry, true);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .userId("u1")
                .agentDefinitionId("ops-agent")
                .agentVersion(3)
                .query("检查最近错误")
                .build();

        OpsChatSession session = service.ensureForChat(request);

        assertEquals(3, session.getAgentVersion());
        assertEquals("PINNED_VERSION", session.getAgentBindingMode());
        assertEquals(3, request.getAgentVersion());
    }

    @Test
    void shouldFilterFallbackSessionsByKeywordAndFavorite() {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve("ops-agent", null, false)).thenReturn(definition(1));
        OpsChatSessionService service = service(
                null, mock(OpsMemoryFacade.class), registry, true);
        service.create(OpsChatSessionCreateRequest.builder()
                .userId("u1")
                .agentId("ops-agent")
                .title("慢 SQL 处理 SOP")
                .metadata(Map.of("favorite", true))
                .build());
        service.create(OpsChatSessionCreateRequest.builder()
                .userId("u1")
                .agentId("ops-agent")
                .title("Prometheus 指标排查")
                .build());

        List<OpsChatSession> favoriteSessions = service.list(
                "u1", "ops-agent", "慢 SQL", true, 10);
        List<OpsChatSession> prometheusSessions = service.list(
                "u1", "ops-agent", "prometheus", null, 10);

        assertEquals(1, favoriteSessions.size());
        assertEquals("慢 SQL 处理 SOP", favoriteSessions.get(0).getTitle());
        assertEquals(1, prometheusSessions.size());
        assertEquals("Prometheus 指标排查", prometheusSessions.get(0).getTitle());
    }

    @Test
    void shouldRejectSwitchingExistingSessionAcrossProjects() {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolveForProject(
                "ops-agent", null, false, "payment"))
                .thenReturn(definition(1));
        OpsChatSessionService service = service(
                null, mock(OpsMemoryFacade.class), registry, true);
        OpsChatSession session = service.create(OpsChatSessionCreateRequest.builder()
                .userId("u1")
                .projectId("payment")
                .agentId("ops-agent")
                .build());

        assertThrows(IllegalArgumentException.class, () -> service.ensureForChat(
                OpsAgentChatRequest.builder()
                        .sessionId(session.getSessionId())
                        .userId("u1")
                        .projectId("demo-project")
                        .agentDefinitionId("ops-agent")
                        .query("继续排查")
                        .build()));
    }

    @Test
    void shouldUseStateVersionCasForConcurrentSessionUpdates() {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolveForProject(
                "ops-agent", null, false, "payment"))
                .thenReturn(definition(1));
        OpsChatSessionService service = service(
                null, mock(OpsMemoryFacade.class), registry, true);
        OpsChatSession session = service.create(OpsChatSessionCreateRequest.builder()
                .userId("u1")
                .projectId("payment")
                .agentId("ops-agent")
                .build());

        OpsChatSession updated = service.update(
                session.getSessionId(),
                OpsChatSessionUpdateRequest.builder()
                        .title("updated")
                        .expectedStateVersion(1L)
                        .build());

        assertEquals(2L, updated.getStateVersion());
        assertThrows(IllegalStateException.class, () -> service.update(
                session.getSessionId(),
                OpsChatSessionUpdateRequest.builder()
                        .title("stale")
                        .expectedStateVersion(1L)
                        .build()));
    }

    @Test
    void shouldEnforceParticipantRolesAndParticipantListCas() {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolveForProject(
                "ops-agent", null, false, "payment"))
                .thenReturn(definition(1));
        OpsChatSessionService service = service(
                null, mock(OpsMemoryFacade.class), registry, true);
        OpsChatSession session = service.create(OpsChatSessionCreateRequest.builder()
                .userId("owner")
                .projectId("payment")
                .agentId("ops-agent")
                .build());

        List<OpsChatSessionService.SessionParticipant> participants =
                service.replaceParticipants(
                        session.getSessionId(),
                        1L,
                        List.of(
                                new OpsChatSessionService.SessionParticipantInput(
                                        "observer", "OBSERVER"),
                                new OpsChatSessionService.SessionParticipantInput(
                                        "editor", "EDITOR")),
                        "owner");

        assertEquals(3, participants.size());
        assertTrue(service.canRead(session.getSessionId(), "observer"));
        assertEquals(false, service.canWrite(session.getSessionId(), "observer"));
        assertTrue(service.canWrite(session.getSessionId(), "editor"));
        assertThrows(SecurityException.class, () -> service.replaceParticipants(
                session.getSessionId(), 2L, List.of(), "editor"));
        assertThrows(IllegalStateException.class, () -> service.replaceParticipants(
                session.getSessionId(), 1L, List.of(), "owner"));
    }

    @Test
    void latestPublishedSessionResolvesAgentAgainForEveryRun() {
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolveForProject(
                "ops-agent", null, false, "payment"))
                .thenReturn(definition(1), definition(2), definition(2));
        OpsChatSessionService service = service(
                null, mock(OpsMemoryFacade.class), registry, true);
        OpsChatSession session = service.create(OpsChatSessionCreateRequest.builder()
                .userId("u1")
                .projectId("payment")
                .agentId("ops-agent")
                .agentBindingMode("LATEST_PUBLISHED")
                .build());
        OpsAgentChatRequest nextRun = OpsAgentChatRequest.builder()
                .sessionId(session.getSessionId())
                .userId("u1")
                .projectId("payment")
                .query("继续排查")
                .build();

        service.ensureForChat(nextRun);

        assertEquals(1, session.getAgentVersion());
        assertEquals(2, nextRun.getAgentVersion());
        assertEquals(
                "ops-agent-v2",
                nextRun.getAgentDefinition().getDefinitionHash());
    }

    @Test
    void shouldDelegatePersistentSessionCreationToTypedRepository() {
        IChatSessionRepository repository = mock(IChatSessionRepository.class);
        when(repository.available()).thenReturn(true);
        OpsAgentDefinitionQueryGateway registry =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve("ops-agent", null, false)).thenReturn(definition(5));
        OpsChatSessionService service = service(
                repository, mock(OpsMemoryFacade.class), registry, false);

        OpsChatSession session = service.create(OpsChatSessionCreateRequest.builder()
                .userId("u1")
                .agentId("ops-agent")
                .build());

        assertEquals(5, session.getAgentVersion());
        verify(repository).insert(any(ChatSessionSnapshot.class));
        verify(repository).findById(session.getSessionId());
    }

    private OpsChatSessionService service(
            IChatSessionRepository repository,
            OpsMemoryFacade memoryFacade,
            OpsAgentDefinitionQueryGateway registry,
            boolean allowInMemoryFallback) {
        ChatSessionStoreApplicationService store =
                new ChatSessionStoreApplicationService(
                        repository,
                        new ChatSessionMemoryCatalog(),
                        () -> allowInMemoryFallback,
                        ignored -> { });
        OpsChatSessionAgentBinder binder =
                new OpsChatSessionAgentBinder(registry);
        return new OpsChatSessionService(
                store,
                directTransactionPort(),
                memoryFacade,
                binder,
                new OpsChatSessionFactory(),
                new OpsChatSessionParticipantCoordinator(store));
    }

    private ChatSessionTransactionPort directTransactionPort() {
        return new ChatSessionTransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }

    private static OpsAgentDefinition definition(int version) {
        return OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .projectId("payment")
                .version(version)
                .definitionHash("ops-agent-v" + version)
                .build();
    }
}
