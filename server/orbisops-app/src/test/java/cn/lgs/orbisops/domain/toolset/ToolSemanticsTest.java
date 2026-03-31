package cn.lgs.orbisops.domain.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSemanticsTest {

    @Test
    void namedFactoriesExposeStableEffectsAndRetryPolicy() {
        ToolSemantics read = ToolSemantics.readOnlyTool();
        ToolSemantics validation = ToolSemantics.validation();
        ToolSemantics repair = ToolSemantics.repairWorkspaceWrite();
        ToolSemantics workflow = ToolSemantics.workflowCommand();
        ToolSemantics targetWrite = ToolSemantics.targetResourceWrite();

        assertTrue(read.readOnly());
        assertTrue(read.idempotent());
        assertTrue(read.retrySafe());
        assertEquals(ToolRiskLevel.LOW, read.riskLevel());

        assertFalse(validation.readOnly());
        assertTrue(validation.idempotent());
        assertTrue(validation.retrySafe());

        assertTrue(repair.writesRepairWorkspace());
        assertFalse(repair.retrySafe());
        assertFalse(workflow.idempotent());

        assertTrue(targetWrite.writesTargetResource());
        assertTrue(targetWrite.requiresChangePackage());
        assertTrue(targetWrite.requiresApproval());
        assertEquals(ToolRiskLevel.HIGH, targetWrite.riskLevel());
    }

    @Test
    void conflictingEffectsAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ToolSemantics(
                        true, true, false, false, false,
                        ToolRiskLevel.MEDIUM, false, false));

        assertEquals("TOOL_EFFECT_CONFLICT", error.getMessage());
    }

    @Test
    void targetWritesRequireChangePackageAndApproval() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ToolSemantics(
                        false, false, true, true, false,
                        ToolRiskLevel.HIGH, false, false));

        assertEquals("TARGET_WRITE_GOVERNANCE_REQUIRED", error.getMessage());
    }

    @Test
    void retrySafeRequiresIdempotency() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ToolSemantics(
                        false, false, false, false, false,
                        ToolRiskLevel.LOW, false, true));

        assertEquals("RETRY_SAFE_REQUIRES_IDEMPOTENT", error.getMessage());
    }
}
