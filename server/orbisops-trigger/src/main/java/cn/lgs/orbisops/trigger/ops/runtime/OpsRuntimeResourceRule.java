package cn.lgs.orbisops.trigger.ops.runtime;

public interface OpsRuntimeResourceRule {

    String name();

    void apply(OpsRuntimeResourceContext context);

}
