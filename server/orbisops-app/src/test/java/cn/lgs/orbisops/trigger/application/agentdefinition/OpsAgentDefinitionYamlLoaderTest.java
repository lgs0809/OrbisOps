package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionYamlLoaderTest {

    @Test
    void parsesYamlIntoCompleteManualWorkflowDefinition() {
        OpsAgentDefinition definition = new OpsAgentDefinitionYamlLoader().parse(resource("""
                agent:
                  agentId: yaml-agent
                  version: 3
                  lifecycle: VALIDATED
                  name: YAML Agent
                  projectId: project-a
                  definitionKind: SPECIALIZED_WORKFLOW
                  workflowInvocationMode: MANUAL_ONLY
                  workflowAutoSelectEnabled: false
                  whenToUse: [订单失败排查]
                  whenNotToUse: [知识问答]
                  routingKeywords: [订单失败, 下单异常]
                  modelId: model-a
                  skills: diagnosis
                  mcpIds: [query-tool]
                  executionTargetIds: [sandbox-a]
                  mcpServers:
                    - name: inline-query
                      transport: http
                      url: http://localhost/mcp
                      allowedTools: [query]
                  agentscopeAgents:
                    - id: specialist
                      instruction: inspect evidence
                      maxDepth: 2
                      allowedToolNames: [query]
                  graph:
                    start: start
                    nodes:
                      - id: start
                        type: START
                      - nodeId: diagnose
                        config:
                          mode: react
                        skills: [node-skill]
                    edges:
                      - id: next
                        from: start
                        to: diagnose
                    loops:
                      - id: investigate-loop
                        nodes: [diagnose]
                        maxRounds: 4
                """));

        assertEquals("yaml-agent", definition.getAgentId());
        assertEquals(3, definition.getVersion());
        assertEquals("VALIDATED", definition.getLifecycle());
        assertEquals("project-a", definition.getProjectId());
        assertEquals("SPECIALIZED_WORKFLOW", definition.getDefinitionKind());
        assertEquals("MANUAL_ONLY", definition.getWorkflowInvocationMode());
        assertEquals(false, definition.getWorkflowAutoSelectEnabled());
        assertEquals(List.of("订单失败排查"), definition.getWhenToUse());
        assertEquals(List.of("知识问答"), definition.getWhenNotToUse());
        assertEquals(List.of("订单失败", "下单异常"), definition.getRoutingKeywords());
        assertEquals("diagnosis", definition.getSkills().get(0));
        assertEquals("inline-query", definition.getMcpServers().get(0).getName());
        assertEquals("specialist", definition.getAgentscopeAgents().get(0).getAgentId());
        assertEquals("react", definition.getNodes().get(1).getMode());
        assertEquals("always", definition.getEdges().get(0).getCondition());
        assertEquals("investigate-loop", definition.getLoops().get(0).getLoopId());
    }

    @Test
    void discoversCommaSeparatedLocationsAndIgnoresInvalidDocuments() throws Exception {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources("classpath*:one.yml"))
                .thenReturn(new Resource[]{resource("agent:\n  agentId: one\n")});
        when(resolver.getResources("classpath*:two.yml"))
                .thenReturn(new Resource[]{resource("- not-a-map\n")});
        OpsAgentDefinitionYamlLoader loader = new OpsAgentDefinitionYamlLoader(resolver);

        List<OpsAgentDefinition> definitions = loader.load(
                " classpath*:one.yml, ,classpath*:two.yml ");

        assertEquals(1, definitions.size());
        assertEquals("one", definitions.get(0).getAgentId());
        verify(resolver).getResources("classpath*:one.yml");
        verify(resolver).getResources("classpath*:two.yml");
        assertTrue(loader.load(" ").isEmpty());
        assertNull(loader.parse(resource("[]")));
    }

    private Resource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "agent.yml";
            }
        };
    }
}
