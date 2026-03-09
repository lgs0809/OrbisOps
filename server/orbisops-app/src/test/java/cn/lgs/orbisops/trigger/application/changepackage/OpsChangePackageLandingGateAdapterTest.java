package cn.lgs.orbisops.trigger.application.changepackage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChangePackageLandingGateAdapterTest {

    @Test
    void legacyConstructorRemainsDisabled() {
        OpsChangePackageLandingGateAdapter adapter =
                new OpsChangePackageLandingGateAdapter();

        assertFalse(adapter.enabled());
        assertThrows(IllegalStateException.class, () ->
                adapter.requireLandingEnabled());
    }

    @Test
    void typedSettingsCanExplicitlyEnableLanding() {
        OpsChangePackageLandingGateAdapter adapter =
                new OpsChangePackageLandingGateAdapter(
                        new OpsChangePackageLandingSettings(true));

        assertTrue(adapter.enabled());
        assertDoesNotThrow(() -> adapter.requireLandingEnabled());
    }
}
