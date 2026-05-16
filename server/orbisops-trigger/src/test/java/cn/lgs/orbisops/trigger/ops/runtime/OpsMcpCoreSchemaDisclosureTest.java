package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpsMcpCoreSchemaDisclosureTest {
    @Test void coreOnlyServerExposesSchemaInspectionWithoutHydratingEveryToolAtStartup() {
        var policy = mock(OpsMcpCallbackPolicyAdapter.class);
        var service = mock(OpsToolExecutionService.class);
        var callbacks = new OpsProgressiveMcpCallbackAdapter(() -> service);
        var progressive = mock(OpsMcpProgressiveRuntimeAdapter.class);
        var remote = mock(OpsMcpRuntimeInvoker.class);
        var config = OpsMcpServerConfig.builder().name("test").mcpId("test").toolId("test").build();
        when(progressive.exposure(config)).thenReturn(OpsMcpProgressiveExposure.managed(
                List.of(Map.of("toolName", "target_version", "disclosureTier", "CORE")), true));
        var actual = new OpsMcpToolCallbackAssembler(policy, callbacks, progressive, remote).assemble(List.of(config));
        assertThat(actual).extracting(tool -> tool.getToolDefinition().name())
                .containsExactly("mcp_tool_catalog_test", "enable_mcp_tool_test", "project_mcp_test");
        verifyNoInteractions(remote, service);
        String input = "{\"toolName\":\"target_version\",\"reason\":\"先核对参数定义\"}";
        when(service.enableMcpTool(config, input, "ops-agent")).thenReturn(Map.of("schema", Map.of("required", List.of("serviceId"))));
        assertThat(callbacks.enableCallback(config).call(input)).contains("serviceId");
        verify(service).enableMcpTool(config, input, "ops-agent");
        verifyNoMoreInteractions(service);
    }
}
