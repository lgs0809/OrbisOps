package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityCatalogSnapshot;

import java.util.Set;

/** Outbound port for resolving the authorized capability catalog of one project. */
public interface AgentCapabilityCatalogPort {

    AgentCapabilityCatalogSnapshot resolve(
            String projectId,
            Set<String> requestedSkillIds,
            Set<String> requestedProjectToolIds,
            Set<String> requestedExecutionTargetIds);
}
