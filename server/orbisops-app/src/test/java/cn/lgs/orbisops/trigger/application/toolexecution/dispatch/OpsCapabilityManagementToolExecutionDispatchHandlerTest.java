package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.capability.OpsCapabilityImportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsCapabilityManagementToolExecutionDispatchHandlerTest {

    @Test
    void projectSkillCreateUsesUnifiedProjectActionAndForcesDraftManualGovernance() {
        AuthorizeProjectAccessUseCase access = mock(AuthorizeProjectAccessUseCase.class);
        SkillManagementUseCase skills = mock(SkillManagementUseCase.class);
        when(skills.createProjectSkill(eq("project-1"), anyMap(), eq("u-1")))
                .thenReturn(Map.of("skillId", "skill-1"));
        OpsCapabilityManagementToolExecutionDispatchHandler handler =
                handler(access, skills, null);

        Object result = handler.dispatch(target("skill_project_create"), request(
                "skill_project_create",
                Map.of(
                        "skillId", "skill-1",
                        "name", "Order Diagnosis",
                        "status", "ENABLED",
                        "updateMode", "AUTO")));

        assertEquals("skill-1", ((Map<?, ?>) result).get("skillId"));
        verify(access).requireAction(
                "project-1", "alice", "u-1", false, ProjectAction.MANAGE_CAPABILITY);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(skills).createProjectSkill(eq("project-1"), payload.capture(), eq("u-1"));
        assertEquals("DRAFT", payload.getValue().get("status"));
        assertEquals("MANUAL_ONLY", payload.getValue().get("updateMode"));
    }

    @Test
    void skillAndMcpImportDelegateOnlyAtomicMutationToGovernedImporter() {
        AuthorizeProjectAccessUseCase access = mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityImportService imports = mock(OpsCapabilityImportService.class);
        when(imports.importSkill(
                eq("project-1"), eq("https://example.com/skill.zip"), anyMap(),
                eq("alice"), eq("u-1"), eq(false)))
                .thenReturn(Map.of("status", "IMPORTED_PAUSED"));
        when(imports.importMcp(
                eq("project-1"), eq("https://mcp.example.com/sse"), anyMap(),
                eq("alice"), eq("u-1"), eq(false)))
                .thenReturn(Map.of("status", "DISCOVERED_PENDING_REVIEW"));
        OpsCapabilityManagementToolExecutionDispatchHandler handler =
                handler(access, null, imports);

        Map<?, ?> skill = (Map<?, ?>) handler.dispatch(
                target("skill_package_import"),
                request("skill_package_import", Map.of(
                        "sourceUrl", "https://example.com/skill.zip",
                        "capabilityName", "redis-diagnosis")));
        Map<?, ?> mcp = (Map<?, ?>) handler.dispatch(
                target("mcp_server_import"),
                request("mcp_server_import", Map.of(
                        "sourceUrl", "https://mcp.example.com/sse",
                        "transportType", "sse")));

        assertEquals("IMPORTED_PAUSED", skill.get("status"));
        assertEquals("DISCOVERED_PENDING_REVIEW", mcp.get("status"));
        verify(imports).importSkill(
                eq("project-1"), eq("https://example.com/skill.zip"), anyMap(),
                eq("alice"), eq("u-1"), eq(false));
        verify(imports).importMcp(
                eq("project-1"), eq("https://mcp.example.com/sse"), anyMap(),
                eq("alice"), eq("u-1"), eq(false));
    }

    private OpsCapabilityManagementToolExecutionDispatchHandler handler(
            AuthorizeProjectAccessUseCase access,
            SkillManagementUseCase skills,
            OpsCapabilityImportService imports) {
        return new OpsCapabilityManagementToolExecutionDispatchHandler(
                provider(access), provider(skills), provider(imports));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "capability.manage", toolName, "CAPABILITY_MANAGEMENT", "MEDIUM",
                false, false, false, false, false);
    }

    private ToolExecutionRequest request(String toolName, Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1",
                "u-1",
                "u-1",
                "capability.manage",
                toolName,
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                arguments,
                "session-1",
                "run-1",
                Map.of("authenticatedUsername", "alice", "agentId", "agent-1"),
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
