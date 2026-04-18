package cn.lgs.orbisops.domain.changepackage.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageValidationFailureTest {

    @Test
    void normalizesAndDefensivelyCopiesFailureSummary() {
        Map<String, Object> summary = new LinkedHashMap<>(Map.of(
                "reasonCode", "POLICY_BLOCKED",
                "status", "FAILED"));
        ChangePackageValidationFailure failure = new ChangePackageValidationFailure(
                " needs_refinement ", " policy_blocked ", summary);
        summary.put("status", "MUTATED");

        assertEquals("needs_refinement", failure.assessment());
        assertEquals("policy_blocked", failure.reasonCode());
        assertEquals("FAILED", failure.failureSummary().get("status"));
        assertEquals(ChangePackageStatus.VALIDATION_FAILED, failure.status());
        assertThrows(UnsupportedOperationException.class,
                () -> failure.failureSummary().put("extra", true));
    }

    @Test
    void requiresAssessmentReasonAndStructuredSummary() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangePackageValidationFailure("", "POLICY_BLOCKED", Map.of("status", "FAILED")));
        assertThrows(IllegalArgumentException.class,
                () -> new ChangePackageValidationFailure("NEEDS_REFINEMENT", "", Map.of("status", "FAILED")));
        assertThrows(IllegalArgumentException.class,
                () -> new ChangePackageValidationFailure("NEEDS_REFINEMENT", "POLICY_BLOCKED", Map.of()));
    }
}
