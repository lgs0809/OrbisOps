package cn.lgs.orbisops.trigger.http.admin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRateLimitSettingsTest {

    @Test
    void shouldBoundLimitsAndAllowExplicitDisable() {
        OpsRateLimitSettings settings = new OpsRateLimitSettings(false, 0, 20_000);

        assertAll(
                () -> assertFalse(settings.enabled()),
                () -> assertEquals(1, settings.perUserPerMinute()),
                () -> assertEquals(10_000, settings.perAgentPathPerMinute()),
                () -> assertTrue(OpsRateLimitSettings.defaults().enabled()));
    }

    @Test
    void serviceShouldUseTypedLimits() {
        OpsRateLimitService service = new OpsRateLimitService(new OpsRateLimitSettings(true, 1, 1));

        assertTrue(service.tryAcquire("alice", "/ops"));
        assertFalse(service.tryAcquire("alice", "/ops"));
    }
}
