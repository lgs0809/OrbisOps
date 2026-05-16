package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectKnowledgeScopeResolverTest {

    @Test
    void resolvesAndNormalizesProjectScopedKnowledgeBase() {
        AtomicReference<String> requestedProject = new AtomicReference<>();
        OpsProjectKnowledgeScopeResolver resolver = new OpsProjectKnowledgeScopeResolver(projectId -> {
            requestedProject.set(projectId);
            return "  kb-demo-project:v1  ";
        });
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        request.setProjectId(" demo-project ");

        String knowledgeBaseId = resolver.resolveKnowledgeBaseId(request);

        assertEquals("demo-project", requestedProject.get());
        assertEquals("kb-demo-project:v1", knowledgeBaseId);
        assertEquals("knowledge == 'kb-demo-project:v1'", resolver.filterFor(knowledgeBaseId));
    }

    @Test
    void productionResolverUsesAuthorizedKnowledgeCatalogDefault() {
        ProjectKnowledgeAuthorizationApplicationService authorization =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        when(authorization.defaultId("demo-project")).thenReturn("demo-ops");
        OpsProjectKnowledgeScopeResolver resolver = new OpsProjectKnowledgeScopeResolver(authorization);
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        request.setProjectId("demo-project");

        assertEquals("demo-ops", resolver.resolveKnowledgeBaseId(request));
    }

    @Test
    void rejectsUnsafeKnowledgeBaseIdentifiersBeforeBuildingFilter() {
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        request.setProjectId("demo-project");
        OpsProjectKnowledgeScopeResolver resolver = new OpsProjectKnowledgeScopeResolver(
                projectId -> "kb' OR true OR knowledge='other");

        assertEquals("", resolver.resolveKnowledgeBaseId(request));
        assertThrows(IllegalArgumentException.class, () -> resolver.filterFor("kb' OR true"));
    }

    @Test
    void unavailableResolverFailsClosed() {
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        request.setProjectId("demo-project");

        assertEquals("", OpsProjectKnowledgeScopeResolver.unavailable().resolveKnowledgeBaseId(request));
        assertEquals("", OpsProjectKnowledgeScopeResolver.unavailable().resolveKnowledgeBaseId(null));
    }
}
