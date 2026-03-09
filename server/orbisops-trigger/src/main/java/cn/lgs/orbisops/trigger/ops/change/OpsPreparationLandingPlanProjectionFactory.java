package cn.lgs.orbisops.trigger.ops.change;

import java.util.LinkedHashMap;
import java.util.Map;

/** Projects the typed PREPARE plan envelope into the persisted Landing plan contract. */
final class OpsPreparationLandingPlanProjectionFactory {

    Map<String, Object> create(OpsPreparationPlanEnvelopeFactory.Envelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("PREPARATION_LANDING_PLAN_REQUIRED");
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("approvalBoundary", envelope.approvalBoundary());
        plan.put("preferredPlan", envelope.preferredPlan());
        plan.put("adjustmentPolicy", envelope.adjustmentPolicy());
        plan.put("replanTriggers", envelope.replanTriggers());
        return plan;
    }
}
