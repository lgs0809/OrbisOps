package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolExposureSettingsTest {

    @Test
    void normalizesModeWithoutCarryingKeywordAuthorityConfiguration() {
        ToolExposureSettings settings = ToolExposureSettings.fromRaw(" ", true);

        assertEquals("analysis-only", settings.actionMode());
        assertTrue(settings.analysisOnly());
        assertTrue(settings.enforceReadOnlyTools());
    }

    @Test
    void nonAnalysisModeDoesNotClaimReadOnlyBoundary() {
        ToolExposureSettings settings = ToolExposureSettings.fromRaw(
                "controlled-write",
                true);

        assertFalse(settings.analysisOnly());
    }
}
