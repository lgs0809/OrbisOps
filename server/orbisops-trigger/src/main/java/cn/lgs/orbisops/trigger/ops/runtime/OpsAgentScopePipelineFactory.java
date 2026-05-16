package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import org.springframework.util.StringUtils;
import org.springframework.ai.template.NoOpTemplateRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Builds AgentScope React agents and sequential/parallel flow pipelines. */
final class OpsAgentScopePipelineFactory {

    private final OpsRuntimePromptAssembler promptAssembler;
    private final Executor executor;
    private final OpsAgentScopeConfigPolicy configPolicy;
    private final OpsAgentScopeFlowPolicy flowPolicy;
    private final ExecutorService modelCallExecutor;
    private final int modelCallTimeoutSeconds;
    private final OpsMcpDiscoveryPolicy discoveryPolicy;

    OpsAgentScopePipelineFactory(OpsRuntimePromptAssembler promptAssembler,
                                 Executor executor,
                                 OpsAgentScopeConfigPolicy configPolicy,
                                 OpsAgentScopeFlowPolicy flowPolicy) {
        this(promptAssembler, executor, configPolicy, flowPolicy, null, 240, OpsMcpDiscoveryPolicy.defaults());
    }

    OpsAgentScopePipelineFactory(OpsRuntimePromptAssembler promptAssembler,
                                 Executor executor,
                                 OpsAgentScopeConfigPolicy configPolicy,
                                 OpsAgentScopeFlowPolicy flowPolicy,
                                 ExecutorService modelCallExecutor,
                                 int modelCallTimeoutSeconds, OpsMcpDiscoveryPolicy discoveryPolicy) {
        this.promptAssembler = promptAssembler;
        this.executor = executor;
        this.configPolicy = configPolicy;
        this.flowPolicy = flowPolicy;
        this.modelCallExecutor = modelCallExecutor;
        this.modelCallTimeoutSeconds = Math.max(1, modelCallTimeoutSeconds);
        this.discoveryPolicy = discoveryPolicy;
    }

    Prepared prepare(OpsAgentDefinition definition,
                     OpsAgentChatRequest request,
                     List<OpsAgentScopeConfig> configs,
                     List<OpsRuntimeResourceBundle> bundles,
                     List<OpsRuntimeEvent> events,
                     Consumer<OpsRuntimeEvent> eventSink,
                     OpsAgentScopeExecutor.Hooks hooks) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        List<Agent> agents = buildAgents(
                definition,
                request,
                configs,
                bundles,
                events,
                eventSink,
                suffix,
                hooks);
        String pipelineName = "pipeline_"
                + safeName(definition.getAgentId())
                + "_"
                + suffix;
        String mode = flowPolicy.mode(definition);
        return new Prepared(
                List.copyOf(agents),
                pipelineName,
                mode,
                flowPolicy.outputKey(mode, configs));
    }

    Agent buildFlow(OpsAgentDefinition definition, Prepared prepared) {
        return buildFlow(
                definition,
                prepared.name(),
                prepared.agents(),
                prepared.mode());
    }

    private List<Agent> buildAgents(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            List<OpsAgentScopeConfig> configs,
            List<OpsRuntimeResourceBundle> bundles,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String suffix,
            OpsAgentScopeExecutor.Hooks hooks) {
        List<Agent> agents = new ArrayList<>();
        for (int index = 0; index < configs.size(); index++) {
            OpsAgentScopeConfig config = configs.get(index);
            OpsRuntimeResourceBundle bundle = bundles.get(index);
            String outputKey = StringUtils.hasText(config.getOutputKey())
                    ? config.getOutputKey()
                    : "agent_" + index;
            String agentName = safeName(firstText(
                    config.getName(), config.getAgentId(), "agent_" + index))
                    + "_"
                    + suffix
                    + "_"
                    + index;
            int requestedToolRounds = configPolicy.maxToolRounds(
                    definition, config, request);
            ToolLoopCoordinator coordinator = hooks.toolLoopCoordinator();
            ToolLoopCoordinator.Budget budget = coordinator == null
                    ? new ToolLoopCoordinator.Budget(
                    requestedToolRounds,
                    flowPolicy.recursionLimit(requestedToolRounds),
                    Math.max(1, Math.min(2, requestedToolRounds)))
                    : coordinator.prepare(
                    requestedToolRounds,
                    flowPolicy.recursionLimit(requestedToolRounds),
                    3,
                    20,
                    2);
            agents.add(ReactAgent.builder()
                    .name(agentName)
                    .model(bundle.getChatModel())
                    // OrbisOps already assembled this instruction; JSON/tool schemas are literal data.
                    .templateRenderer(new NoOpTemplateRenderer())
                    .instruction(promptAssembler.agentInstruction(
                            definition, config, bundle))
                    .tools(bundle.getTools())
                    .interceptors(
                            new OpsAgentScopeModelCallInterceptor(
                                    modelCallExecutor,
                                    modelCallTimeoutSeconds,
                                    events,
                                    eventSink,
                                    agentName),
                            new OpsMcpDisclosureInterceptor(bundle, event -> record(events, eventSink, event), discoveryPolicy),
                            new OpsToolRoundSynthesisInterceptor(budget.maxRounds()))
                    .compileConfig(CompileConfig.builder()
                            .recursionLimit(budget.recursionLimit())
                            .build())
                    .outputKey(outputKey)
                    .executor(executor)
                    .build());
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("REACT_AGENT_READY")
                    .agent(agentName)
                    .status("READY")
                    .summary("ReAct 工具节点已构建：" + agentName)
                    .payload(Map.of(
                            "outputKey", outputKey,
                            "projectId", bundle.getProjectId(),
                            "mcpIds", bundle.getMcpIds(),
                            "toolNames", bundle.getTools().stream()
                                    .filter(tool -> tool != null && tool.getToolDefinition() != null)
                                    .map(tool -> tool.getToolDefinition().name())
                                    .filter(StringUtils::hasText)
                                    .distinct()
                                    .toList(),
                            "maxToolRounds", budget.maxRounds(),
                            "recursionLimit", budget.recursionLimit(),
                            "checkpointInterval", budget.checkpointInterval()))
                    .build());
        }
        return agents;
    }

    private Agent buildFlow(OpsAgentDefinition definition,
                            String pipelineName,
                            List<Agent> agents,
                            String mode) {
        if (agents.size() == 1) return agents.get(0);
        if ("PARALLEL".equals(mode)) {
            return ParallelAgent.builder()
                    .name(pipelineName)
                    .description("Parallel ReAct operations investigation flow")
                    .subAgents(agents)
                    .mergeOutputKey("parallel_results")
                    .mergeStrategy(new ParallelAgent.ConcatenationMergeStrategy("\n\n"))
                    .maxConcurrency(flowPolicy.maxConcurrency(
                            definition, agents.size()))
                    .executor(executor)
                    .build();
        }
        return SequentialAgent.builder()
                .name(pipelineName)
                .description("Sequential ReAct operations investigation flow")
                .subAgents(agents)
                .executor(executor)
                .build();
    }

    private String safeName(String name) {
        String value = StringUtils.hasText(name) ? name.trim() : "agent";
        return value.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private void record(List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> eventSink,
                        OpsRuntimeEvent event) {
        events.add(event);
        if (eventSink != null) eventSink.accept(event);
    }

    record Prepared(List<Agent> agents, String name, String mode, String outputKey) {
    }
}
