package cn.lgs.orbisops.domain.changepackage.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageCurrentStateTest {

    @Test
    void appliesCanonicalDefaultsForMissingJsonFields() {
        ChangePackageCurrentState state = ChangePackageCurrentState.fromSnapshot(Map.of(
                "riskLevel", "HIGH"));

        assertEquals("{}", state.value(ChangePackageCurrentField.EVIDENCE_JSON));
        assertEquals("[]", state.value(ChangePackageCurrentField.MCP_STEPS_JSON));
        assertEquals("{}", state.value(ChangePackageCurrentField.FAILURE_SUMMARY_JSON));
        assertNull(state.nullable(ChangePackageCurrentField.REPAIR_COMMIT));
    }

    @Test
    void riskLevelRemainsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangePackageCurrentState.fromSnapshot(Map.of("summary", "change")));
    }

    @Test
    void enforcesNullableFieldAccessMode() {
        ChangePackageCurrentState state = ChangePackageCurrentState.fromSnapshot(Map.of(
                "riskLevel", "MEDIUM",
                "repairCommit", "abc123"));

        assertEquals("abc123", state.nullable(ChangePackageCurrentField.REPAIR_COMMIT));
        assertThrows(IllegalArgumentException.class,
                () -> state.value(ChangePackageCurrentField.REPAIR_COMMIT));
        assertThrows(IllegalArgumentException.class,
                () -> state.nullable(ChangePackageCurrentField.EVIDENCE_JSON));
    }
}
