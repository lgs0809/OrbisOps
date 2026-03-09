package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.application.project.ProjectWorkspacePort;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageMcpTemplateUseCaseTest {

    @Test
    void createOverridesUntrustedCreateByWithAuthenticatedActor() {
        McpTemplatePort port = mock(McpTemplatePort.class);
        McpAuditPort auditPort = mock(McpAuditPort.class);
        ManageMcpTemplateUseCase useCase = useCase(port, auditPort);
        when(port.createDefinition(any(McpTemplateDefinition.class)))
                .thenAnswer(invocation -> entry(invocation.getArgument(0)));

        useCase.create(Map.of(
                "templateId", "template-1",
                "templateName", "Template One",
                "resourceType", "mysql",
                "transportType", "stdio",
                "riskLevel", "LOW",
                "createBy", "forged-user"), "alice");

        ArgumentCaptor<McpTemplateDefinition> definition =
                ArgumentCaptor.forClass(McpTemplateDefinition.class);
        verify(port).createDefinition(definition.capture());
        assertEquals("alice", definition.getValue().createBy());
        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq(""),
                eq("mcp-template"),
                eq("create"),
                eq("template-1"),
                org.mockito.ArgumentMatchers.isNull(),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
    }

    @Test
    void copyWithoutExplicitIdDelegatesToPortCopyAndCarriesActor() {
        McpTemplatePort port = mock(McpTemplatePort.class);
        McpAuditPort auditPort = mock(McpAuditPort.class);
        ManageMcpTemplateUseCase useCase = useCase(port, auditPort);
        McpTemplateCatalogEntry source = entry(template("template-1", "original"));
        McpTemplateCatalogEntry copied = entry(template("template-1-copy", "alice"));
        when(port.getEntry("template-1")).thenReturn(source);
        when(port.copyDefinition(eq("template-1"), anyMap())).thenReturn(copied);

        useCase.copy("template-1", Map.of(
                "templateName", "Copy",
                "createBy", "forged-user"), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(port).copyDefinition(eq("template-1"), command.capture());
        assertEquals("alice", command.getValue().get("createBy"));
        verify(port, never()).createDefinition(any(McpTemplateDefinition.class));
    }

    @Test
    void generateProjectToolUsesTypedTemplateAndMaterializesView() {
        McpTemplatePort port = mock(McpTemplatePort.class);
        McpAuditPort auditPort = mock(McpAuditPort.class);
        McpProjectTemplateGenerationPort generationService =
                mock(McpProjectTemplateGenerationPort.class);
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ManageMcpTemplateUseCase useCase = new ManageMcpTemplateUseCase(
                port, auditPort, generationService, workspacePort);
        McpTemplateDefinition template = template("template-1", "alice");
        McpTemplateCatalogEntry templateEntry = entry(template);
        Map<String, Object> generatedView = Map.of(
                "projectId", "project-1",
                "mcpId", "tool-1",
                "toolId", "tool-1");
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        ProjectMcpDefinition generated = new ProjectMcpDefinition(
                "tool-1", "Generated Tool", "project-1", "resource-1", "mysql",
                "stdio", "template-1", Map.of(), List.of("SELECT"), ProjectMcpRiskLevel.LOW,
                true, Map.of(), 15, ProjectMcpStatus.ENABLED, now, now);
        when(port.getEntry("template-1")).thenReturn(templateEntry);
        when(generationService.generate(eq("project-1"), eq(template), anyMap()))
                .thenReturn(new McpProjectToolGenerationOutcome(generated, generatedView));

        useCase.generateProjectTool(
                "project-1",
                "template-1",
                Map.of("actor", "forged-user", "resourceId", "resource-1"),
                "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(generationService).generate(
                eq("project-1"), eq(template), command.capture());
        assertEquals("alice", command.getValue().get("actor"));
        verify(workspacePort).materializeMcp(generated);
        verify(auditPort).record(
                eq("project-1"),
                eq("project-tool"),
                eq("generate-from-template"),
                eq("tool-1"),
                eq(Map.of("templateId", "template-1")),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRejectsMissingAuthenticatedActor() {
        McpTemplatePort port = mock(McpTemplatePort.class);
        McpAuditPort auditPort = mock(McpAuditPort.class);
        ManageMcpTemplateUseCase useCase = useCase(port, auditPort);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(Map.of(
                        "templateId", "template-1",
                        "resourceType", "mysql"), " "));

        assertEquals("MCP_ACTOR_REQUIRED", error.getMessage());
    }

    private McpTemplateDefinition template(String id, String creator) {
        return new McpTemplateDefinition(
                id,
                "Template " + id,
                "mysql",
                "stdio",
                Map.of(),
                List.of("SELECT"),
                "LOW",
                true,
                "",
                McpTemplateStatus.ENABLED,
                creator);
    }

    private McpTemplateCatalogEntry entry(McpTemplateDefinition definition) {
        return new McpTemplateCatalogEntry(1L, definition, null, null);
    }

    private ManageMcpTemplateUseCase useCase(
            McpTemplatePort port,
            McpAuditPort auditPort) {
        return new ManageMcpTemplateUseCase(
                port,
                auditPort,
                mock(McpProjectTemplateGenerationPort.class),
                mock(ProjectWorkspacePort.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
