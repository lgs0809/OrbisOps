package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Builds and executes one Graph-scoped AgentScope/ReAct node. */
final class OpsGraphAgentScopeNodeExecutor {

    private final OpsAgentScopeExecutionCoordinator agentScopeExecutionCoordinator;
    private final OpsRuntimePromptAssembler promptAssembler;
    private final OpsAnalysisRoutingPolicy analysisRoutingPolicy;
    private final OpsGraphRuntimeStateManager graphRuntimeStateManager;
    private final OpsGraphNodePromptContextPolicy promptContextPolicy;

    OpsGraphAgentScopeNodeExecutor(
            OpsAgentScopeExecutionCoordinator agentScopeExecutionCoordinator,
            OpsRuntimePromptAssembler promptAssembler,
            OpsAnalysisRoutingPolicy analysisRoutingPolicy,
            OpsGraphRuntimeStateManager graphRuntimeStateManager,
            OpsGraphNodePromptContextPolicy promptContextPolicy) {
        this.agentScopeExecutionCoordinator = agentScopeExecutionCoordinator;
        this.promptAssembler = promptAssembler;
        this.analysisRoutingPolicy = analysisRoutingPolicy;
        this.graphRuntimeStateManager = graphRuntimeStateManager;
        this.promptContextPolicy = promptContextPolicy;
    }

    OpsGraphNodeExecutionResult execute(OpsGraphNodeExecutionContext context) {
        context.hooks().assertNotCanceled(context.request());
        OpsAgentDefinition nodeDefinition = OpsAgentDefinition.builder()
                .agentId(context.definition().getAgentId() + "-" + context.node().getNodeId())
                .name(firstText(context.node().getAgent(), context.node().getNodeId()))
                .projectId(context.definition().getProjectId())
                .engine("AGENTSCOPE")
                .instruction(promptAssembler.globalInstructionForNode(
                        context.definition(), context.node()))
                .modelId(firstText(
                        context.node().getModelId(), context.definition().getModelId()))
                .ragEnabled(firstNonNull(
                        context.node().getRagEnabled(), context.definition().getRagEnabled()))
                .knowledgeBaseId(firstText(
                        context.node().getKnowledgeBaseId(), context.definition().getKnowledgeBaseId()))
                .changePackageEnabled(firstNonNull(
                        context.node().getChangePackageEnabled(),
                        context.definition().getChangePackageEnabled()))
                .mcpServers(context.node().getMcpServers())
                .mcpIds(context.node().getMcpIds())
                .skills(mergeStrings(
                        context.definition().getSkills(), context.node().getSkills()))
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder()
                        .agentId(context.node().getAgent())
                        .name(firstText(context.node().getAgent(), context.node().getNodeId()))
                        .instruction(context.node().getInstruction())
                        .outputContract(OpsRuntimePromptAssembler.nodeOutputContract(context.node()))
                        .modelId(firstText(
                                context.node().getModelId(), context.definition().getModelId()))
                        .outputKey(StringUtils.hasText(context.node().getOutputKey())
                                ? context.node().getOutputKey()
                                : "agent_0")
                        .ragEnabled(firstNonNull(
                                context.node().getRagEnabled(), context.definition().getRagEnabled()))
                        .knowledgeBaseId(firstText(
                                context.node().getKnowledgeBaseId(),
                                context.definition().getKnowledgeBaseId()))
                        .role(configText(context.node(), "role"))
                        .maxDepth(intConfig(context.node(), "maxDepth", 1))
                        .allowedToolNames(stringConfigList(context.node(), "allowedToolNames"))
                        .inheritProjectCapabilities(Boolean.parseBoolean(firstText(
                                configText(context.node(), "inheritProjectCapabilities"), "false")))
                        .changePackageStatusEnabled(context.node().getConfig() != null && Boolean.TRUE.equals(context.node().getConfig().get("changePackageStatusEnabled")))
                .repairEnabled(Boolean.TRUE.equals(context.node().getRepairEnabled()))
                        .changePackageEnabled(firstNonNull(
                                context.node().getChangePackageEnabled(),
                                context.definition().getChangePackageEnabled()))
                        .mcpServers(context.node().getMcpServers())
                        .mcpIds(context.node().getMcpIds())
                        .skills(context.node().getSkills())
                        .build()))
                .build();
        String scopedInput = buildInput(
                context.definition(),
                context.node(),
                context.request(),
                context.input(),
                context.state(),
                context.hooks());
        String output = agentScopeExecutionCoordinator.execute(
                nodeDefinition,
                context.request(),
                scopedInput,
                context.events(),
                context.eventSink());
        Map<String, Object> result = context.analysisState() != null
                ? recordAnalysisObservation(
                        context.definition(), context.node(), context.analysisState(), output)
                : createObservationResult(context.definition(), context.node(), output);
        graphRuntimeStateManager.markAgentScopeInvestigationCompleted(
                context.request(), result, analysisRoutingPolicy);
        if (!agentScopeExecutionCoordinator.isMeaningfulText(output)) {
            throw new IllegalStateException("ReAct 节点未生成有效输出");
        }
        return new OpsGraphNodeExecutionResult(output, result);
    }

    String buildInput(OpsAgentDefinition definition,
                      OpsWorkflowNode node,
                      OpsAgentChatRequest request,
                      String input,
                      OverAllState state,
                      OpsGraphNodeExecutionCoordinator.Hooks hooks) {
        return promptAssembler.buildNodePrompt(
                definition,
                node,
                request == null ? input : request.getQuery(),
                input,
                state,
                promptContextPolicy.promptPolicy(hooks));
    }

    Map<String, Object> createObservationResult(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            String output) {
        Map<String, Object> result = new LinkedHashMap<>();
        String source = analysisRoutingPolicy.resolveSource(definition, node, "AGENTSCOPE");
        if (!StringUtils.hasText(source)) {
            source = analysisRoutingPolicy.normalizeSource(node.getNodeId());
        }
        String agent = firstText(node.getAgent(), node.getNodeId(), source + "-agent");
        boolean hasOutput = agentScopeExecutionCoordinator.isMeaningfulText(output);
        OpsAnalysisResponseDTO.InvestigationResultDTO observation =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(source)
                        .agent(agent)
                        .status(hasOutput ? "FOUND" : "INSUFFICIENT")
                        .summary(hasOutput
                                ? OpsMemoryTextUtils.abbreviate(output, 800)
                                : "ReAct 工具节点未返回有效 observation。")
                        .evidence(hasOutput
                                ? List.of(OpsMemoryTextUtils.abbreviate(output, 4000))
                                : new ArrayList<>())
                        .attempts(List.of(OpsAnalysisResponseDTO.InvestigationAttemptDTO.builder()
                                .query("REACT node=" + value(node.getNodeId()))
                                .resultCount(hasOutput ? 1 : 0)
                                .reason("按画布节点 Prompt、Skill、MCP、RAG 配置执行 ReAct 子 Agent。")
                                .build()))
                        .gaps(hasOutput
                                ? new ArrayList<>()
                                : List.of("当前节点没有可汇总的输出。"))
                        .suggestedAdjustments(new ArrayList<>())
                        .shouldRetry(!hasOutput)
                        .confidence(hasOutput ? 0.72D : 0.15D)
                        .build();
        result.put("latestObservation", observation);
        result.put("results", List.of(observation));
        return result;
    }

    private Map<String, Object> recordAnalysisObservation(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            OpsAnalysisRuntimeStateManager.State analysisState,
            String output) {
        Map<String, Object> result = createObservationResult(definition, node, output);
        OpsAnalysisResponseDTO.InvestigationResultDTO observation =
                (OpsAnalysisResponseDTO.InvestigationResultDTO) result.get("latestObservation");
        synchronized (analysisState.initialResults()) {
            analysisState.initialResults().add(observation);
            analysisState.response().setInvestigationResults(
                    new ArrayList<>(analysisState.initialResults()));
        }
        analysisState.resultRef().set(new ArrayList<>(analysisState.initialResults()));
        result.put("results", new ArrayList<>(analysisState.initialResults()));
        result.put("response", analysisState.response());
        return result;
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null) {
            return "";
        }
        Object configured = node.getConfig().get(key);
        return configured == null ? "" : String.valueOf(configured);
    }

    private int intConfig(OpsWorkflowNode node, String key, int fallback) {
        if (node == null || node.getConfig() == null) {
            return fallback;
        }
        Object configured = node.getConfig().get(key);
        if (configured instanceof Number number) {
            return number.intValue();
        }
        if (configured == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(configured).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("节点配置 " + key + " 必须是整数", error);
        }
    }

    private List<String> stringConfigList(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null) {
            return List.of();
        }
        Object configured = node.getConfig().get(key);
        if (configured instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            }
            return List.copyOf(values);
        }
        return configured != null && StringUtils.hasText(String.valueOf(configured))
                ? List.of(String.valueOf(configured).trim())
                : List.of();
    }

    @SafeVarargs
    private final List<String> mergeStrings(List<String>... lists) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (List<String> list : lists) {
            if (list != null) {
                result.addAll(list);
            }
        }
        return new ArrayList<>(result);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
