package cn.lgs.orbisops.application.agentdefinition;

/** Outbound authorization port for capabilities referenced by one Agent Definition. */
public interface AgentCapabilityAuthorizationPort {

    boolean projectExists(String projectId);

    boolean skillAllowed(String projectId, String skillId);

    boolean projectToolAllowed(String projectId, String toolId);

    boolean knowledgeBaseAllowed(String projectId, String knowledgeBaseId);

    boolean executionTargetEnabled(String projectId, String executionTargetId);
}
