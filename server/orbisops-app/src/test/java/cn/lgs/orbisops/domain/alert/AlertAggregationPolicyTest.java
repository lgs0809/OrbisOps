package cn.lgs.orbisops.domain.alert;

import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateState;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationAction;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationSignal;
import cn.lgs.orbisops.domain.alert.service.AlertAggregationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertAggregationPolicyTest {

    private final AlertAggregationPolicy policy = new AlertAggregationPolicy();

    @Test
    void firstFiringDispatchesImmediatelyWithStableKeys() {
        AlertAggregationSignal signal = signal("firing", "warning", "mysql");
        String key = policy.seed(signal).aggregateKey();
        AlertAggregationPlan plan = policy.plan(true, snapshot(key, AlertAggregateState.FIRING, 50), signal);

        assertEquals(AlertAggregationAction.MARK_FIRST, plan.action());
        assertEquals(AlertAggregateEventType.FIRST, plan.eventType());
        assertTrue(policy.decision(snapshot(key, AlertAggregateState.FIRING, 50), plan.eventType()).dispatchNow());
        assertEquals(key, policy.seed(signal).aggregateKey());
    }

    @Test
    void initialRecoveryIsIgnored() {
        AlertAggregationSignal signal = signal("resolved", "warning", "mysql");
        AlertAggregationPlan plan = policy.plan(
                true,
                snapshot(policy.seed(signal).aggregateKey(), AlertAggregateState.RESOLVED, 50),
                signal);

        assertEquals(AlertAggregationAction.NONE, plan.action());
        assertEquals(AlertAggregateEventType.IGNORED_RECOVERY, plan.eventType());
        assertFalse(plan.eventType().dispatchNow());
    }

    @Test
    void firingAggregateSupportsRecoveryRecurrenceEscalationAndDuplicate() {
        String key = policy.seed(signal("firing", "warning", "mysql")).aggregateKey();
        AlertAggregateSnapshot firing = snapshot(key, AlertAggregateState.FIRING, 50);
        AlertAggregateSnapshot resolved = snapshot(key, AlertAggregateState.RESOLVED, 50);

        assertEquals(AlertAggregateEventType.RECOVERY,
                policy.plan(false, firing, signal("resolved", "warning", "mysql")).eventType());
        assertEquals(AlertAggregateEventType.RECURRENCE,
                policy.plan(false, resolved, signal("firing", "warning", "mysql")).eventType());
        assertEquals(AlertAggregateEventType.ESCALATION,
                policy.plan(false, firing, signal("firing", "critical", "redis")).eventType());
        AlertAggregationPlan duplicate = policy.plan(
                false, firing, signal("firing", "warning", "redis"));
        assertEquals(AlertAggregateEventType.DUPLICATE, duplicate.eventType());
        assertEquals(AlertAggregationAction.SCHEDULE_SUMMARY, duplicate.action());
        assertEquals(List.of("mysql", "redis"), duplicate.affectedResources());
    }

    @Test
    void severityAndOperationalBoundsAreOwnedByDomain() {
        assertEquals(100, policy.severityRank("P0"));
        assertEquals(80, policy.severityRank("error"));
        assertEquals(50, policy.severityRank("warn"));
        assertEquals(20, policy.severityRank("info"));
        assertEquals(1, policy.claimLimit(0));
        assertEquals(100, policy.claimLimit(999));
        assertEquals(30, policy.staleClaimSeconds(1));
        assertEquals(1, policy.debounceSeconds(0));
    }

    private AlertAggregationSignal signal(String state, String severity, String resource) {
        return policy.signal(
                "project-a", 7L, "fingerprint-1", state, severity, resource,
                Map.of("status", state), 120, 900);
    }

    private AlertAggregateSnapshot snapshot(String key, AlertAggregateState state, int severityRank) {
        return new AlertAggregateSnapshot(
                key,
                "project-a",
                7L,
                "fingerprint-1",
                state,
                "WARNING",
                severityRank,
                3,
                1,
                List.of("mysql"),
                Map.of("status", state.name()),
                5);
    }
}
