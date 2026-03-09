package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/** Thin runtime resource facade: build context, execute the typed pipeline, return bundle. */
@Service
public final class OpsRuntimeResourceAssembler {

    private final OpsRuntimeResourceContextFactory contextFactory;
    private final OpsRuntimeResourcePipeline pipeline;

    public OpsRuntimeResourceAssembler(
            OpsRuntimeResourceContextFactory contextFactory,
            OpsRuntimeResourcePipeline pipeline) {
        if (contextFactory == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_FACTORY_REQUIRED");
        }
        if (pipeline == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_PIPELINE_REQUIRED");
        }
        this.contextFactory = contextFactory;
        this.pipeline = pipeline;
    }

    public OpsRuntimeResourceBundle assembleAgent(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return pipeline.assemble(
                contextFactory.agent(definition, request, events, eventSink));
    }

    public OpsRuntimeResourceBundle assembleNode(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return pipeline.assemble(
                contextFactory.node(definition, node, request, events, eventSink));
    }

    public OpsRuntimeResourceBundle assembleAgentScope(
            OpsAgentDefinition definition,
            OpsAgentScopeConfig config,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return pipeline.assemble(
                contextFactory.agentScope(definition, config, request, events, eventSink));
    }
}
