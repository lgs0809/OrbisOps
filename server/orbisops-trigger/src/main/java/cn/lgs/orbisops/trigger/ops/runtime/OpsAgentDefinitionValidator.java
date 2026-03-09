package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import org.springframework.stereotype.Component;

/** Definition-time structural compilation facade. */
@Component
public final class OpsAgentDefinitionValidator {

    private final OpsAgentWorkflowStructuralCompiler structuralCompiler;

    public OpsAgentDefinitionValidator(
            OpsAgentGraphDefinitionPolicy graphDefinitionPolicy,
            OpsAgentScopeDefinitionPolicy agentScopeDefinitionPolicy,
            OpsAgentDefinitionResourceValidator resourceValidator) {
        this.structuralCompiler = new OpsAgentWorkflowStructuralCompiler(
                graphDefinitionPolicy,
                agentScopeDefinitionPolicy,
                resourceValidator);
    }

    public void validate(OpsAgentDefinition definition) {
        compile(definition);
    }

    public CompiledAgentDefinitionVersion compile(OpsAgentDefinition definition) {
        return structuralCompiler.compile(definition);
    }

    public OpsAgentWorkflowStructuralCompiler structuralCompiler() {
        return structuralCompiler;
    }
}
