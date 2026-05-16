package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns engine-adapter resolution and CHAT/GRAPH/AGENT_SCOPE node strategy dispatch. */
final class OpsRuntimeEngineDispatcher implements OpsAgentRuntimeSupport {

    private final Map<String, OpsEngineAdapter> adapters;
    private final Supplier<OpsNodeExecutionStrategyRegistry> strategyRegistrySupplier;
    private final OpsChatEngineExecutionCoordinator chatCoordinator;
    private final OpsGraphEngineExecutionCoordinator graphCoordinator;
    private final OpsAgentScopeExecutionCoordinator agentScopeCoordinator;
    private final Consumer<OpsAgentChatRequest> cancellationCheck;
    private final Supplier<OpsGraphNodeExecutionCoordinator.Hooks> graphHooksSupplier;

    OpsRuntimeEngineDispatcher(
            List<OpsEngineAdapter> adapterList,
            Supplier<OpsNodeExecutionStrategyRegistry> strategyRegistrySupplier,
            OpsChatEngineExecutionCoordinator chatCoordinator,
            OpsGraphEngineExecutionCoordinator graphCoordinator,
            OpsAgentScopeExecutionCoordinator agentScopeCoordinator,
            Consumer<OpsAgentChatRequest> cancellationCheck,
            Supplier<OpsGraphNodeExecutionCoordinator.Hooks> graphHooksSupplier) {
        Map<String, OpsEngineAdapter> indexed = new LinkedHashMap<>();
        for (OpsEngineAdapter adapter : adapterList == null ? List.<OpsEngineAdapter>of() : adapterList) {
            indexed.put(adapter.key(), adapter);
        }
        this.adapters = Collections.unmodifiableMap(indexed);
        this.strategyRegistrySupplier = strategyRegistrySupplier;
        this.chatCoordinator = chatCoordinator;
        this.graphCoordinator = graphCoordinator;
        this.agentScopeCoordinator = agentScopeCoordinator;
        this.cancellationCheck = cancellationCheck;
        this.graphHooksSupplier = graphHooksSupplier;
    }

    String executePlan(OpsAgentDefinition definition,
                       OpsAgentChatRequest request,
                       OpsRuntimeExecutionPlan plan,
                       List<OpsRuntimeEvent> events,
                       Consumer<OpsRuntimeEvent> eventSink) {
        if (plan == null) {
            throw new IllegalArgumentException("RUNTIME_EXECUTION_PLAN_REQUIRED");
        }
        if (!OpsUnifiedAgentEngineAdapter.KEY.equals(plan.getAdapterKey())) {
            throw new SecurityException(
                    "LEGACY_TOP_LEVEL_AGENT_RUNTIME_FORBIDDEN:" + plan.getAdapterKey());
        }
        OpsEngineAdapter adapter = adapters.get(plan.getAdapterKey());
        if (adapter == null) {
            throw new IllegalStateException("未注册 Agent 引擎适配器：" + plan.getAdapterKey());
        }
        String output = adapter.execute(
                definition,
                request,
                plan,
                this,
                events,
                eventSink);
        OpsAgentOutputGuard.assertSuccessful(output);
        return output;
    }

    Set<String> adapterKeys() {
        return adapters.keySet();
    }

    @Override
    public String runChatEngine(OpsAgentDefinition definition,
                                OpsAgentChatRequest request,
                                OpsRuntimeExecutionPlan plan,
                                List<OpsRuntimeEvent> events,
                                Consumer<OpsRuntimeEvent> eventSink) {
        return executeNode(
                OpsRuntimeExecutionNode.CHAT,
                nodeContext(definition, request, plan, events, eventSink));
    }

    @Override
    public String runGraphEngine(OpsAgentDefinition definition,
                                 OpsAgentChatRequest request,
                                 OpsRuntimeExecutionPlan plan,
                                 List<OpsRuntimeEvent> events,
                                 Consumer<OpsRuntimeEvent> eventSink) {
        return executeNode(
                OpsRuntimeExecutionNode.GRAPH,
                nodeContext(definition, request, plan, events, eventSink));
    }

    @Override
    public String runAgentScopeEngine(OpsAgentDefinition definition,
                                      OpsAgentChatRequest request,
                                      OpsRuntimeExecutionPlan plan,
                                      List<OpsRuntimeEvent> events,
                                      Consumer<OpsRuntimeEvent> eventSink) {
        return executeNode(
                OpsRuntimeExecutionNode.AGENT_SCOPE,
                nodeContext(definition, request, plan, events, eventSink));
    }

    private String executeNode(OpsRuntimeExecutionNode node,
                               OpsRuntimeNodeExecutionContext context) {
        OpsNodeExecutionStrategyRegistry registry = strategyRegistrySupplier == null
                ? null
                : strategyRegistrySupplier.get();
        if (registry != null) {
            return registry.execute(node, context);
        }
        return switch (node) {
            case CHAT -> executeChat(context);
            case GRAPH -> executeGraph(context);
            case AGENT_SCOPE -> executeAgentScope(context);
        };
    }

    private OpsRuntimeNodeExecutionContext nodeContext(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            OpsRuntimeExecutionPlan plan,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return new OpsRuntimeNodeExecutionContext(
                new OpsRuntimeNodeExecutionContext.NodeExecutor() {
                    @Override
                    public String executeChat(OpsRuntimeNodeExecutionContext context) {
                        return OpsRuntimeEngineDispatcher.this.executeChat(context);
                    }

                    @Override
                    public String executeGraph(OpsRuntimeNodeExecutionContext context) {
                        return OpsRuntimeEngineDispatcher.this.executeGraph(context);
                    }

                    @Override
                    public String executeAgentScope(OpsRuntimeNodeExecutionContext context) {
                        return OpsRuntimeEngineDispatcher.this.executeAgentScope(context);
                    }
                },
                definition,
                request,
                plan,
                events,
                eventSink);
    }

    private String executeChat(OpsRuntimeNodeExecutionContext context) {
        return chatCoordinator.execute(
                context.definition(),
                context.request(),
                context.events(),
                context.eventSink(),
                context.plan().isMemoryEnabled(),
                cancellationCheck::accept);
    }

    private String executeGraph(OpsRuntimeNodeExecutionContext context) {
        return graphCoordinator.execute(
                context.definition(),
                context.request(),
                context.events(),
                context.eventSink(),
                graphHooksSupplier.get());
    }

    private String executeAgentScope(OpsRuntimeNodeExecutionContext context) {
        return agentScopeCoordinator.execute(
                context.definition(),
                context.request(),
                context.request().getQuery(),
                context.events(),
                context.eventSink());
    }
}
