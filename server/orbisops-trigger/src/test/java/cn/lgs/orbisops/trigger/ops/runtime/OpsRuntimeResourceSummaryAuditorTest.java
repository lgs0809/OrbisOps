package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRuntimeResourceSummaryAuditorTest {

    private final OpsRuntimeResourceSummaryAuditor auditor =
            new OpsRuntimeResourceSummaryAuditor();

    @Test
    void summaryMustFinalizeMetadataAndEmitSingleRuntimeResourceEvent() {
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .node(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .type("AGENT")
                        .agent("worker")
                        .build())
                .request(OpsAgentChatRequest.builder().projectId("project-1").build())
                .projectId("project-1")
                .modelId("model-1")
                .mcpIds(new LinkedHashSet<>(List.of("mcp-a", "mcp-b")))
                .mcpServers(new ArrayList<>(List.of(
                        OpsMcpServerConfig.builder().name("server-a").build())))
                .skillNames(new LinkedHashSet<>(List.of("skill-a")))
                .events(events)
                .build();

        auditor.summarize(context);

        assertEquals("NODE:node-1", context.getMetadata().get("owner"));
        assertEquals("project-1", context.getMetadata().get("projectId"));
        assertEquals("model-1", context.getMetadata().get("modelId"));
        assertEquals(List.of("mcp-a", "mcp-b"), context.getMetadata().get("mcpIds"));
        assertEquals(1, context.getMetadata().get("mcpServerCount"));
        assertEquals(List.of("skill-a"), context.getMetadata().get("skillNames"));
        assertEquals(1, events.size());
        OpsRuntimeEvent event = events.get(0);
        assertEquals("RUNTIME_RESOURCES", event.getEventType());
        assertEquals("SUCCEEDED", event.getStatus());
        assertTrue(event.getSummary().contains("NODE:node-1"));
        assertSame(context.getMetadata(), event.getPayload());
    }

    @Test
    void nullProjectAndModelMustUseEmptyCompatibilityValues() {
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .request(OpsAgentChatRequest.builder().build())
                .events(new ArrayList<>())
                .build();

        auditor.summarize(context);

        assertEquals("", context.getMetadata().get("projectId"));
        assertEquals("", context.getMetadata().get("modelId"));
    }
}
