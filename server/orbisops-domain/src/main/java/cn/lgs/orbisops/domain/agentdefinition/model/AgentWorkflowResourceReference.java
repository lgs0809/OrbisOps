package cn.lgs.orbisops.domain.agentdefinition.model;

/** Typed reference from a workflow node to a separately governed runtime resource. */
public record AgentWorkflowResourceReference(
        ResourceType resourceType,
        String resourceId
) {

    public enum ResourceType {
        MODEL,
        KNOWLEDGE_BASE,
        SKILL,
        MCP,
        EXECUTION_TARGET,
        INLINE_MCP
    }

    public AgentWorkflowResourceReference {
        if (resourceType == null) {
            throw new IllegalArgumentException("WORKFLOW_RESOURCE_TYPE_REQUIRED");
        }
        resourceId = resourceId == null ? "" : resourceId.trim();
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_RESOURCE_ID_REQUIRED:" + resourceType);
        }
    }
}
