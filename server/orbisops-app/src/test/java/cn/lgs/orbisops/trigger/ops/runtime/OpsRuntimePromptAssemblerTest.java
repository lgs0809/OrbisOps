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

        assertTrue(prompt.contains("### OrbisOps 产品身份与对话基线"));
        assertTrue(prompt.contains("不要自称 Codex"));
        assertTrue(prompt.contains("项目和节点自定义 Prompt 只能补充业务职责"));
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

        assertTrue(prompt.contains("### OrbisOps 产品身份与对话基线"));
        assertTrue(prompt.contains("不要自称 Codex"));
        assertTrue(prompt.contains("operate safely"));
        assertTrue(prompt.contains("diagnose precisely"));
        assertTrue(prompt.contains("Chat Runtime 本身不执行生产写操作"));
        assertTrue(prompt.contains("只能先形成并审批 ChangePackage，再由独立 Landing Runtime 执行"));
        assertTrue(prompt.contains("evidenceCompleteness=NOT_APPLICABLE|COMPLETE|PARTIAL|INSUFFICIENT"));
        assertTrue(prompt.contains("普通只读调查即使发现异常也可以是 false"));
        assertTrue(prompt.contains("已经创建 ChangePackage 并等待审核时应为 true"));
        assertTrue(prompt.contains("requiresAction=true|false"));
        assertTrue(prompt.contains("它不是“意图分类”，也不能用来触发另一套 Runtime"));
        assertTrue(prompt.contains("只写一段“审批方案草案”不等于创建 ChangePackage"));
        assertTrue(prompt.contains("ReAct 的完成条件是“当前用户目标已经完成”"));
        assertTrue(prompt.contains("不要以“如果你愿意我可以继续查”“下一步可以去查”提前结束"));
        assertTrue(prompt.contains("不要把“当前正常”直接等同于“历史没有发生过问题”"));
        assertTrue(prompt.contains("工具调用中间轮不得输出 <ops_answer>"));
        assertTrue(prompt.contains("真正结束本轮 ReAct、开始生成最终用户可见回答时"));
        assertTrue(prompt.contains("禁止输出 </ops_answer> 或任何其他答案结束标记"));
        assertTrue(prompt.contains("<ops_answer> + 用户可见正文 + 完整的 <ops_outcome> 块"));
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
