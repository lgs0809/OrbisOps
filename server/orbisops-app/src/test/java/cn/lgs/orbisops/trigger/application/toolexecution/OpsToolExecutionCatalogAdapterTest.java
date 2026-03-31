package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import cn.lgs.orbisops.trigger.ops.toolset.BusinessOperationsToolsetContributor;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetCatalogService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsToolExecutionCatalogAdapterTest {

    @Test
    void shouldResolveLegacyCatalogAndRouterDecisionToTypedResolution() {
        OpsToolsetCatalogService catalog = mock(OpsToolsetCatalogService.class);
        OpsToolsetRouter router = mock(OpsToolsetRouter.class);
        OpsToolDefinition tool = OpsToolDefinition.builder()
                .toolName("code_bash")
                .adapterType("CODE_REPAIR")
                .riskLevel("MEDIUM")
                .writesRepairWorkspace(true)
                .enabled(true)
                .build();
        OpsToolsetDefinition toolset = OpsToolsetDefinition.builder()
                .toolsetId("code.repair")
                .adapterType("CODE_REPAIR")
                .enabled(true)
                .tools(List.of(tool))
                .build();
        when(catalog.listEffectiveToolsets("project-1", "alice")).thenReturn(List.of(toolset));
        when(router.decide(eq(toolset), eq(tool), eq(OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW), any()))
                .thenReturn(Map.of("allowed", true, "decision", "ALLOWED", "riskLevel", "MEDIUM"));

        ToolExecutionResolution resolution = new OpsToolExecutionCatalogAdapter(catalog, router)
                .resolve(request("code.repair", "code_bash"));

        assertEquals("CODE_REPAIR", resolution.target().adapterType());
        assertEquals("MEDIUM", resolution.target().riskLevel());
        assertEquals(true, resolution.decision().allowed());
    }

    @Test
    void shouldUseTrustedBusinessDefinitionWhileBindingConcreteMcpProvider() {
        OpsToolsetCatalogService catalog = mock(OpsToolsetCatalogService.class);
        OpsToolsetRouter router = mock(OpsToolsetRouter.class);
        OpsToolsetDefinition toolset = new BusinessOperationsToolsetContributor()
                .definitions().get(0);
        OpsToolDefinition tool = toolset.getTools().get(0);
        when(catalog.listEffectiveToolsets("project-1", "alice"))
                .thenReturn(List.of(toolset));
        when(router.decide(eq(toolset), any(), eq(OpsToolExecutionScope.APPROVED_LANDING), any()))
                .thenReturn(Map.of("allowed", true, "decision", "ALLOWED", "riskLevel", "HIGH"));
        ToolExecutionRequest request = new ToolExecutionRequest(
                "project-1", "alice", "alice",
                UpdateAlertThresholdPolicy.TOOLSET_ID,
                UpdateAlertThresholdPolicy.TOOL_NAME,
                ToolExecutionScope.APPROVED_LANDING,
                Map.of(), "session-1", "run-1",
                Map.of(OpsMcpToolExecutionBinding.CONTEXT_KEY,
                        new OpsMcpToolExecutionBinding("ops-prod", "update_alert_threshold")),
                Map.of("landingApproved", true));

        ToolExecutionResolution resolution =
                new OpsToolExecutionCatalogAdapter(catalog, router).resolve(request);

        assertEquals("ops-prod", resolution.target().providerDescriptor().mcpServerId());
        assertEquals("update_alert_threshold",
                resolution.target().providerDescriptor().remoteToolName());
        assertEquals(true, resolution.target().semantics().idempotent());
        assertEquals(true, resolution.target().semantics().retrySafe());
        assertEquals(UpdateAlertThresholdPolicy.OUTPUT_SCHEMA.trim(),
                resolution.target().schema().outputSchemaJson().trim());
        assertEquals(tool.getSemantics(), resolution.target().semantics());
    }

    @Test
    void shouldPreserveTrustedReadonlyHintForMcpCompatibilityFallback() {
        OpsToolsetCatalogService catalog = mock(OpsToolsetCatalogService.class);
        OpsToolsetRouter router = mock(OpsToolsetRouter.class);
        when(catalog.listEffectiveToolsets("project-1", "alice")).thenReturn(List.of());
        when(router.decide(any(), any(), eq(OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW), any()))
                .thenReturn(Map.of("allowed", true, "decision", "ALLOWED", "riskLevel", "LOW"));
        ToolExecutionRequest request = new ToolExecutionRequest(
                "project-1", "alice", "alice",
                "mcp.ops-read", "list_operations",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of(), "session-1", "run-1",
                Map.of(OpsMcpToolExecutionBinding.CONTEXT_KEY,
                        new OpsMcpToolExecutionBinding("ops-read", "list_operations", true)),
                Map.of());

        ToolExecutionResolution resolution =
                new OpsToolExecutionCatalogAdapter(catalog, router).resolve(request);

        assertTrue(resolution.target().readOnly());
        assertTrue(resolution.target().semantics().readOnly());
        assertTrue(resolution.target().semantics().idempotent());
        assertTrue(resolution.target().semantics().retrySafe());
    }

    @Test
    void shouldFailClosedWhenToolIsNotInEffectiveCatalog() {
        OpsToolsetCatalogService catalog = mock(OpsToolsetCatalogService.class);
        when(catalog.listEffectiveToolsets("project-1", "alice")).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new OpsToolExecutionCatalogAdapter(catalog, mock(OpsToolsetRouter.class))
                        .resolve(request("missing", "missing")));
    }

    private ToolExecutionRequest request(String toolsetId, String toolName) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", toolsetId, toolName,
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of(),
                "session-1", "run-1", Map.of(), Map.of());
    }
}
