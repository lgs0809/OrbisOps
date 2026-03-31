package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsLocalAdapterSettingsTest {

    @Test
    void defaultsMustPreserveHistoricalConfiguration() {
        OpsLocalAdapterSettings settings = OpsLocalAdapterSettings.defaults();

        assertEquals("http://127.0.0.1:9090", settings.prometheusUrl());
        assertEquals("http://127.0.0.1:9200", settings.elasticsearchUrl());
        assertEquals("", settings.elasticsearchIndex());
        assertEquals("", settings.elasticsearchIndexWhitelist());
        assertEquals("./logs", settings.allowedLogRoots());
        assertEquals("./", settings.allowedDockerComposeRoots());
        assertEquals(8, settings.timeoutSeconds());
        assertEquals(200, settings.maxRows());
        assertEquals(65_536, settings.maxResponseBytes());
    }

    @Test
    void valuesMustNormalizeUrlsAndApplySafetyMinimums() {
        OpsLocalAdapterSettings settings = new OpsLocalAdapterSettings(
                " http://prom/// ",
                " http://es// ",
                " index ",
                " one,two ",
                " ",
                " ",
                0,
                0,
                10);

        assertEquals("http://prom", settings.prometheusUrl());
        assertEquals("http://es", settings.elasticsearchUrl());
        assertEquals("index", settings.elasticsearchIndex());
        assertEquals("one,two", settings.elasticsearchIndexWhitelist());
        assertEquals("./logs", settings.allowedLogRoots());
        assertEquals("./", settings.allowedDockerComposeRoots());
        assertEquals(1, settings.timeoutSeconds());
        assertEquals(1, settings.maxRows());
        assertEquals(1024, settings.maxResponseBytes());
    }
}
