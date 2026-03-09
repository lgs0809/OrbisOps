package cn.lgs.orbisops.domain.toolset.model;

/** Immutable execution-tool facts owned by the Toolset context. */
public record ToolDefinition(
        String toolName,
        String displayName,
        String description,
        String parametersJson,
        String adapterType,
        String commandTemplate,
        String mcpServerId,
        String remoteToolName,
        String httpConfigJson,
        String dbConfigJson,
        boolean readOnly,
        boolean writesRepairWorkspace,
        boolean writesTargetResource,
        boolean requiresChangePackage,
        boolean requiresApproval,
        ToolRiskLevel riskLevel,
        String outputBudgetJson,
        boolean enabled,
        ToolProviderDescriptor providerDescriptor,
        ToolSemantics semantics,
        ToolSchema schema,
        ToolGovernance governance
) {

    public ToolDefinition {
        toolName = required(toolName, "TOOL_NAME_REQUIRED");
        displayName = fallback(displayName, toolName);
        description = text(description);
        parametersJson = json(parametersJson, "{}");
        adapterType = required(adapterType, "TOOL_ADAPTER_TYPE_REQUIRED");
        commandTemplate = text(commandTemplate);
        mcpServerId = text(mcpServerId);
        remoteToolName = text(remoteToolName);
        httpConfigJson = json(httpConfigJson, "{}");
        dbConfigJson = json(dbConfigJson, "{}");
        if (riskLevel == null) throw new IllegalArgumentException("TOOL_RISK_LEVEL_REQUIRED");
        outputBudgetJson = json(outputBudgetJson, "{}");
        providerDescriptor = providerDescriptor == null
                ? legacyProvider(adapterType, commandTemplate, mcpServerId,
                        remoteToolName, httpConfigJson, dbConfigJson)
                : providerDescriptor;
        semantics = semantics == null
                ? legacySemantics(
                        readOnly,
                        writesRepairWorkspace,
                        writesTargetResource,
                        requiresChangePackage,
                        requiresApproval,
                        riskLevel)
                : semantics;
        schema = schema == null ? ToolSchema.inputOnly(parametersJson) : schema;
        governance = governance == null
                ? ToolGovernance.from(providerDescriptor, semantics)
                : governance;
        if (readOnly != semantics.readOnly()
                || writesRepairWorkspace != semantics.writesRepairWorkspace()
                || writesTargetResource != semantics.writesTargetResource()
                || requiresChangePackage != semantics.requiresChangePackage()
                || requiresApproval != semantics.requiresApproval()
                || riskLevel != semantics.riskLevel()) {
            throw new IllegalArgumentException("TOOL_SEMANTICS_LEGACY_PROJECTION_CONFLICT");
        }
        if (!adapterType.equals(providerDescriptor.adapterType())) {
            throw new IllegalArgumentException("TOOL_PROVIDER_LEGACY_PROJECTION_CONFLICT");
        }
        if (!parametersJson.equals(schema.inputSchemaJson())) {
            throw new IllegalArgumentException("TOOL_SCHEMA_LEGACY_PROJECTION_CONFLICT");
        }
        if (governance.effect() != ToolEffect.from(semantics)
                || governance.risk() != semantics.riskLevel()
                || governance.approvalRequirement() != ApprovalRequirement.from(semantics)) {
            throw new IllegalArgumentException("TOOL_GOVERNANCE_SEMANTICS_PROJECTION_CONFLICT");
        }
        if (!semantics.idempotent()
                && governance.idempotencyCapability() != IdempotencyCapability.NONE) {
            throw new IllegalArgumentException("TOOL_GOVERNANCE_IDEMPOTENCY_PROJECTION_CONFLICT");
        }
    }

    /** Compatibility constructor for existing persistence and ACL projections. */
    public ToolDefinition(
            String toolName,
            String displayName,
            String description,
            String parametersJson,
            String adapterType,
            String commandTemplate,
            String mcpServerId,
            String remoteToolName,
            String httpConfigJson,
            String dbConfigJson,
            boolean readOnly,
            boolean writesRepairWorkspace,
            boolean writesTargetResource,
            boolean requiresChangePackage,
            boolean requiresApproval,
            ToolRiskLevel riskLevel,
            String outputBudgetJson,
            boolean enabled) {
        this(toolName, displayName, description, parametersJson, adapterType,
                commandTemplate, mcpServerId, remoteToolName, httpConfigJson,
                dbConfigJson, readOnly, writesRepairWorkspace,
                writesTargetResource, requiresChangePackage, requiresApproval,
                riskLevel, outputBudgetJson, enabled, null, null, null, null);
    }

    /** Compatibility constructor for the first typed Provider/Semantics migration. */
    public ToolDefinition(
            String toolName,
            String displayName,
            String description,
            String parametersJson,
            String adapterType,
            String commandTemplate,
            String mcpServerId,
            String remoteToolName,
            String httpConfigJson,
            String dbConfigJson,
            boolean readOnly,
            boolean writesRepairWorkspace,
            boolean writesTargetResource,
            boolean requiresChangePackage,
            boolean requiresApproval,
            ToolRiskLevel riskLevel,
            String outputBudgetJson,
            boolean enabled,
            ToolProviderDescriptor providerDescriptor,
            ToolSemantics semantics) {
        this(toolName, displayName, description, parametersJson, adapterType,
                commandTemplate, mcpServerId, remoteToolName, httpConfigJson,
                dbConfigJson, readOnly, writesRepairWorkspace,
                writesTargetResource, requiresChangePackage, requiresApproval,
                riskLevel, outputBudgetJson, enabled, providerDescriptor, semantics,
                null, null);
    }

    public ToolReference reference(String toolsetId) {
        return new ToolReference(toolsetId, toolName);
    }

    /** Canonical invocation binding. Legacy provider fields remain only as a migration projection. */
    public ToolBinding binding() {
        return ToolBinding.compatibility(providerDescriptor);
    }

    private static ToolSemantics legacySemantics(
            boolean readOnly,
            boolean writesRepairWorkspace,
            boolean writesTargetResource,
            boolean requiresChangePackage,
            boolean requiresApproval,
            ToolRiskLevel riskLevel) {
        boolean idempotent = readOnly;
        return new ToolSemantics(
                readOnly,
                writesRepairWorkspace,
                writesTargetResource,
                requiresChangePackage,
                requiresApproval,
                riskLevel,
                idempotent,
                idempotent);
    }

    private static ToolProviderDescriptor legacyProvider(
            String adapterType,
            String commandTemplate,
            String mcpServerId,
            String remoteToolName,
            String httpConfigJson,
            String dbConfigJson) {
        ToolProviderType type = adapterType != null && adapterType.startsWith("LOCAL_")
                ? ToolProviderType.LOCAL
                : "MCP".equalsIgnoreCase(adapterType)
                        ? ToolProviderType.MCP
                        : ToolProviderType.BUILT_IN;
        return new ToolProviderDescriptor(
                type,
                adapterType,
                adapterType,
                commandTemplate,
                mcpServerId,
                remoteToolName,
                httpConfigJson,
                dbConfigJson);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String json(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
