package cn.lgs.orbisops.domain.agentdefinition.model;

/** Versioned typed Workflow envelope reusing the authoritative Agent graph and release hash. */
public record AgentWorkflowDefinition(
        int schemaVersion,
        int definitionVersion,
        String definitionHash,
        AgentGraphDefinition graph
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AgentWorkflowDefinition {
        if (schemaVersion <= 0 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "WORKFLOW_SCHEMA_VERSION_UNSUPPORTED:" + schemaVersion);
        }
        if (definitionVersion < 0) {
            throw new IllegalArgumentException("WORKFLOW_DEFINITION_VERSION_INVALID");
        }
        definitionHash = definitionHash == null ? "" : definitionHash.trim();
        if (graph == null) {
            throw new IllegalArgumentException("WORKFLOW_GRAPH_DEFINITION_REQUIRED");
        }
    }

    public boolean versioned() {
        return definitionVersion > 0 && !definitionHash.isBlank();
    }

    public void requireVersionedIdentity() {
        if (!versioned()) {
            throw new IllegalStateException("WORKFLOW_DEFINITION_VERSION_IDENTITY_REQUIRED");
        }
    }
}
