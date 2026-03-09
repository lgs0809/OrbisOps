package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRuntimePromptAssemblerTest {

    private final OpsRuntimePromptAssembler assembler = new OpsRuntimePromptAssembler();
    private final OpsRuntimePromptAssembler.ContextPolicy policy = new OpsRuntimePromptAssembler.ContextPolicy() {
        @Override
        public String executionNodeType(OpsWorkflowNode node) {
            return node == null || node.getType() == null ? "LLM" : node.getType().toUpperCase();
        }

        @Override
        public String incomingRouteKey(OpsAgentDefinition definition, OpsWorkflowNode node) {
            return "route-a";
        }

        @Override
        public String analysisAgentRole(OpsWorkflowNode node) {
            return node != null && node.getConfig() != null
                    ? String.valueOf(node.getConfig().getOrDefault("analysisRole", "")) : "";
        }

        @Override
        public boolean edgeActive(OpsGraphEdge edge, OverAllState state) {
            return true;
        }

        @Override
        public Map<String, Object> edgeRuntimeMetadata(OpsAgentDefinition definition,
                                                       OverAllState state,
                                                       OpsGraphEdge edge) {
            return Map.of();
        }
    };

    @Test
    void legacyRuntimeNoLongerOwnsPromptAssemblyMethods() {
        Set<String> migratedMethods = Set.of(
                "systemPrompt", "agentInstruction", "composeRuntimeInstruction",
                "buildNodePrompt", "selectedContextInputSection", "contextInputKeys",
                "resolveContextInput", "formatContextInputValue");

        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .anyMatch(migratedMethods::contains));
    }

    @Test
    void systemPromptComposesGlobalLocalAndSkillBoundaries() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .instruction("global evidence rules")
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .instruction("local diagnosis rules")
                .build();
        OpsRuntimeResourceBundle bundle = OpsRuntimeResourceBundle.builder()
                .skillContext("slow-sql: read only")
                .build();

        String prompt = assembler.systemPrompt(definition, node, bundle);

        assertTrue(prompt.contains("### 全局 System Prompt"));
        assertTrue(prompt.contains("global evidence rules"));
        assertTrue(prompt.contains("### 节点 System Prompt"));
        assertTrue(prompt.contains("local diagnosis rules"));
        assertTrue(prompt.contains("### 可用 Skill 与使用边界"));
        assertTrue(prompt.contains("slow-sql: read only"));
    }

    @Test
    void nodeOnlyModeDoesNotLeakGlobalInstruction() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .instruction("global instruction")
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .instruction("isolated instruction")
                .config(Map.of("globalPromptMode", "node_only"))
                .build();

        String prompt = assembler.systemPrompt(definition, node, null);

        assertTrue(prompt.contains("isolated instruction"));
        assertFalse(prompt.contains("global instruction"));
    }

    @Test
    void agentInstructionMustEncodeAuthorityPreflightAndTypedRequiresActionBoundary() {
        String prompt = assembler.agentInstruction(
                OpsAgentDefinition.builder().instruction("operate safely").build(),
                OpsAgentScopeConfig.builder().instruction("diagnose precisely").build(),
                null);

        assertTrue(prompt.contains("authority preflight"));
        assertTrue(prompt.contains("不要调用 Skill、RAG、Prometheus、Elasticsearch、MySQL"));
        assertTrue(prompt.contains("actionClassification=ABSTAIN"));
        assertTrue(prompt.contains("普通只读查询或诊断"));
        assertTrue(prompt.contains("创建/归档/关联 Incident"));
        assertTrue(prompt.contains("即使当前实时证据是 PARTIAL/INSUFFICIENT"));
        assertTrue(prompt.contains("requiresAction=true|false"));
        assertTrue(prompt.contains("REMEDIATE=用户明确要求 Incident 归档/关联、ChangePackage、受控修复、回滚"));
        assertTrue(prompt.contains("当 actionClassification=REMEDIATE 时必须 requiresAction=true"));
        assertFalse(prompt.contains("\nrequiresAction=false\n"));
        assertTrue(prompt.contains("不得在零次实时事实查询的情况下仅凭发现结果直接输出 INSUFFICIENT"));
        assertTrue(prompt.contains("正式 Incident/Remediation/ChangePackage 的证据门槛高于普通问答"));
        assertTrue(prompt.contains("不能把 OpenAPI/Tool Catalog 当作第二份故障证据"));
    }

    @Test
    void agentInstructionRequiresOpenApiResolutionBeforeEndpointSpecificQueries() {
        OpsRuntimeResourceBundle bundle = OpsRuntimeResourceBundle.builder()
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("project-openapi")
                        .mcpId("project-openapi")
                        .allowedTools(List.of("openapi_list_operations"))
                        .build()))
                .build();

        String prompt = assembler.agentInstruction(
                OpsAgentDefinition.builder().instruction("operate safely").build(),
                OpsAgentScopeConfig.builder().instruction("diagnose precisely").build(),
                bundle);

        assertTrue(prompt.contains("业务资源身份解析约束"));
        assertTrue(prompt.contains("必须先使用该项目 OpenAPI MCP"));
        assertTrue(prompt.contains("不得根据业务词自行猜测、拼接、模糊匹配或正则扩展 URI/API path"));
    }

    @Test
    void agentInstructionDoesNotInventOpenApiResolutionContractWhenUnavailable() {
        String prompt = assembler.agentInstruction(
                OpsAgentDefinition.builder().instruction("operate safely").build(),
                OpsAgentScopeConfig.builder().instruction("diagnose precisely").build(),
                OpsRuntimeResourceBundle.builder().mcpServers(List.of()).build());

        assertFalse(prompt.contains("业务资源身份解析约束"));
    }

    @Test
    void explicitContextInputsProjectOnlyConfiguredState() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("diagnose")
                .type("LLM")
                .agent("diagnosis-agent")
                .description("diagnose current evidence")
                .mcpIds(List.of("logs-mcp"))
                .config(Map.of("contextInputs", List.of(
                        "query", "state.results", "state.nested.answer", "upstreamOutputs")))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .nodes(List.of(node))
                .edges(List.of())
                .build();
        OverAllState state = new OverAllState(Map.of(
                "query", "why 5xx",
                "results", List.of(Map.of("source", "logs", "value", "timeout")),
                "nested", Map.of("answer", "database saturation"),
                "hidden", "must-not-leak"));

        String prompt = assembler.buildNodePrompt(
                definition, node, "original question", "previous output", state, policy);

        assertTrue(prompt.contains("why 5xx"));
        assertTrue(prompt.contains("timeout"));
        assertTrue(prompt.contains("database saturation"));
        assertTrue(prompt.contains("previous output"));
        assertTrue(prompt.contains("nodeId: diagnose"));
        assertTrue(prompt.contains("routeKey: route-a"));
        assertFalse(prompt.contains("must-not-leak"));
    }

    @Test
    void reportNodesReceiveResultsByDefault() {
        OpsWorkflowNode report = OpsWorkflowNode.builder()
                .nodeId("final-report")
                .type("REPORT")
                .agent("reporter")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .nodes(List.of(report))
                .edges(List.of())
                .build();
        OverAllState state = new OverAllState(Map.of(
                "query", "summarize",
                "results", List.of("log evidence", "metric evidence")));

        String prompt = assembler.buildNodePrompt(
                definition, report, "summarize", "upstream summary", state, policy);

        assertTrue(prompt.contains("log evidence"));
        assertTrue(prompt.contains("metric evidence"));
        assertTrue(prompt.contains("upstream summary"));
    }
}
