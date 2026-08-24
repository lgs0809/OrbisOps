package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import java.util.ArrayList;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OpsChangePackageStatusRuntimeToolContributorTest {
    @Test void optInExposesOnlyProjectScopedStatusAndDoesNotDuplicateIt() {
        var provider = mock(OpsChangePackageToolProvider.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("provider", provider);
        var contributor = new OpsChangePackageStatusRuntimeToolContributor(beans.getBeanProvider(OpsChangePackageToolProvider.class));
        var request = OpsAgentChatRequest.builder().projectId("p").runId("run").userId("alice").build();
        var tool = FunctionToolCallback.builder("QueryChangePackageStatus", (String input) -> "{}").inputType(String.class).build();
        when(provider.buildStatusQuery("p", "alice", "run", request)).thenReturn(tool);
        var context = OpsRuntimeResourceContext.builder().projectId("p").request(request)
                .node(OpsWorkflowNode.builder().config(Map.of("changePackageStatusEnabled", true)).build())
                .tools(new ArrayList<>()).build();
        contributor.contribute(context);
        contributor.contribute(context);
        assertEquals(1, context.getTools().size());
        assertEquals("QueryChangePackageStatus", context.getTools().get(0).getToolDefinition().name());
        verify(provider).buildStatusQuery("p", "alice", "run", request);
        verifyNoMoreInteractions(provider);
        context.setNode(OpsWorkflowNode.builder().config(Map.of()).build());
        context.setTools(new ArrayList<>());
        contributor.contribute(context);
        assertTrue(context.getTools().isEmpty());
    }
    @Test void scopedGraphProjectionPreservesStatusCapability() {
        var node = OpsWorkflowNode.builder().nodeId("resolve").agent("resolver").type("AGENTSCOPE")
                .config(Map.of("changePackageStatusEnabled", true, "role", "DATA_AGENT",
                        "allowedToolNames", java.util.List.of("QueryChangePackageStatus"))).build();
        var definition = OpsAgentDefinition.builder().agentId("c").nodes(java.util.List.of(node)).build();
        var request = OpsAgentChatRequest.builder().projectId("p").runId("run").userId("alice").build();
        var scope = new OpsAgentScopeConfigPolicy().configs(definition, request).get(0);
        assertTrue(scope.getChangePackageStatusEnabled());
        var provider = mock(OpsChangePackageToolProvider.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("provider", provider);
        when(provider.buildStatusQuery("p", "alice", "run", request)).thenReturn(
                FunctionToolCallback.builder("QueryChangePackageStatus", (String input) -> "{}").inputType(String.class).build());
        var context = OpsRuntimeResourceContext.builder().projectId("p").request(request).agentScope(scope)
                .tools(new ArrayList<>()).build();
        new OpsChangePackageStatusRuntimeToolContributor(beans.getBeanProvider(OpsChangePackageToolProvider.class)).contribute(context);
        assertEquals(1, context.getTools().size());
        assertEquals("QueryChangePackageStatus", context.getTools().get(0).getToolDefinition().name());
        scope.setChangePackageStatusEnabled(false);
        context.setTools(new ArrayList<>());
        new OpsChangePackageStatusRuntimeToolContributor(beans.getBeanProvider(OpsChangePackageToolProvider.class)).contribute(context);
        assertTrue(context.getTools().isEmpty());
    }
    @Test void missingRuntimeIdentityCannotEnableStatusLookup() {
        var beans = new DefaultListableBeanFactory();
        var contributor = new OpsChangePackageStatusRuntimeToolContributor(beans.getBeanProvider(OpsChangePackageToolProvider.class));
        var context = OpsRuntimeResourceContext.builder().projectId("p")
                .node(OpsWorkflowNode.builder().config(Map.of("changePackageStatusEnabled", true)).build()).build();
        assertThrows(SecurityException.class, () -> contributor.contribute(context));
    }
}
