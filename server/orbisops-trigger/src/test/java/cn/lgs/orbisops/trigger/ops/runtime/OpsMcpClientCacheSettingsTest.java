package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsMcpClientCacheSettingsTest {

    @Test
    void normalizesBoundsAndPreservesLegacyOneSecondTtl() {
        OpsMcpClientCacheSettings normalized =
                new OpsMcpClientCacheSettings(0L, 100L);

        assertEquals(600L, normalized.ttlSeconds());
        assertEquals(60_000L, normalized.cleanupIntervalMillis());
        assertEquals(600_000L, normalized.ttlMillis());
        assertEquals(1L,
                OpsMcpClientCacheSettings.legacyConstructorDefaults().ttlSeconds());
    }
}
