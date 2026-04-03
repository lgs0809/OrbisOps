package cn.lgs.orbisops.domain.changepackage.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageLandingCompletionTest {

    @Test
    void landedCompletionRequiresNoFailureSummaryAndDefensivelyCopiesResult() {
        Map<String, Object> result = new LinkedHashMap<>(Map.of("status", "LANDED"));
        ChangePackageLandingCompletion completion = new ChangePackageLandingCompletion(
                ChangePackageStatus.LANDED, "lr-1", result, Map.of());
        result.put("status", "MUTATED");

        assertEquals("LANDED", completion.result().get("status"));
        assertEquals(Map.of(), completion.failureSummary());
        assertThrows(UnsupportedOperationException.class,
                () -> completion.result().put("extra", true));
    }

    @Test
    void failedCompletionRequiresStructuredFailureSummary() {
        assertThrows(IllegalArgumentException.class, () -> new ChangePackageLandingCompletion(
                ChangePackageStatus.LANDING_FAILED, "lr-1", Map.of("status", "FAILED"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ChangePackageLandingCompletion(
                ChangePackageStatus.LANDED, "lr-1", Map.of(), Map.of("error", "unexpected")));
    }

    @Test
    void runningStatusCannotBePersistedAsCompletion() {
        assertThrows(IllegalArgumentException.class, () -> new ChangePackageLandingCompletion(
                ChangePackageStatus.LANDING_RUNNING, "lr-1", Map.of(), Map.of("error", "invalid")));
    }
}
