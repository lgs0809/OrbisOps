package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.domain.agentdefinition.model.WorkflowInvocationMode;

import java.util.List;

/**
 * Validates product-level discovery metadata independently from graph structure.
 *
 * <p>Phase 652 deliberately removed natural-language/intent based Workflow routing.
 * A specialized Workflow is executable only after an explicit user selection; the
 * positive/negative scenarios and keywords are catalog/search hints only.</p>
 */
public final class AgentDefinitionRoutingMetadataPolicy {

    public void validate(
            String definitionKind,
            String invocationMode,
            Boolean autoSelectEnabled,
            List<String> whenToUse,
            List<String> whenNotToUse,
            List<String> routingKeywords) {
        AgentDefinitionKind kind = AgentDefinitionKind.parse(definitionKind);
        if (kind != AgentDefinitionKind.SPECIALIZED_WORKFLOW) return;

        WorkflowInvocationMode mode = WorkflowInvocationMode.parse(invocationMode);
        if (mode != WorkflowInvocationMode.MANUAL_ONLY || Boolean.TRUE.equals(autoSelectEnabled)) {
            throw new IllegalArgumentException("WORKFLOW_REQUIRES_EXPLICIT_USER_SELECTION");
        }
    }
}
