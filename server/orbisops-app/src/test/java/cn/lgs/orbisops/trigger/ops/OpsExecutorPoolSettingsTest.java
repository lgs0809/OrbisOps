package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsExecutorPoolSettingsTest {

    @Test
    void resolvesNullsAndBoundsPoolDimensions() {
        OpsExecutorPoolSettings settings = OpsExecutorPoolSettings.resolve(
                0,
                1,
                0,
                0L,
                null,
                "ops-test-",
                3,
                6,
                32,
                "AbortPolicy");

        assertEquals(1, settings.corePoolSize());
        assertEquals(1, settings.maxPoolSize());
        assertEquals(1, settings.queueCapacity());
        assertEquals(1L, settings.keepAliveSeconds());
        assertEquals("AbortPolicy", settings.rejectionPolicy());
    }

    @Test
    void preservesExplicitBlankPolicyForFactoryFallbackCompatibility() {
        OpsExecutorPoolSettings settings = OpsExecutorPoolSettings.resolve(
                null, null, null, null, " ", "ops-test-",
                2, 4, 16, "AbortPolicy");

        assertEquals("", settings.rejectionPolicy());
        assertEquals(2, settings.corePoolSize());
        assertEquals(4, settings.maxPoolSize());
        assertEquals(16, settings.queueCapacity());
    }
}
