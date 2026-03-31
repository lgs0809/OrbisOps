package cn.lgs.orbisops.domain.toolexecution.model;

import cn.lgs.orbisops.domain.toolset.model.BoundToolReference;
import cn.lgs.orbisops.domain.toolset.model.ToolBinding;
import cn.lgs.orbisops.domain.toolset.model.ToolGovernance;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolReference;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSchema;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;

public record ToolExecutionTarget(
        String toolsetId,
        String toolName,
        String adapterType,
        String riskLevel,
        boolean readOnly,
        boolean writesRepairWorkspace,
        boolean writesTargetResource,
        boolean requiresChangePackage,
        boolean requiresApproval,
        ToolProviderDescriptor providerDescriptor,
        ToolSemantics semantics,
        ToolSchema schema,
        ToolGovernance governance) {

    public ToolExecutionTarget {
        toolsetId = required(toolsetId, "TOOL_EXECUTION_TOOLSET_REQUIRED");
        toolName = required(toolName, "TOOL_EXECUTION_TOOL_REQUIRED");
        adapterType = required(adapterType, "TOOL_EXECUTION_ADAPTER_REQUIRED");
        riskLevel = value(riskLevel).isBlank() ? "UNKNOWN" : value(riskLevel).toUpperCase();
        providerDescriptor = providerDescriptor == null
                ? legacyProvider(adapterType)
                : providerDescriptor;
        boolean legacySemanticsProjection = semantics == null;
        semantics = legacySemanticsProjection
                ? legacySemantics(
                        riskLevel,
                        readOnly,
                        writesRepairWorkspace,
                        writesTargetResource,
                        requiresChangePackage,
                        requiresApproval)
                : semantics;
        schema = schema == null ? ToolSchema.empty() : schema;
        governance = governance == null
                ? ToolGovernance.from(providerDescriptor, semantics)
                : governance;
        if (!adapterType.equals(providerDescriptor.adapterType())) {
            throw new IllegalArgumentException("TOOL_EXECUTION_PROVIDER_PROJECTION_CONFLICT");
        }
        boolean legacyControlPlaneWrite = legacySemanticsProjection
                && writesTargetResource
                && !requiresChangePackage
                && !requiresApproval
                && !semantics.writesTargetResource();
        if ((readOnly != semantics.readOnly()
                || writesRepairWorkspace != semantics.writesRepairWorkspace()
                || writesTargetResource != semantics.writesTargetResource()
                || requiresChangePackage != semantics.requiresChangePackage()
                || requiresApproval != semantics.requiresApproval())
                && !legacyControlPlaneWrite) {
            throw new IllegalArgumentException("TOOL_EXECUTION_SEMANTICS_PROJECTION_CONFLICT");
        }
        if (governance.effect() != cn.lgs.orbisops.domain.toolset.model.ToolEffect.from(semantics)
                || governance.risk() != semantics.riskLevel()
                || governance.approvalRequirement()
                != cn.lgs.orbisops.domain.toolset.model.ApprovalRequirement.from(semantics)) {
            throw new IllegalArgumentException("TOOL_EXECUTION_GOVERNANCE_PROJECTION_CONFLICT");
        }
    }

    /** Compatibility constructor for typed Provider/Semantics/Schema callers. */
    public ToolExecutionTarget(
            String toolsetId,
            String toolName,
            String adapterType,
            String riskLevel,
            boolean readOnly,
            boolean writesRepairWorkspace,
            boolean writesTargetResource,
            boolean requiresChangePackage,
            boolean requiresApproval,
            ToolProviderDescriptor providerDescriptor,
            ToolSemantics semantics,
            ToolSchema schema) {
        this(toolsetId, toolName, adapterType, riskLevel, readOnly,
                writesRepairWorkspace, writesTargetResource,
                requiresChangePackage, requiresApproval, providerDescriptor,
                semantics, schema, null);
    }

    /** Compatibility constructor for existing callers. */
    public ToolExecutionTarget(
            String toolsetId,
            String toolName,
            String adapterType,
            String riskLevel,
            boolean readOnly,
            boolean writesRepairWorkspace,
            boolean writesTargetResource,
            boolean requiresChangePackage,
            boolean requiresApproval) {
        this(toolsetId, toolName, adapterType, riskLevel, readOnly,
                writesRepairWorkspace, writesTargetResource,
                requiresChangePackage, requiresApproval, null, null, null, null);
    }

    public ToolReference reference() {
        return new ToolReference(toolsetId, toolName);
    }

    public BoundToolReference bind(String projectId) {
        return new BoundToolReference(
                projectId,
                reference(),
                providerDescriptor,
                semantics,
                schema,
                governance);
    }

    public ToolBinding binding() {
        return ToolBinding.compatibility(providerDescriptor);
    }

    private static ToolProviderDescriptor legacyProvider(String adapterType) {
        ToolProviderType providerType = adapterType.startsWith("LOCAL_")
                ? ToolProviderType.LOCAL
                : "MCP".equalsIgnoreCase(adapterType)
                        ? ToolProviderType.MCP
                        : "HTTP_API".equalsIgnoreCase(adapterType)
                                ? ToolProviderType.HTTP_API
                                : "SKILL".equalsIgnoreCase(adapterType)
                                        ? ToolProviderType.SKILL
                                        : ToolProviderType.BUILT_IN;
        return new ToolProviderDescriptor(
                providerType,
                adapterType,
                adapterType,
                "",
                "",
                "",
                "{}",
                "{}");
    }

    private static ToolSemantics legacySemantics(
            String riskLevel,
            boolean readOnly,
            boolean writesRepairWorkspace,
            boolean writesTargetResource,
            boolean requiresChangePackage,
            boolean requiresApproval) {
        ToolRiskLevel typedRisk = ToolRiskLevel.require(riskLevel, ToolRiskLevel.HIGH);
        if (writesTargetResource && !requiresChangePackage && !requiresApproval) {
            return new ToolSemantics(
                    false, false, false, false, false,
                    typedRisk, false, false);
        }
        return new ToolSemantics(
                readOnly,
                writesRepairWorkspace,
                writesTargetResource,
                requiresChangePackage,
                requiresApproval,
                typedRisk,
                readOnly,
                readOnly);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
