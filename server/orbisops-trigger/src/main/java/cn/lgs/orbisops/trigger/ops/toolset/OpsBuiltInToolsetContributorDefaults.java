package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

/** Explicit bootstrap list for non-Spring compatibility construction. */
final class OpsBuiltInToolsetContributorDefaults {

    private OpsBuiltInToolsetContributorDefaults() {
    }

    static OpsBuiltInToolsetContributorRegistry registry() {
        return new OpsBuiltInToolsetContributorRegistry(List.of(
                new ObservabilityToolsetContributor(),
                new DatabaseToolsetContributor(),
                new CacheToolsetContributor(),
                new ContainerToolsetContributor(),
                new PlatformToolsetContributor(),
                new BusinessOperationsToolsetContributor(),
                new JavaServiceLandingToolsetContributor(),
                new RepairToolsetContributor(),
                new MemoryToolsetContributor(),
                new ChangePackageToolsetContributor(),
                new InspectionToolsetContributor(),
                new ChannelToolsetContributor(),
                new CapabilityManagementToolsetContributor(),
                new SkillToolsetContributor()));
    }
}
