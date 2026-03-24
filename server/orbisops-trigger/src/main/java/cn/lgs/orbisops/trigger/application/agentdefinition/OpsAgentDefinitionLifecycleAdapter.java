package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecyclePort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionReleaseGatePort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionValidationPort;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinitionValidator;
import cn.lgs.orbisops.trigger.application.agenteval.OpsAgentEvalAdapter;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Anti-corruption adapter around the legacy registry during repository extraction. */
@Component
public class OpsAgentDefinitionLifecycleAdapter implements
        AgentDefinitionLifecyclePort<OpsAgentDefinition>,
        AgentDefinitionValidationPort<OpsAgentDefinition>,
        AgentDefinitionReleaseGatePort<OpsAgentDefinition> {

    private final OpsAgentDefinitionGateway registry;
    private final OpsAgentDefinitionValidator validator;
    private final OpsAgentEvalAdapter evalService;

    public OpsAgentDefinitionLifecycleAdapter(OpsAgentDefinitionGateway registry,
                                              OpsAgentDefinitionValidator validator,
                                              OpsAgentEvalAdapter evalService) {
        this.registry = registry;
        this.validator = validator;
        this.evalService = evalService;
    }

    @Override
    public OpsAgentDefinition saveDraft(OpsAgentDefinition definition) {
        return registry.saveDraft(definition);
    }

    @Override
    public OpsAgentDefinition definition(String agentId, int version) {
        return registry.resolve(agentId, version, true);
    }

    @Override
    public AgentDefinitionVersionState state(String agentId, int version) {
        OpsAgentDefinition definition = definition(agentId, version);
        return state(definition);
    }

    @Override
    public OpsAgentDefinition transition(AgentDefinitionVersionState expected,
                                         AgentDefinitionVersionState target) {
        requireUnchanged(expected);
        if (!expected.agentId().equals(target.agentId()) || expected.version() != target.version()) {
            throw new IllegalArgumentException("AGENT_DEFINITION_IDENTITY_IMMUTABLE");
        }
        return switch (target.lifecycle()) {
            case VALIDATED -> registry.validateVersion(target.agentId(), target.version());
            case PUBLISHED -> registry.publishVersion(target.agentId(), target.version());
            default -> throw new IllegalStateException(
                    "AGENT_DEFINITION_TRANSITION_UNSUPPORTED:" + target.lifecycle().name());
        };
    }

    @Override
    public OpsAgentDefinition republish(AgentDefinitionVersionState expected) {
        requireUnchanged(expected);
        return registry.rollback(expected.agentId(), expected.version());
    }

    @Override
    public boolean disable(AgentDefinitionVersionState expected) {
        requireUnchanged(expected);
        return registry.disableVersion(expected.agentId(), expected.version());
    }

    @Override
    public void validate(OpsAgentDefinition definition) {
        validator.validate(definition);
    }

    @Override
    public void assertReleaseAllowed(OpsAgentDefinition definition) {
        evalService.assertReleaseAllowed(definition);
    }

    private void requireUnchanged(AgentDefinitionVersionState expected) {
        AgentDefinitionVersionState actual = state(expected.agentId(), expected.version());
        if (actual.lifecycle() != expected.lifecycle()
                || !Objects.equals(actual.definitionHash(), expected.definitionHash())) {
            throw new IllegalStateException("AGENT_DEFINITION_VERSION_CONFLICT：expected="
                    + expected.lifecycle().name() + "/" + expected.definitionHash()
                    + " actual=" + actual.lifecycle().name() + "/" + actual.definitionHash());
        }
    }

    private AgentDefinitionVersionState state(OpsAgentDefinition definition) {
        return registry.describe(definition);
    }
}
