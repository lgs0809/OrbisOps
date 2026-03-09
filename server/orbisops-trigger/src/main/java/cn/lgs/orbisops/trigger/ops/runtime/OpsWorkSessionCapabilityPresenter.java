package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.ArrayList;
import java.util.Map;

/** Presents the effective runtime rule tree and registered engine adapters. */
final class OpsWorkSessionCapabilityPresenter {

    private final OpsAgentRuntimeRuleRouter ruleRouter;
    private final OpsRuntimeEngineDispatcher engineDispatcher;

    OpsWorkSessionCapabilityPresenter(OpsWorkSessionRuntimeAssembly assembly) {
        this.ruleRouter = assembly.ruleRouter();
        this.engineDispatcher = assembly.engineDispatcher();
    }

    Map<String, Object> capabilities() {
        return ruleRouter.describeRuleTree(
                new ArrayList<>(engineDispatcher.adapterKeys()));
    }
}
