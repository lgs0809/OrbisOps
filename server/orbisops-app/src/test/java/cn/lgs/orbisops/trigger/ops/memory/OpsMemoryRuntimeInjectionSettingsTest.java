package cn.lgs.orbisops.trigger.ops.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsMemoryRuntimeInjectionSettingsTest {

    @Test
    void boundsRuntimeSelectionCountAndAllowsExplicitDisable() {
        assertEquals(8, new OpsMemoryRuntimeInjectionSettings(-1).maxInjectionCount());
        assertEquals(8, new OpsMemoryRuntimeInjectionSettings(101).maxInjectionCount());
        assertEquals(0, new OpsMemoryRuntimeInjectionSettings(0).maxInjectionCount());
        assertEquals(24, new OpsMemoryRuntimeInjectionSettings(24).maxInjectionCount());
    }
}
