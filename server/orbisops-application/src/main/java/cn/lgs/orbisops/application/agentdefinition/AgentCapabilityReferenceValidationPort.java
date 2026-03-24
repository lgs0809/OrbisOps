package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Outbound validation port for project, Skill and MCP references. */
public interface AgentCapabilityReferenceValidationPort {

    void validateProject(String projectId);

    void validateSkills(List<String> skills, String owner, String projectId);

    void validateMcpReferences(List<String> mcpIds, String owner, String projectId);
}
