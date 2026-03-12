package cn.lgs.orbisops.types.execution;

/**
 * Product-level execution binding. REACT intentionally hides the project's internal default AgentDefinition.
 * WORKFLOW binds a named published workflow; NONE is used by output-only entry points such as notification channels.
 */
public record ExecutionBinding(
        ExecutionType type,
        String workflowId,
        ExecutionVersionPolicy versionPolicy,
        Integer version,
        String definitionHash) {

    public ExecutionBinding {
        type = type == null ? ExecutionType.NONE : type;
        workflowId = normalize(workflowId);
        versionPolicy = versionPolicy == null ? ExecutionVersionPolicy.LATEST_PUBLISHED : versionPolicy;
        definitionHash = normalize(definitionHash);
        if (type == ExecutionType.WORKFLOW && workflowId.isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_ID_REQUIRED");
        }
        if (type != ExecutionType.WORKFLOW) {
            workflowId = "";
            versionPolicy = ExecutionVersionPolicy.LATEST_PUBLISHED;
            version = null;
            definitionHash = "";
        } else if (versionPolicy == ExecutionVersionPolicy.PINNED_VERSION && (version == null || version <= 0)) {
            throw new IllegalArgumentException("WORKFLOW_PINNED_VERSION_REQUIRED");
        }
    }

    public static ExecutionBinding none() {
        return new ExecutionBinding(ExecutionType.NONE, "", ExecutionVersionPolicy.LATEST_PUBLISHED, null, "");
    }

    public static ExecutionBinding react() {
        return new ExecutionBinding(ExecutionType.REACT, "", ExecutionVersionPolicy.LATEST_PUBLISHED, null, "");
    }

    public static ExecutionBinding workflow(String workflowId,
                                            ExecutionVersionPolicy versionPolicy,
                                            Integer version,
                                            String definitionHash) {
        return new ExecutionBinding(ExecutionType.WORKFLOW, workflowId, versionPolicy, version, definitionHash);
    }

    public boolean acceptsInbound() {
        return type != ExecutionType.NONE;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
