package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.ApprovalRequirement;
import cn.lgs.orbisops.domain.toolset.model.CompensationCapability;
import cn.lgs.orbisops.domain.toolset.model.IdempotencyCapability;
import cn.lgs.orbisops.domain.toolset.model.ReconciliationCapability;
import cn.lgs.orbisops.domain.toolset.model.ToolEffect;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-closed production Tool profile readiness. */
@Component
public final class OpsToolRuntimeReadiness {

    private final OpsExternalLocalProviderSettings providerSettings;
    private final OpsToolsetRegistry toolsets;

    public OpsToolRuntimeReadiness(
            OpsExternalLocalProviderSettings providerSettings,
            OpsToolsetRegistry toolsets) {
        if (providerSettings == null) {
            throw new IllegalArgumentException("EXTERNAL_LOCAL_PROVIDER_SETTINGS_REQUIRED");
        }
        if (toolsets == null) {
            throw new IllegalArgumentException("TOOLSET_REGISTRY_REQUIRED");
        }
        this.providerSettings = providerSettings;
        this.toolsets = toolsets;
    }

    public static OpsToolRuntimeReadiness compatibility() {
        OpsExternalLocalProviderSettings providers =
                OpsExternalLocalProviderSettings.compatibilityEnabled();
        return new OpsToolRuntimeReadiness(
                providers,
                new OpsToolsetRegistry(
                        new OpsBuiltInToolsetCatalog(),
                        new OpsToolsetDefinitionCopier(),
                        providers));
    }

    public Map<String, Object> readiness() {
        Map<String, Object> provider = providerSettings.readiness();
        List<String> effectiveIds = toolsets.listBuiltInToolsets().stream()
                .map(OpsToolsetDefinition::getToolsetId)
                .toList();
        List<String> exposedUnsafe = toolsets.rawBuiltInToolsets().stream()
                .filter(toolset -> providerSettings.production()
                        && !providerSettings.allowsToolset(toolset))
                .map(OpsToolsetDefinition::getToolsetId)
                .filter(effectiveIds::contains)
                .sorted()
                .toList();
        boolean businessWriteReady = !providerSettings.production()
                || businessWriteToolReady();
        boolean up = "UP".equals(provider.get("status"))
                && exposedUnsafe.isEmpty()
                && businessWriteReady;

        Map<String, Object> details = new LinkedHashMap<>(provider);
        details.put("status", up ? "UP" : "DOWN");
        details.put("reason", reason(
                provider, exposedUnsafe, businessWriteReady));
        details.put("unsafeToolsetsExposed", exposedUnsafe);
        details.put("businessWriteToolReady", businessWriteReady);
        details.put("productionToolBindings", List.of("INTERNAL", "MCP"));
        return Map.copyOf(details);
    }

    private String reason(
            Map<String, Object> provider,
            List<String> exposedUnsafe,
            boolean businessWriteReady) {
        if (!"UP".equals(provider.get("status"))) {
            return String.valueOf(provider.getOrDefault(
                    "reason", "PRODUCTION_TOOL_PROFILE_UNSAFE"));
        }
        if (!exposedUnsafe.isEmpty()) return "PRODUCTION_UNSAFE_TOOLSET_EXPOSED";
        if (!businessWriteReady) return "PRODUCTION_BUSINESS_WRITE_TOOL_UNSAFE";
        return "";
    }

    private boolean businessWriteToolReady() {
        var expected = UpdateAlertThresholdPolicy.governance();
        return toolsets.listBuiltInToolsets().stream()
                .filter(toolset -> UpdateAlertThresholdPolicy.TOOLSET_ID.equals(toolset.getToolsetId()))
                .flatMap(toolset -> toolset.getTools().stream())
                .anyMatch(tool -> UpdateAlertThresholdPolicy.TOOL_NAME.equals(tool.getToolName())
                        && tool.isWritesTargetResource()
                        && tool.isRequiresApproval()
                        && tool.getSemantics() != null
                        && tool.getSemantics().idempotent()
                        && tool.getSemantics().retrySafe()
                        && expected.equals(tool.getGovernance())
                        && tool.getGovernance().effect() == ToolEffect.SIDE_EFFECTING
                        && tool.getGovernance().approvalRequirement()
                        == ApprovalRequirement.OPERATOR_APPROVAL
                        && tool.getGovernance().idempotencyCapability()
                        == IdempotencyCapability.SERVER_RECEIPT
                        && tool.getGovernance().reconciliationCapability()
                        == ReconciliationCapability.QUERY_BY_EXECUTION_KEY
                        && tool.getGovernance().compensationCapability()
                        == CompensationCapability.STATE_RESTORE
                        && !text(tool.getParametersJson()).isBlank()
                        && !text(tool.getOutputSchemaJson()).isBlank());
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
