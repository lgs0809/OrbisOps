package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityCatalogSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerDecision;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerSelection;

import java.util.List;
import java.util.Set;

/** Intersects requested Agent capabilities with the authorized project catalog. */
public final class AgentCapabilitySanitizationPolicy {

    public List<AgentCapabilityOwnerDecision> sanitize(
            List<AgentCapabilityOwnerSelection> selections,
            AgentCapabilityCatalogSnapshot catalog) {
        AgentCapabilityCatalogSnapshot safeCatalog = catalog == null
                ? new AgentCapabilityCatalogSnapshot(null, null, null, null)
                : catalog;
        return (selections == null ? List.<AgentCapabilityOwnerSelection>of() : selections)
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(selection -> decide(selection, safeCatalog))
                .toList();
    }

    private AgentCapabilityOwnerDecision decide(
            AgentCapabilityOwnerSelection selection,
            AgentCapabilityCatalogSnapshot catalog) {
        List<String> skills = allowed(selection.requestedSkills(), catalog.skillIds());
        List<String> projectTools = allowed(
                selection.requestedProjectTools(),
                catalog.projectToolIds());
        List<String> executionTargets = allowed(
                selection.requestedExecutionTargets(),
                catalog.executionTargetIds());
        boolean forceRagEnabled = selection.enableRagWhenNoProjectTool()
                && projectTools.isEmpty();
        return new AgentCapabilityOwnerDecision(
                selection.ownerKey(),
                catalog.primaryKnowledgeBaseId(),
                skills,
                projectTools,
                executionTargets,
                forceRagEnabled);
    }

    private List<String> allowed(List<String> requested, Set<String> authorized) {
        return (requested == null ? List.<String>of() : requested).stream()
                .filter(java.util.Objects::nonNull)
                .filter(authorized::contains)
                .toList();
    }
}
