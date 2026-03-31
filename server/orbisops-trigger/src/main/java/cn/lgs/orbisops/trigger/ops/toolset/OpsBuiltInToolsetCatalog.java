package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

/** Compatibility facade over the ordered built-in Toolset contributor registry. */
public final class OpsBuiltInToolsetCatalog {

    private final OpsBuiltInToolsetContributorRegistry registry;

    public OpsBuiltInToolsetCatalog() {
        this(OpsBuiltInToolsetContributorDefaults.registry());
    }

    OpsBuiltInToolsetCatalog(OpsBuiltInToolsetContributorRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("BUILT_IN_TOOLSET_CONTRIBUTOR_REGISTRY_REQUIRED");
        }
        this.registry = registry;
    }

    public List<OpsToolsetDefinition> definitions() {
        return registry.definitions();
    }
}
