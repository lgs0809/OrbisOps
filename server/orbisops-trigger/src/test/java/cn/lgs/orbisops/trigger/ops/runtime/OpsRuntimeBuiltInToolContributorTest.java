package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceDTO;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageToolProvider;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairToolProvider;
import cn.lgs.orbisops.trigger.ops.source.OpsProjectServiceCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeBuiltInToolContributorTest {

    @Test
    void repairToolsMustRequireEnabledProjectServiceCatalog() {
        OpsRepairToolProvider repairProvider = mock(OpsRepairToolProvider.class);
        OpsProjectServiceCatalogService serviceCatalog =
                mock(OpsProjectServiceCatalogService.class);
        ToolCallback repairTool = tool("code_read");
        when(serviceCatalog.capabilities()).thenReturn(Map.of("enabled", true));
        when(serviceCatalog.list("project-1"))
                .thenReturn(List.of(mock(OpsProjectServiceDTO.class)));
        when(repairProvider.buildCodeTools(
                eq("project-1"), eq("alice"), any(OpsAgentChatRequest.class)))
                .thenReturn(List.of(repairTool));
        OpsRuntimeBuiltInToolContributor contributor = contributor(
                repairProvider, serviceCatalog, null, null, null);
        OpsRuntimeResourceContext context = context("alice", "run-1");
        context.setRepairEnabled(true);

        contributor.contribute(context);

        assertEquals(1, context.getTools().size());
        assertSame(repairTool, ((OpsRuntimeGovernedToolCallback) context.getTools().get(0)).delegate());
        assertEquals(true, context.getMetadata().get("repairCandidateToolEnabled"));
        assertEquals(true, context.getMetadata().get("controlledCodeToolsEnabled"));
    }

    @Test
    void mainInvestigationMustNotExposeChangePackageTool() {
        OpsChangePackageToolProvider changeProvider =
                mock(OpsChangePackageToolProvider.class);
        OpsRuntimeBuiltInToolContributor contributor = contributor(
                null, null, changeProvider, null, null);
        OpsRuntimeResourceContext context = context("alice", "run-1");
        context.setChangePackageEnabled(true);
        context.setExecutionTargetIds(new java.util.LinkedHashSet<>(List.of("target-1")));

        contributor.contribute(context);

        assertTrue(context.getTools().isEmpty());
        assertTrue(!context.getMetadata().containsKey("changePackageToolEnabled"));
    }

    @Test
    void unavailableExecutionTargetsMustWarnAndSuppressChangePackageTool() {
        OpsChangePackageToolProvider changeProvider =
                mock(OpsChangePackageToolProvider.class);
        when(changeProvider.available("project-1", List.of("target-1")))
                .thenReturn(false);
        OpsRuntimeBuiltInToolContributor contributor = contributor(
                null, null, changeProvider, null, null);
        OpsRuntimeResourceContext context = context("alice", "run-1");
        context.getRequest().getMetadata().put(
                OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY, true);
        context.setChangePackageEnabled(true);
        context.setExecutionTargetIds(new java.util.LinkedHashSet<>(List.of("target-1")));

        contributor.contribute(context);

        assertTrue(context.getTools().isEmpty());
        assertTrue(context.getEvents().stream().anyMatch(event ->
                "RESOURCE_WARN".equals(event.getEventType())
                        && event.getSummary().contains("绑定目标不可用")));
    }

    @Test
    void changePackageToolMustUseFrozenRunActorAndContextBundleIdentity() {
        OpsChangePackageToolProvider changeProvider =
                mock(OpsChangePackageToolProvider.class);
        ToolCallback tool = tool("PrepareChangePackage");
        ToolCallback statusQuery = tool("QueryChangePackageStatus");
        when(changeProvider.buildStatusQuery(eq("project-1"), eq("alice"), eq("run-1"), any(OpsAgentChatRequest.class)))
                .thenReturn(statusQuery);
        when(changeProvider.available("project-1", List.of("target-1")))
                .thenReturn(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        when(changeProvider.build(
                eq("project-1"),
                eq("alice"),
                eq("run-1"),
                eq("bundle-1"),
                eq("hash-1"),
                same(events),
                eq(List.of("target-1")),
                any(OpsAgentChatRequest.class)))
                .thenReturn(tool);
        OpsAgentRunRequestDTO runRequest = OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .requestedBy("alice")
                .build();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST, runRequest);
        metadata.put(OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY, true);
        metadata.put("contextBundleId", "bundle-1");
        metadata.put("contextBundleHash", "hash-1");
        OpsRuntimeResourceContext context = context("", "", metadata, events);
        context.setChangePackageEnabled(true);
        context.setExecutionTargetIds(new java.util.LinkedHashSet<>(List.of("target-1")));
        OpsRuntimeBuiltInToolContributor contributor = contributor(
                null, null, changeProvider, null, null);

        contributor.contribute(context);

        assertSame(tool, ((OpsRuntimeGovernedToolCallback) context.getTools().get(0)).delegate());
        assertEquals(2, context.getTools().size());
        assertSame(statusQuery, ((OpsRuntimeGovernedToolCallback) context.getTools().get(1)).delegate());
        assertEquals("run-1", context.getMetadata().get("changePackageRunId"));
        assertEquals(List.of("target-1"), context.getMetadata().get("executionTargetIds"));
    }

    @Test
    void inspectionAndChannelToolsMustUseCurrentRuntimeIdentityInOrder() {
        OpsInspectionTaskToolProvider inspectionProvider =
                mock(OpsInspectionTaskToolProvider.class);
        OpsChannelToolProvider channelProvider = mock(OpsChannelToolProvider.class);
        ToolCallback inspectionTool = tool("ManageInspectionTask");
        ToolCallback channelTool = tool("NotifyChannel");
        when(inspectionProvider.build("project-1", "alice", "run-1", "agent-1"))
                .thenReturn(inspectionTool);
        when(channelProvider.available("project-1")).thenReturn(true);
        when(channelProvider.build("project-1", "alice", "run-1"))
                .thenReturn(channelTool);
        OpsRuntimeBuiltInToolContributor contributor = contributor(
                null, null, null, inspectionProvider, channelProvider);
        OpsRuntimeResourceContext context = context("alice", "run-1");

        contributor.contribute(context);

        assertEquals(2, context.getTools().size());
        assertSame(inspectionTool, ((OpsRuntimeGovernedToolCallback) context.getTools().get(0)).delegate());
        assertSame(channelTool, ((OpsRuntimeGovernedToolCallback) context.getTools().get(1)).delegate());
        assertEquals(true, context.getMetadata().get("inspectionTaskToolEnabled"));
        assertEquals("agent-1", context.getMetadata().get("inspectionTaskDefaultAgentId"));
        assertEquals(true, context.getMetadata().get("channelToolEnabled"));
        verify(channelProvider).available("project-1");
    }

    private OpsRuntimeBuiltInToolContributor contributor(
            OpsRepairToolProvider repairProvider,
            OpsProjectServiceCatalogService serviceCatalog,
            OpsChangePackageToolProvider changeProvider,
            OpsInspectionTaskToolProvider inspectionProvider,
            OpsChannelToolProvider channelProvider) {
        return new OpsRuntimeBuiltInToolContributor(new OpsRuntimeToolContributorRegistry(List.of(
                new OpsRepairRuntimeToolContributor(() -> repairProvider, () -> serviceCatalog),
                new OpsChangePackageRuntimeToolContributor(() -> changeProvider),
                new OpsInspectionTaskRuntimeToolContributor(() -> inspectionProvider, projectId -> "agent-1"),
                new OpsChannelRuntimeToolContributor(() -> channelProvider))));
    }

    private ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(definition.description()).thenReturn(name + " test tool");
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }

    private OpsRuntimeResourceContext context(String actor, String runId) {
        return context(actor, runId, new LinkedHashMap<>(), new ArrayList<>());
    }

    private OpsRuntimeResourceContext context(
            String actor,
            String runId,
            Map<String, Object> metadata,
            List<OpsRuntimeEvent> events) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .request(OpsAgentChatRequest.builder()
                        .projectId("project-1")
                        .userId(actor)
                        .runId(runId)
                        .metadata(metadata)
                        .build())
                .projectId("project-1")
                .events(events)
                .build();
    }
}
