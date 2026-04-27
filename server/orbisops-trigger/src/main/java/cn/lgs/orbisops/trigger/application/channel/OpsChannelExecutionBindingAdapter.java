package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelExecutionBindingPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Component;

@Component
public final class OpsChannelExecutionBindingAdapter implements ChannelExecutionBindingPort {

    private final OpsAgentDefinitionQueryGateway registry;
    private final ProjectDefinitionApplicationService projects;

    public OpsChannelExecutionBindingAdapter(OpsAgentDefinitionQueryGateway registry,
                                             ProjectDefinitionApplicationService projects) {
        if (registry == null || projects == null) {
            throw new IllegalArgumentException("CHANNEL_EXECUTION_BINDING_DEPENDENCIES_REQUIRED");
        }
        this.registry = registry;
        this.projects = projects;
    }

    @Override
    public ResolvedExecution resolve(String projectId, ExecutionBinding binding) {
        ExecutionBinding requested = binding == null ? ExecutionBinding.none() : binding;
        if (requested.type() == ExecutionType.NONE) {
            return new ResolvedExecution(ExecutionType.NONE, "", 0, "");
        }
        if (requested.type() == ExecutionType.REACT) {
            String defaultAgentId = projects.defaultAgentId(projectId);
            if (defaultAgentId == null || defaultAgentId.isBlank()) {
                throw new IllegalStateException("PROJECT_DEFAULT_REACT_NOT_CONFIGURED");
            }
            OpsAgentDefinition definition = resolve(projectId, defaultAgentId, null);
            if (AgentDefinitionKind.parse(definition.getDefinitionKind()) == AgentDefinitionKind.SPECIALIZED_WORKFLOW) {
                throw new IllegalStateException("PROJECT_DEFAULT_AGENT_MUST_BE_REACT");
            }
            return resolved(ExecutionType.REACT, definition);
        }
        Integer version = requested.versionPolicy() == ExecutionVersionPolicy.PINNED_VERSION
                ? requested.version()
                : null;
        OpsAgentDefinition definition = resolve(projectId, requested.workflowId(), version);
        if (AgentDefinitionKind.parse(definition.getDefinitionKind()) != AgentDefinitionKind.SPECIALIZED_WORKFLOW) {
            throw new IllegalArgumentException("CHANNEL_WORKFLOW_REQUIRED");
        }
        return resolved(ExecutionType.WORKFLOW, definition);
    }

    private OpsAgentDefinition resolve(String projectId, String definitionId, Integer version) {
        try {
            OpsAgentDefinition definition = registry.resolveForProject(definitionId, version, false, projectId);
            if (definition == null || definition.getVersion() == null || definition.getVersion() <= 0
                    || definition.getDefinitionHash() == null || definition.getDefinitionHash().isBlank()) {
                throw new IllegalStateException("CHANNEL_EXECUTION_DEFINITION_UNAVAILABLE");
            }
            return definition;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("CHANNEL_EXECUTION_PROJECT_MISMATCH:" + failure.getMessage(), failure);
        }
    }

    private ResolvedExecution resolved(ExecutionType type, OpsAgentDefinition definition) {
        return new ResolvedExecution(type, definition.getAgentId(), definition.getVersion(), definition.getDefinitionHash());
    }
}
