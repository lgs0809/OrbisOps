package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageToolProvider;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairToolProvider;
import cn.lgs.orbisops.trigger.ops.source.OpsProjectServiceCatalogService;

import java.util.List;
import java.util.function.Supplier;

/** Compatibility facade over the ordered runtime tool contributor registry. */
public final class OpsRuntimeBuiltInToolContributor {

    private final OpsRuntimeToolContributorRegistry contributorRegistry;

    public OpsRuntimeBuiltInToolContributor(
            OpsRuntimeToolContributorRegistry contributorRegistry) {
        if (contributorRegistry == null) {
            throw new IllegalArgumentException("RUNTIME_TOOL_CONTRIBUTOR_REGISTRY_REQUIRED");
        }
        this.contributorRegistry = contributorRegistry;
    }

    public OpsRuntimeBuiltInToolContributor(
            Supplier<OpsRepairToolProvider> repairToolProvider,
            Supplier<OpsProjectServiceCatalogService> projectServiceCatalog,
            Supplier<OpsChangePackageToolProvider> changePackageToolProvider,
            Supplier<OpsInspectionTaskToolProvider> inspectionTaskToolProvider,
            Supplier<OpsChannelToolProvider> channelToolProvider) {
        this(new OpsRuntimeToolContributorRegistry(List.of(
                new OpsRepairRuntimeToolContributor(repairToolProvider, projectServiceCatalog),
                new OpsChangePackageRuntimeToolContributor(changePackageToolProvider),
                new OpsInspectionTaskRuntimeToolContributor(inspectionTaskToolProvider),
                new OpsChannelRuntimeToolContributor(channelToolProvider))));
    }

    public void contribute(OpsRuntimeResourceContext context) {
        if (context != null) {
            List<String> contributorIds = contributorRegistry.orderedContributorIds();
            context.getMetadata().put("runtimeToolContributors", contributorIds);
            context.record(OpsRuntimeEvent.builder()
                    .eventType("RUNTIME_TOOL_CONTRIBUTORS")
                    .status("SUCCEEDED")
                    .summary("Runtime Tool Contributor 装配完成。")
                    .payload(java.util.Map.of(
                            "owner", context.ownerLabel(),
                            "contributors", contributorIds))
                    .build());
        }
        contributorRegistry.contribute(context);
    }
}
