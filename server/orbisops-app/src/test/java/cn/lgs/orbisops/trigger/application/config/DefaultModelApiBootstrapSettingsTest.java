package cn.lgs.orbisops.trigger.application.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultModelApiBootstrapSettingsTest {

    @Test
    void trimsValuesAndKeepsSpringAndLegacyDefaultsDistinct() {
        DefaultModelApiBootstrapSettings normalized =
                new DefaultModelApiBootstrapSettings(
                        true,
                        " 1001 ",
                        " https://proxy.example.com/ ",
                        " secret ");

        assertEquals("1001", normalized.apiId());
        assertEquals(
                "https://proxy.example.com/",
                normalized.configuredBaseUrl());
        assertEquals("secret", normalized.configuredApiKey());
        assertFalse(DefaultModelApiBootstrapSettings.defaults().enabled());
        assertEquals(
                "1001",
                DefaultModelApiBootstrapSettings.defaults().apiId());
        assertEquals(
                "",
                DefaultModelApiBootstrapSettings
                        .legacyConstructorDefaults()
                        .apiId());
    }
}
