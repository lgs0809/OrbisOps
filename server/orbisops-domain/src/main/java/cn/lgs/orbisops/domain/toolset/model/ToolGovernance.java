package cn.lgs.orbisops.domain.toolset.model;

/** Provider-neutral policy facts for one Tool definition. */
public record ToolGovernance(
        ToolEffect effect,
        ToolRiskLevel risk,
        ApprovalRequirement approvalRequirement,
        IdempotencyCapability idempotencyCapability,
        ReconciliationCapability reconciliationCapability,
        CompensationCapability compensationCapability,
        ToolTrustLevel trustLevel
) {

    public ToolGovernance {
        if (effect == null) throw new IllegalArgumentException("TOOL_EFFECT_REQUIRED");
        if (risk == null) throw new IllegalArgumentException("TOOL_RISK_REQUIRED");
        if (approvalRequirement == null) throw new IllegalArgumentException("TOOL_APPROVAL_REQUIREMENT_REQUIRED");
        if (idempotencyCapability == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_CAPABILITY_REQUIRED");
        if (reconciliationCapability == null) throw new IllegalArgumentException("TOOL_RECONCILIATION_CAPABILITY_REQUIRED");
        if (compensationCapability == null) throw new IllegalArgumentException("TOOL_COMPENSATION_CAPABILITY_REQUIRED");
        if (trustLevel == null) throw new IllegalArgumentException("TOOL_TRUST_LEVEL_REQUIRED");
        if (reconciliationCapability != ReconciliationCapability.NONE
                && idempotencyCapability == IdempotencyCapability.NONE) {
            throw new IllegalArgumentException("TOOL_RECONCILIATION_REQUIRES_IDEMPOTENCY");
        }
    }

    public static ToolGovernance from(
            ToolProviderDescriptor provider,
            ToolSemantics semantics) {
        if (provider == null) throw new IllegalArgumentException("TOOL_PROVIDER_REQUIRED");
        if (semantics == null) throw new IllegalArgumentException("TOOL_SEMANTICS_REQUIRED");
        ToolTrustLevel trust = switch (provider.providerType()) {
            case INTERNAL, BUILT_IN, SKILL -> ToolTrustLevel.PLATFORM_INTERNAL;
            case MCP, HTTP_API, LOCAL -> ToolTrustLevel.PLATFORM_CONFIGURED;
        };
        return new ToolGovernance(
                ToolEffect.from(semantics),
                semantics.riskLevel(),
                ApprovalRequirement.from(semantics),
                IdempotencyCapability.from(semantics),
                ReconciliationCapability.NONE,
                semantics.writesTargetResource()
                        ? CompensationCapability.MANUAL
                        : CompensationCapability.NONE,
                trust);
    }
}
