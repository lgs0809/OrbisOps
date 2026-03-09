package cn.lgs.orbisops.domain.runtime.workflow.model;

public record BoundWorkflowResourceSnapshot(
        BoundWorkflowResourceKind kind,
        String resourceId,
        long resourceVersion,
        String resourceHash,
        String bindingSource,
        boolean required,
        boolean readOnly
) {

    public BoundWorkflowResourceSnapshot {
        if (kind == null) throw new IllegalArgumentException("BOUND_WORKFLOW_RESOURCE_KIND_REQUIRED");
        resourceId = required(resourceId, "BOUND_WORKFLOW_RESOURCE_ID_REQUIRED");
        if (resourceVersion < 0) throw new IllegalArgumentException("BOUND_WORKFLOW_RESOURCE_VERSION_INVALID");
        resourceHash = required(resourceHash, "BOUND_WORKFLOW_RESOURCE_HASH_REQUIRED");
        bindingSource = required(bindingSource, "BOUND_WORKFLOW_RESOURCE_SOURCE_REQUIRED");
    }

    public String identityKey() {
        return kind.name() + ":" + resourceId;
    }

    public String immutableFingerprint() {
        return identityKey() + "@" + resourceVersion + "#" + resourceHash
                + ":required=" + required + ":readOnly=" + readOnly;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
