package cn.lgs.orbisops.domain.toolset.model;

/** Project-bound immutable execution reference. It contains no endpoint credentials or live clients. */
public record BoundToolReference(
        String projectId,
        ToolReference reference,
        ToolProviderDescriptor provider,
        ToolSemantics semantics,
        ToolSchema schema,
        ToolGovernance governance
) {

    public BoundToolReference {
        projectId = required(projectId, "BOUND_TOOL_PROJECT_REQUIRED");
        if (reference == null) throw new IllegalArgumentException("BOUND_TOOL_REFERENCE_REQUIRED");
        if (provider == null) throw new IllegalArgumentException("BOUND_TOOL_PROVIDER_REQUIRED");
        if (semantics == null) throw new IllegalArgumentException("BOUND_TOOL_SEMANTICS_REQUIRED");
        schema = schema == null ? ToolSchema.empty() : schema;
        governance = governance == null ? ToolGovernance.from(provider, semantics) : governance;
    }

    public BoundToolReference(
            String projectId,
            ToolReference reference,
            ToolProviderDescriptor provider,
            ToolSemantics semantics,
            ToolSchema schema) {
        this(projectId, reference, provider, semantics, schema, null);
    }

    public String canonicalId() {
        return projectId + ":" + reference.canonicalId();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
