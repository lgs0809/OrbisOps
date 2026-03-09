package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeKnowledgeResolverTest {

    @Test
    void ragEnabledWithoutExplicitKnowledgeBaseMustUseProjectDefault() {
        ProjectKnowledgeAuthorizationApplicationService authorization =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        when(authorization.defaultId("project-1")).thenReturn("kb-default");
        when(authorization.scope("project-1", "kb-default")).thenReturn("PROJECT");
        OpsRuntimeKnowledgeResolver resolver = resolver(authorization);
        OpsRuntimeResourceContext context = context(true, null);

        resolver.resolve(context);

        assertEquals("kb-default", context.getKnowledgeBaseId());
        assertEquals(true, context.getMetadata().get("ragEnabled"));
        assertEquals("kb-default", context.getMetadata().get("knowledgeBaseId"));
        assertEquals("PROJECT", context.getMetadata().get("knowledgeBaseScope"));
    }

    @Test
    void explicitAuthorizedKnowledgeBaseMustPreserveGlobalScope() {
        ProjectKnowledgeAuthorizationApplicationService authorization =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        when(authorization.scope("project-1", "global-kb")).thenReturn("GLOBAL");
        OpsRuntimeKnowledgeResolver resolver = resolver(authorization);
        OpsRuntimeResourceContext context = context(true, "global-kb");

        resolver.resolve(context);

        assertEquals("global-kb", context.getKnowledgeBaseId());
        assertEquals("GLOBAL", context.getMetadata().get("knowledgeBaseScope"));
        verify(authorization, never()).defaultId("project-1");
    }

    @Test
    void explicitUnauthorizedKnowledgeBaseMustFailClosed() {
        ProjectKnowledgeAuthorizationApplicationService authorization =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        when(authorization.scope("project-1", "foreign-kb")).thenReturn("");
        OpsRuntimeKnowledgeResolver resolver = resolver(authorization);
        OpsRuntimeResourceContext context = context(true, "foreign-kb");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(context));

        assertTrue(error.getMessage().contains("foreign-kb"));
        assertTrue(error.getMessage().contains("project-1"));
    }

    @Test
    void absentAuthorizationServiceMustPreserveLegacyMetadataCompatibility() {
        OpsRuntimeKnowledgeResolver resolver = resolver(null);
        OpsRuntimeResourceContext context = context(false, "legacy-kb");

        resolver.resolve(context);

        assertEquals(false, context.getMetadata().get("ragEnabled"));
        assertEquals("legacy-kb", context.getMetadata().get("knowledgeBaseId"));
        assertEquals("", context.getMetadata().get("knowledgeBaseScope"));
    }

    private OpsRuntimeKnowledgeResolver resolver(
            ProjectKnowledgeAuthorizationApplicationService authorization) {
        return new OpsRuntimeKnowledgeResolver(() -> authorization);
    }

    private OpsRuntimeResourceContext context(
            boolean ragEnabled,
            String knowledgeBaseId) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .request(OpsAgentChatRequest.builder().projectId("project-1").build())
                .projectId("project-1")
                .ragEnabled(ragEnabled)
                .knowledgeBaseId(knowledgeBaseId)
                .events(new ArrayList<>())
                .build();
    }
}
