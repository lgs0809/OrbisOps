package cn.lgs.orbisops.trigger.ops.runtime;

public interface OpsRuntimeToolContributor {

    String id();

    int order();

    OpsRuntimeToolContributorRequirement requirement();

    void contribute(OpsRuntimeResourceContext context);
}
