package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.NodeAggregationStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/** Executes the top-level Graph engine lifecycle and Spring AI Alibaba StateGraph invocation. */
@Slf4j
final class OpsGraphEngineExecutionCoordinator {

    private final OpsGraphRuntimeStateManager graphRuntimeStateManager;
    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final Supplier<OpsAgentGraphCompilerAdapter> graphCompilerSupplier;
    private final OpsGraphNodeExecutionCoordinator graphNodeExecutionCoordinator;
    private final OpsGraphTopologyAssembler graphTopologyAssembler;
    private final OpsRuntimeConversationContextCoordinator conversationContextCoordinator;
    private final OpsAnalysisRoutingPolicy analysisRoutingPolicy;
    private final OpsRuntimeSkillLearningCoordinator skillLearningCoordinator;
    private final OpsRuntimeEventJournal eventJournal;
    private final OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy;
    private final OpsTypedWorkflowExecutionCoordinator typedWorkflowCoordinator;
    private final Executor subAgentExecutor;

    OpsGraphEngineExecutionCoordinator(
            OpsGraphRuntimeStateManager graphRuntimeStateManager,
            OpsAnalysisRuntimeStateManager analysisStateManager,
            Supplier<OpsAgentGraphCompilerAdapter> graphCompilerSupplier,
            OpsGraphNodeExecutionCoordinator graphNodeExecutionCoordinator,
            OpsGraphTopologyAssembler graphTopologyAssembler,
            OpsRuntimeConversationContextCoordinator conversationContextCoordinator,
            OpsAnalysisRoutingPolicy analysisRoutingPolicy,
            OpsRuntimeSkillLearningCoordinator skillLearningCoordinator,
            OpsRuntimeEventJournal eventJournal,
            OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy,
            Executor subAgentExecutor) {
        this(
                graphRuntimeStateManager,
                analysisStateManager,
                graphCompilerSupplier,
                graphNodeExecutionCoordinator,
                graphTopologyAssembler,
                conversationContextCoordinator,
                analysisRoutingPolicy,
                skillLearningCoordinator,
                eventJournal,
                nodeExecutionPolicy,
                null,
                subAgentExecutor);
    }

    OpsGraphEngineExecutionCoordinator(
            OpsGraphRuntimeStateManager graphRuntimeStateManager,
            OpsAnalysisRuntimeStateManager analysisStateManager,
            Supplier<OpsAgentGraphCompilerAdapter> graphCompilerSupplier,
            OpsGraphNodeExecutionCoordinator graphNodeExecutionCoordinator,
            OpsGraphTopologyAssembler graphTopologyAssembler,
            OpsRuntimeConversationContextCoordinator conversationContextCoordinator,
            OpsAnalysisRoutingPolicy analysisRoutingPolicy,
            OpsRuntimeSkillLearningCoordinator skillLearningCoordinator,
            OpsRuntimeEventJournal eventJournal,
            OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy,
            OpsTypedWorkflowExecutionCoordinator typedWorkflowCoordinator,
            Executor subAgentExecutor) {
        this.graphRuntimeStateManager = graphRuntimeStateManager;
        this.analysisStateManager = analysisStateManager;
        this.graphCompilerSupplier = graphCompilerSupplier;
        this.graphNodeExecutionCoordinator = graphNodeExecutionCoordinator;
        this.graphTopologyAssembler = graphTopologyAssembler;
        this.conversationContextCoordinator = conversationContextCoordinator;
        this.analysisRoutingPolicy = analysisRoutingPolicy;
        this.skillLearningCoordinator = skillLearningCoordinator;
        this.eventJournal = eventJournal;
        this.nodeExecutionPolicy = nodeExecutionPolicy;
        this.typedWorkflowCoordinator = typedWorkflowCoordinator;
        this.subAgentExecutor = subAgentExecutor;
    }

    String execute(OpsAgentDefinition definition,
                   OpsAgentChatRequest request,
                   List<OpsRuntimeEvent> events,
                   Consumer<OpsRuntimeEvent> eventSink,
                   OpsGraphNodeExecutionCoordinator.Hooks nodeHooks) {
        graphRuntimeStateManager.initialize(request);
        OpsAnalysisRuntimeStateManager.State analysisState = analysisStateManager.ensure(definition, request);
        analysisStateManager.publishRunStarted(definition, analysisState);
        try {
            List<OpsWorkflowNode> nodes = graphNodes(definition);
            OpsAgentGraphCompilerAdapter configuredCompiler = graphCompilerSupplier == null
                    ? null
                    : graphCompilerSupplier.get();
            var domainGraph = (configuredCompiler == null
                    ? new OpsAgentGraphCompilerAdapter()
                    : configuredCompiler).compile(definition, nodes);
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("GRAPH_COMPILED")
                    .status("SUCCEEDED")
                    .summary("工作流 Domain 编译校验完成。")
                    .payload(Map.of(
                            "graphId", domainGraph.graphId(),
                            "startNodeId", domainGraph.startNodeId(),
                            "reachableNodeCount", domainGraph.reachableNodeIds().size(),
                            "terminalNodeCount", domainGraph.terminalNodeIds().size(),
                            "loopEdgeCount", domainGraph.loopEdges().size()))
                    .build());
            if (typedWorkflowCoordinator != null) {
                typedWorkflowCoordinator.begin(
                        definition, request, nodes, events, eventSink);
            }

            String graphName = definition.getAgentId()
                    + "_"
                    + UUID.randomUUID().toString().substring(0, 8);
            StateGraph graph = new StateGraph(
                    graphName,
                    graphRuntimeStateManager.keyStrategyFactory());
            java.util.Set<String> completedNodeIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
            for (OpsWorkflowNode node : nodes) {
                graph.addNode(node.getNodeId(), node_async(state -> {
                    try {
                        if (typedWorkflowCoordinator != null) {
                            Optional<Map<String, Object>> replayed =
                                    typedWorkflowCoordinator.replayCompletedNode(
                                            request, node, events, eventSink);
                            if (replayed.isPresent()) {
                                completedNodeIds.add(node.getNodeId());
                                return replayed.get();
                            }
                            Optional<Map<String, Object>> approval =
                                    typedWorkflowCoordinator.handleHumanApproval(
                                            request, node, events, eventSink);
                            if (approval.isPresent()) {
                                typedWorkflowCoordinator.afterNode(
                                        request, node, approval.get(), events, eventSink);
                                completedNodeIds.add(node.getNodeId());
                                return approval.get();
                            }
                            typedWorkflowCoordinator.beforeNode(
                                    request, node, events, eventSink);
                        }
                        Map<String, Object> output = graphNodeExecutionCoordinator.execute(
                                definition,
                                node,
                                request,
                                state,
                                events,
                                eventSink,
                                nodeHooks);
                        if (typedWorkflowCoordinator != null) {
                            typedWorkflowCoordinator.afterNode(
                                    request, node, output, events, eventSink);
                        }
                        completedNodeIds.add(node.getNodeId());
                        return output;
                    } catch (OpsWorkflowApprovalPendingException pending) {
                        throw pending;
                    } catch (OpsRunCanceledException canceled) {
                        throw canceled;
                    } catch (RuntimeException error) {
                        if (typedWorkflowCoordinator != null) {
                            typedWorkflowCoordinator.nodeFailed(
                                    request, node, error, events, eventSink);
                        }
                        throw error;
                    }
                }));
            }
            graphTopologyAssembler.addEdges(
                    graph,
                    definition,
                    analysisState == null ? null : analysisState.analysisRequest(),
                    request,
                    nodes);
            CompiledGraph compiledGraph = graph.compile(CompileConfig.builder()
                    .recursionLimit(graphTopologyAssembler.recursionLimit(
                            definition,
                            analysisState == null ? null : analysisState.analysisRequest(),
                            nodes))
                    .build());
            Map<String, Object> input = graphRuntimeStateManager.initialInput(
                    request,
                    analysisState,
                    conversationContextCoordinator.originalUserQuery(request),
                    conversationContextCoordinator.memoryContext(request),
                    analysisRoutingPolicy.routeConstraint(
                            request, "allowedInvestigationSources"),
                    analysisRoutingPolicy.routeConstraint(
                            request, "excludedCapabilities"));
            Optional<OverAllState> state = compiledGraph.invoke(
                    input,
                    RunnableConfig.builder()
                            .threadId(request.getSessionId())
                            .defaultParallelExecutor(subAgentExecutor)
                            .defaultParallelAggregationStrategy(NodeAggregationStrategy.ALL_OF)
                            .build());
            if (!domainGraph.terminalNodeIds().isEmpty()
                    && domainGraph.terminalNodeIds().stream().noneMatch(completedNodeIds::contains)) {
                throw new IllegalStateException("WORKFLOW_TERMINAL_NODE_NOT_REACHED");
            }
            if (typedWorkflowCoordinator != null) {
                typedWorkflowCoordinator.complete(request, events, eventSink);
            }
            analysisStateManager.publishRunFinished(
                    definition,
                    analysisState,
                    "SUCCEEDED",
                    "通用 Runtime 运维 Graph 执行完成。");
            if (analysisState != null
                    && StringUtils.hasText(analysisState.response().getMarkdownReport())) {
                return analysisState.response().getMarkdownReport();
            }
            return state
                    .map(overAllState -> messageText(overAllState.value("output", "")))
                    .orElse("");
        } catch (OpsWorkflowApprovalPendingException pending) {
            throw pending;
        } catch (OpsRunCanceledException error) {
            if (typedWorkflowCoordinator != null) {
                try {
                    typedWorkflowCoordinator.cancel(
                            request, error.getMessage(), events, eventSink);
                } catch (RuntimeException typedFailure) {
                    error.addSuppressed(typedFailure);
                }
            }
            analysisStateManager.publishRunFinished(
                    definition, analysisState, "CANCELED", error.getMessage());
            throw error;
        } catch (Exception error) {
            RuntimeException runtimeError = error instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(error);
            if (typedWorkflowCoordinator != null) {
                typedWorkflowCoordinator.graphFailed(
                        request, runtimeError, events, eventSink);
            }
            analysisStateManager.publishRunFinished(
                    definition, analysisState, "FAILED", error.getMessage());
            log.error("通用 Agent Graph 执行失败，agentId={}", definition.getAgentId(), error);
            eventJournal.record(
                    events,
                    eventSink,
                    OpsRuntimeEvent.of(
                            "RUN_FAILED",
                            "FAILED",
                            "Graph 执行失败：" + error.getMessage()));
            throw new IllegalStateException("Graph 执行失败：" + error.getMessage(), error);
        } finally {
            if (typedWorkflowCoordinator != null) {
                typedWorkflowCoordinator.cleanup(request);
            }
            graphRuntimeStateManager.cleanup(request);
            analysisStateManager.remove(value(request.getRunId()));
        }
    }

    List<OpsWorkflowNode> graphNodes(OpsAgentDefinition definition) {
        List<OpsWorkflowNode> nodes = Optional.ofNullable(definition.getNodes()).orElse(List.of());
        if (!nodes.isEmpty()) {
            return nodes;
        }
        String engine = value(definition.getEngine()).trim().toUpperCase(java.util.Locale.ROOT);
        List<OpsAgentScopeConfig> scopes = Optional.ofNullable(definition.getAgentscopeAgents()).orElse(List.of());
        if ("AGENTSCOPE".equals(engine) && !scopes.isEmpty() && scopes.get(0) != null) {
            OpsAgentScopeConfig scope = scopes.get(0);
            Map<String, Object> config = new LinkedHashMap<>();
            if (StringUtils.hasText(scope.getRole())) config.put("role", scope.getRole());
            if (scope.getMaxDepth() != null) config.put("maxDepth", scope.getMaxDepth());
            if (scope.getMaxIterations() != null) config.put("maxIterations", scope.getMaxIterations());
            config.put("inheritProjectCapabilities", Boolean.TRUE.equals(scope.getInheritProjectCapabilities()));
            config.put("changePackageStatusEnabled", Boolean.TRUE.equals(scope.getChangePackageStatusEnabled()));
            config.put("allowedToolNames", Optional.ofNullable(scope.getAllowedToolNames()).orElse(List.of()));
            return List.of(OpsWorkflowNode.builder()
                    .nodeId(nodeExecutionPolicy.firstText(scope.getAgentId(), "main-assistant"))
                    .type("AGENTSCOPE")
                    .mode("REACT")
                    .agent(nodeExecutionPolicy.firstText(
                            scope.getAgentId(), scope.getName(), definition.getName(), definition.getAgentId()))
                    .instruction(nodeExecutionPolicy.firstText(scope.getInstruction(), definition.getInstruction()))
                    .modelId(nodeExecutionPolicy.firstText(scope.getModelId(), definition.getModelId()))
                    .outputKey(scope.getOutputKey())
                    .ragEnabled(scope.getRagEnabled())
                    .knowledgeBaseId(scope.getKnowledgeBaseId())
                    .repairEnabled(scope.getRepairEnabled())
                    .changePackageEnabled(scope.getChangePackageEnabled())
                    .skills(Optional.ofNullable(scope.getSkills()).orElse(List.of()))
                    .mcpIds(Optional.ofNullable(scope.getMcpIds()).orElse(List.of()))
                    .executionTargetIds(Optional.ofNullable(scope.getExecutionTargetIds()).orElse(List.of()))
                    .mcpServers(Optional.ofNullable(scope.getMcpServers()).orElse(List.of()))
                    .config(config)
                    .build());
        }
        return List.of(OpsWorkflowNode.builder()
                .nodeId("chat")
                .type("CHAT")
                .agent(nodeExecutionPolicy.firstText(
                        definition.getName(), definition.getAgentId()))
                .instruction(definition.getInstruction())
                .ragEnabled(definition.getRagEnabled())
                .knowledgeBaseId(definition.getKnowledgeBaseId())
                .changePackageEnabled(definition.getChangePackageEnabled())
                .mcpServers(definition.getMcpServers())
                .build());
    }

    private String messageText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
