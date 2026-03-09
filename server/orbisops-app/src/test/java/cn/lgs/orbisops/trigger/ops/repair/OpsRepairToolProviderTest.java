package cn.lgs.orbisops.trigger.ops.repair;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRepairToolProviderTest {

    @Test
    void buildsRepairAndControlledCodeToolSchemasWithoutConcreteServiceDependencies() {
        OpsRepairToolProvider provider = new OpsRepairToolProvider();

        assertThatCode(() -> provider.build("project-1", "tester"))
                .doesNotThrowAnyException();
        assertThat(provider.buildCodeTools("project-1", "tester").stream()
                .map(tool -> tool.getToolDefinition().name())
                .toList())
                .contains(
                        "ValidateCodeCandidate",
                        "code_read",
                        "code_grep",
                        "code_glob",
                        "code_edit",
                        "code_write",
                        "code_bash",
                        "code_lsp",
                        "code_enter_worktree",
                        "code_exit_worktree",
                        "code_compute_diff",
                        "code_commit_repair",
                        "tool_result_read",
                        "tool_result_grep",
                        "tool_result_slice");
    }

    @Test
    void constructorBoundExecutionServiceReceivesControlledCodeRequest() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        when(executionService.execute(org.mockito.ArgumentMatchers.anyMap(), eq("tester")))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        OpsRepairToolProvider provider = new OpsRepairToolProvider(executionService);
        ToolCallback codeRead = provider.buildCodeTools("project-1", "tester").stream()
                .filter(tool -> "code_read".equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        String output = codeRead.call("{\"path\":\"README.md\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(executionService).execute(request.capture(), eq("tester"));
        assertThat(output).contains("SUCCEEDED");
        assertThat(request.getValue())
                .containsEntry("projectId", "project-1")
                .containsEntry("userId", "tester")
                .containsEntry("toolsetId", "code.repository")
                .containsEntry("toolName", "code_read")
                .containsEntry("executionScope", "PRE_APPROVAL_WORKFLOW");
        assertThat((Map<String, Object>) request.getValue().get("arguments"))
                .containsEntry("projectId", "project-1")
                .containsEntry("path", "README.md");
    }
}
