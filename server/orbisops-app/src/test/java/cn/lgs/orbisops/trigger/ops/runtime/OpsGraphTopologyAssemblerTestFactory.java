package cn.lgs.orbisops.trigger.ops.runtime;

/** Creates the production-shaped graph topology object graph for tests. */
final class OpsGraphTopologyAssemblerTestFactory {

    private OpsGraphTopologyAssemblerTestFactory() {
    }

    static OpsGraphTopologyAssembler create(
            OpsAnalysisRoutingPolicy routingPolicy,
            OpsGraphConditionEvaluator conditionEvaluator,
            OpsGraphRuntimeStateManager runtimeStateManager) {
        return new OpsGraphTopologyAssembler(
                routingPolicy,
                conditionEvaluator,
                runtimeStateManager,
                new OpsGraphFeedbackLoopPolicy(
                        routingPolicy,
                        conditionEvaluator));
    }
}
