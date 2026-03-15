package cn.lgs.orbisops.trigger.application.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdminUserSecuritySettingsTest {

    @Test
    void shouldBoundCredentialPolicy() {
        AdminUserSecuritySettings settings = new AdminUserSecuritySettings(2, false);

        assertAll(
                () -> assertEquals(8, settings.minimumLength()),
                () -> assertFalse(settings.prehashedAllowed()),
                () -> assertEquals(256, new AdminUserSecuritySettings(1000, true).minimumLength()));
    }
}
