package cn.lgs.orbisops.trigger.ops.toolset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OpsToolsetRegistry {

    private final OpsBuiltInToolsetCatalog builtInCatalog;
    private final OpsToolsetDefinitionCopier copier;
    private final OpsExternalLocalProviderSettings externalLocalProviders;

    public OpsToolsetRegistry() {
        this(
                new OpsBuiltInToolsetCatalog(),
                new OpsToolsetDefinitionCopier(),
                OpsExternalLocalProviderSettings.compatibilityEnabled());
    }

    @Autowired
    public OpsToolsetRegistry(
            OpsExternalLocalProviderSettings externalLocalProviders) {
        this(
                new OpsBuiltInToolsetCatalog(),
                new OpsToolsetDefinitionCopier(),
                externalLocalProviders);
    }

    OpsToolsetRegistry(
            OpsBuiltInToolsetCatalog builtInCatalog,
            OpsToolsetDefinitionCopier copier) {
        this(
                builtInCatalog,
                copier,
                OpsExternalLocalProviderSettings.compatibilityEnabled());
    }

    OpsToolsetRegistry(
            OpsBuiltInToolsetCatalog builtInCatalog,
            OpsToolsetDefinitionCopier copier,
            OpsExternalLocalProviderSettings externalLocalProviders) {
        if (builtInCatalog == null) {
            throw new IllegalArgumentException("BUILT_IN_TOOLSET_CATALOG_REQUIRED");
        }
        if (copier == null) {
            throw new IllegalArgumentException("TOOLSET_DEFINITION_COPIER_REQUIRED");
        }
        if (externalLocalProviders == null) {
            throw new IllegalArgumentException("EXTERNAL_LOCAL_PROVIDER_SETTINGS_REQUIRED");
        }
        this.builtInCatalog = builtInCatalog;
        this.copier = copier;
        this.externalLocalProviders = externalLocalProviders;
    }

    public List<OpsToolsetDefinition> listBuiltInToolsets() {
        return rawBuiltInToolsets().stream()
                .filter(externalLocalProviders::allowsToolset)
                .toList();
    }

    List<OpsToolsetDefinition> rawBuiltInToolsets() {
        return builtInCatalog.definitions().stream()
                .map(copier::copy)
                .toList();
    }

    public List<OpsToolsetDefinition> listEffectiveToolsets(
            String projectId,
            List<OpsToolsetDefinition> customToolsets) {
        List<OpsToolsetDefinition> result = new ArrayList<>(listBuiltInToolsets());
        if (customToolsets != null) {
            result.addAll(customToolsets.stream().map(copier::copy).toList());
        }
        return result.stream()
                .filter(OpsToolsetDefinition::isEnabled)
                .toList();
    }

    public Optional<OpsToolDefinition> findTool(
            List<OpsToolsetDefinition> toolsets,
            String toolsetId,
            String toolName) {
        return toolsets.stream()
                .filter(item -> value(item.getToolsetId()).equals(toolsetId))
                .flatMap(item -> item.getTools().stream())
                .filter(tool -> value(tool.getToolName()).equals(toolName))
                .findFirst();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
