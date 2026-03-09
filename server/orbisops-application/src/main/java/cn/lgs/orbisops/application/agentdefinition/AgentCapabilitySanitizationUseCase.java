package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityCatalogSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerDecision;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerSelection;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilitySanitizationPolicy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves the project catalog once and sanitizes all Agent capability owners deterministically. */
public final class AgentCapabilitySanitizationUseCase {

    private final AgentCapabilityCatalogPort catalogPort;
    private final AgentCapabilitySanitizationPolicy sanitizationPolicy;

    public AgentCapabilitySanitizationUseCase(
            AgentCapabilityCatalogPort catalogPort,
            AgentCapabilitySanitizationPolicy sanitizationPolicy) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_CATALOG_PORT_REQUIRED");
        }
        if (sanitizationPolicy == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_SANITIZATION_POLICY_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.sanitizationPolicy = sanitizationPolicy;
    }

    public List<AgentCapabilityOwnerDecision> sanitize(
            AgentCapabilitySanitizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_SANITIZATION_REQUEST_REQUIRED");
        }
        Set<String> skills = aggregate(
                request.selections(),
                AgentCapabilityOwnerSelection::requestedSkills);
        Set<String> projectTools = aggregate(
                request.selections(),
                AgentCapabilityOwnerSelection::requestedProjectTools);
        Set<String> executionTargets = aggregate(
                request.selections(),
                AgentCapabilityOwnerSelection::requestedExecutionTargets);
        AgentCapabilityCatalogSnapshot catalog = catalogPort.resolve(
                request.projectId(),
                skills,
                projectTools,
                executionTargets);
        return sanitizationPolicy.sanitize(request.selections(), catalog);
    }

    private Set<String> aggregate(
            List<AgentCapabilityOwnerSelection> selections,
            java.util.function.Function<AgentCapabilityOwnerSelection, List<String>> extractor) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (AgentCapabilityOwnerSelection selection : selections == null
                ? List.<AgentCapabilityOwnerSelection>of()
                : selections) {
            if (selection == null) continue;
            List<String> values = extractor.apply(selection);
            if (values == null) continue;
            values.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(result::add);
        }
        return Set.copyOf(result);
    }
}
