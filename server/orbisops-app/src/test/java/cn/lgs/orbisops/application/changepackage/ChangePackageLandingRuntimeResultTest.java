package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageLandingRuntimeResultTest {

    @Test
    void typedFieldsOverrideConflictingProtocolPayload() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "LANDING_FAILED");
        source.put("eventType", "WRONG_EVENT");
        source.put("reasonCode", "stale");
        source.put("summary", "stale summary");
        source.put("executedProductionAction", false);
        source.put("executorDetail", Map.of("attempt", 1));

        ChangePackageLandingRuntimeResult result = new ChangePackageLandingRuntimeResult(
                ChangePackageStatus.LANDED,
                "LANDING_SUCCEEDED",
                "",
                "done",
                true,
                source);

        assertEquals("LANDED", result.payload().get("status"));
        assertEquals("LANDING_SUCCEEDED", result.payload().get("eventType"));
        assertEquals("done", result.payload().get("summary"));
        assertEquals(true, result.payload().get("executedProductionAction"));
        assertFalse(result.payload().containsKey("reasonCode"));
        assertEquals(Map.of("attempt", 1), result.payload().get("executorDetail"));
    }

    @Test
    void payloadIsImmutableButMutableProjectionCanAddProcessContext() {
        ChangePackageLandingRuntimeResult result = new ChangePackageLandingRuntimeResult(
                ChangePackageStatus.NEEDS_REPLAN,
                "",
                "BOUNDARY_CHANGED",
                "",
                false,
                Map.of());

        assertEquals("LANDING_NEEDS_REPLAN", result.eventType());
        assertEquals(ChangePackageLandingRunStatus.NEEDS_REPLAN, result.runStatus());
        assertThrows(UnsupportedOperationException.class,
                () -> result.payload().put("landingRunId", "lr-1"));

        Map<String, Object> mutable = result.mutablePayload();
        mutable.put("landingRunId", "lr-1");
        assertEquals("lr-1", mutable.get("landingRunId"));
        assertFalse(result.payload().containsKey("landingRunId"));
    }

    @Test
    void operationFactUsesTypedRecoverySemantics() {
        LandingOperationFact fact = new LandingOperationFact(
                "op-1",
                LandingOperationFact.FactStatus.COMPLETED,
                LandingOperationFact.ExecutionStatus.SUCCEEDED,
                "",
                "result-1",
                "hash-1",
                Map.of("fact_status", "COMPLETED", "status", "SUCCEEDED"));

        assertTrue(fact.completed());
        assertTrue(fact.succeeded());
        assertFalse(fact.unknown());
    }

    @Test
    void unknownProtocolValuesFailClosedAsOther() {
        assertEquals(LandingOperationFact.FactStatus.OTHER,
                LandingOperationFact.FactStatus.from("new-fact-state"));
        assertEquals(LandingOperationFact.ExecutionStatus.OTHER,
                LandingOperationFact.ExecutionStatus.from("new-execution-state"));
    }
}
