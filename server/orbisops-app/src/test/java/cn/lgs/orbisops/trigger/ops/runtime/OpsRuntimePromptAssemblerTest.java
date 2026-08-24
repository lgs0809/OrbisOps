package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRuntimePromptAssemblerTest {

    private final OpsRuntimePromptAssembler assembler = new OpsRuntimePromptAssembler();

    @Test
    void llmAndReactReceiveTheSameInternalContractDespiteNaturalLanguageDisplayInstructions() {
        Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("assessment"), "properties", Map.of("assessment", Map.of("type", "string")));
        Map<String, Object> contract = Map.of("format", "JSON", "schema", schema);
        var definition = OpsAgentDefinition.builder().instruction("global rules").build();
        var node = OpsWorkflowNode.builder().type("LLM").instruction("请直接用中文自然语言展示结论")
                .config(Map.of("globalPromptMode", "node_only", "outputContract", contract)).build();
        var agent = OpsAgentScopeConfig.builder().instruction("请直接用中文自然语言展示结论")
                .outputContract(contract).build();
        for (String prompt : List.of(assembler.systemPrompt(definition, node, null),
                assembler.agentInstruction(definition, agent, null))) {
            assertTrue(prompt.contains(com.alibaba.fastjson.JSON.toJSONString(schema)));
            assertTrue(prompt.contains("用户无需提供 JSON"));
            assertTrue(prompt.contains("不能覆盖本节点内部输出协议"));
            org.junit.jupiter.api.Assertions.assertEquals(1, prompt.split("JSON Schema：", -1).length - 1);
        }
        assertFalse(assembler.systemPrompt(definition, node, null).contains("global rules"));
    }

    @Test
    void ordinaryNaturalLanguageNodeDoesNotAcquireAnInternalJsonProtocol() {
        var node = OpsWorkflowNode.builder().type("LLM").instruction("直接回答用户")
                .config(Map.of("outputContract", Map.of("format", "TEXT"))).build();
        assertFalse(assembler.systemPrompt(null, node, null).contains("工作流内部输出协议"));
    }

    @Test
    void projectIdentityUsesAuthorizedBundleInsteadOfDefinitionOrDisplayNames() {
        var definition = OpsAgentDefinition.builder().projectId("stale-template-project")
                .name("Human display name").instruction("local rule").build();
        var bundle = OpsRuntimeResourceBundle.builder().projectId("authorized-project").build();
        var node = OpsWorkflowNode.builder().instruction("local rule")
                .config(Map.of("globalPromptMode", "node_only")).build();
        for (String prompt : List.of(assembler.systemPrompt(definition, node, bundle),
                assembler.agentInstruction(definition, null, bundle))) {
            assertTrue(prompt.contains("currentProjectId: \"authorized-project\""));
            assertFalse(prompt.contains("stale-template-project"));
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    prompt.split("currentProjectId:", -1).length - 1);
        }
        assertFalse(assembler.agentInstruction(definition, null, null).contains("currentProjectId:"));
    }

    @Test
    void everyModelEntrypointGetsOneAuthoritativeUtcAndEpochAnchorIncludingIsolatedNodes() {
        Instant now = Instant.parse("2026-09-09T14:45:00Z");
        OpsRuntimePromptAssembler timed = new OpsRuntimePromptAssembler(
                Clock.fixed(now, ZoneId.of("Asia/Shanghai")));
        OpsAgentDefinition definition = OpsAgentDefinition.builder().instruction("private global rule").build();
        OpsWorkflowNode isolated = OpsWorkflowNode.builder().instruction("local rule")
                .config(Map.of("globalPromptMode", "node_only")).build();

        String nodePrompt = timed.systemPrompt(definition, isolated, null);
        String agentPrompt = timed.agentInstruction(definition, null, null);

        for (String prompt : List.of(nodePrompt, agentPrompt)) {
            assertTrue(prompt.contains("currentTimeUtc: " + now));
            assertTrue(prompt.contains("currentEpochSeconds: " + now.getEpochSecond()));
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    prompt.split("currentEpochSeconds:", -1).length - 1);
        }
        assertFalse(nodePrompt.contains("private global rule"));
    }
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
    void declaredEvidenceKeepsCompleteLateSourcesAndTheirConditions() {
        var sources = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> Map.of("sourceId", "source-" + index,
                        "body", "full accepted content ".repeat(35),
                        "condition", index == 20 ? "coverage-gap-is-a-lower-bound-only" : "complete-boundaries"))
                .toList();
        var receipt = Map.of("normalizedContent", Map.of("sources", sources));
        var node = OpsWorkflowNode.builder().nodeId("review").type("LLM").agent("reviewer")
                .config(Map.of("contextInputs", List.of("workflowData_receipt"))).build();
        String prompt = assembler.buildNodePrompt(OpsAgentDefinition.builder().nodes(List.of(node))
                .edges(List.of()).build(), node, "review every source", "", new OverAllState(
                Map.of("workflowData_receipt", receipt)), policy);
        assertTrue(prompt.contains(com.alibaba.fastjson.JSON.toJSONString(receipt)));
        assertTrue(prompt.contains("source-20"));
        assertTrue(prompt.contains("coverage-gap-is-a-lower-bound-only"));
    }

    @Test
    void overBudgetEvidenceFailsBeforeAClippedPromptCanReachAModel() {
        var node = OpsWorkflowNode.builder().nodeId("review").type("LLM").agent("reviewer")
                .config(Map.of("contextInputs", List.of("receipt"))).build();
        var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> assembler.buildNodePrompt(OpsAgentDefinition.builder().nodes(List.of(node))
                        .edges(List.of()).build(), node, "review", "", new OverAllState(
                        Map.of("receipt", "sensitive-evidence".repeat(2000))), policy));
        assertTrue(failure.getMessage().startsWith("NODE_CONTEXT_INPUT_BUDGET_EXCEEDED:"));
        assertFalse(failure.getMessage().contains("sensitive-evidence"));
    }

    @Test
    void aggregateEvidenceBudgetFailsEvenWhenEachSelectedInputFits() {
        var keys = List.of("one", "two", "three", "four", "five");
        var node = OpsWorkflowNode.builder().nodeId("review").type("LLM").agent("reviewer")
                .config(Map.of("contextInputs", keys)).build();
        var state = keys.stream().collect(java.util.stream.Collectors.toMap(key -> key, key -> "x".repeat(30_000)));
        var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> assembler.buildNodePrompt(OpsAgentDefinition.builder().nodes(List.of(node))
                        .edges(List.of()).build(), node, "review", "", new OverAllState(
                        new java.util.HashMap<String, Object>(state)), policy));
        assertTrue(failure.getMessage().startsWith("NODE_CONTEXT_TOTAL_BUDGET_EXCEEDED:"));
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
