package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateCatalogPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeBaseCatalogCommands;
import cn.lgs.orbisops.application.knowledge.KnowledgeCatalogApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeGlobalAuthorizationCommand;
import cn.lgs.orbisops.application.rag.RagIngestionCommandUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.trigger.application.knowledge.OpsKnowledgeAuthorizationCommandMapper;
import cn.lgs.orbisops.trigger.application.knowledge.OpsKnowledgeCatalogCommandMapper;
import cn.lgs.orbisops.trigger.application.rag.AiClientRagOrderApplicationService;
import cn.lgs.orbisops.trigger.application.rag.LegacyRagDocumentApplicationService;
import cn.lgs.orbisops.trigger.application.rag.RagFeedbackService;
import cn.lgs.orbisops.trigger.application.rag.RagIngestionJobService;
import cn.lgs.orbisops.trigger.application.rag.RagQualityEvalService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientRagOrderAdminControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void createGlobalKnowledgeBaseUsesAuthenticatedPrincipal() {
        KnowledgeCatalogApplicationService<RagIngestionJobView, MultipartFile> knowledgeCatalog =
                mock(KnowledgeCatalogApplicationService.class);
        AiClientRagOrderAdminController controller = controller(knowledgeCatalog);
        when(knowledgeCatalog.createGlobal(any(KnowledgeBaseCatalogCommands.Mutation.class)))
                .thenReturn(Map.of("kbId", "global-kb"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.createGlobalKnowledgeBase(Map.of(
                "kbId", "global-kb",
                "actor", "forged-user"), request).getData();

        assertEquals("global-kb", result.get("kbId"));
        ArgumentCaptor<KnowledgeBaseCatalogCommands.Mutation> command =
                ArgumentCaptor.forClass(KnowledgeBaseCatalogCommands.Mutation.class);
        verify(knowledgeCatalog).createGlobal(command.capture());
        assertEquals("global-kb", command.getValue().knowledgeBaseId().value());
        assertEquals("alice", command.getValue().actor());
    }

    @Test
    @SuppressWarnings("unchecked")
    void enableGlobalKnowledgeBaseUsesAuthenticatedPrincipalAndTypedAuthorization() {
        KnowledgeCatalogApplicationService<RagIngestionJobView, MultipartFile> knowledgeCatalog =
                mock(KnowledgeCatalogApplicationService.class);
        AiClientRagOrderAdminController controller = controller(knowledgeCatalog);
        when(knowledgeCatalog.enableGlobalForProject(any(KnowledgeGlobalAuthorizationCommand.class)))
                .thenReturn(Map.of("kbId", "global-kb", "projectId", "project-1"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.enableGlobalKnowledgeBase(
                "project-1",
                Map.of(
                        "knowledgeTag", "global-kb",
                        "status", "enabled",
                        "enabledBy", "forged-user"),
                request).getData();

        assertEquals("project-1", result.get("projectId"));
        ArgumentCaptor<KnowledgeGlobalAuthorizationCommand> command =
                ArgumentCaptor.forClass(KnowledgeGlobalAuthorizationCommand.class);
        verify(knowledgeCatalog).enableGlobalForProject(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("global-kb", command.getValue().globalKbId());
        assertEquals(KnowledgeStatus.ENABLED, command.getValue().status());
        assertEquals("alice", command.getValue().enabledBy());
    }

    private AiClientRagOrderAdminController controller(
            KnowledgeCatalogApplicationService<RagIngestionJobView, MultipartFile> knowledgeCatalog) {
        return new AiClientRagOrderAdminController(
                mock(AiClientRagOrderApplicationService.class),
                mock(LegacyRagDocumentApplicationService.class),
                mock(KnowledgeAggregateCatalogPort.class),
                mock(RagIngestionCommandUseCase.class),
                mock(RagIngestionJobService.class),
                mock(RagQualityEvalService.class),
                mock(RagFeedbackService.class),
                knowledgeCatalog,
                new OpsKnowledgeAuthorizationCommandMapper(),
                new OpsKnowledgeCatalogCommandMapper());
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
