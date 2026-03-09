package cn.lgs.orbisops.trigger.application.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsChannelInboundProtocolSettingsTest {

    @Test
    void defaultsPreserveExistingProtocolLimits() {
        OpsChannelInboundProtocolSettings settings =
                OpsChannelInboundProtocolSettings.defaults();

        assertAll(
                () -> assertEquals(300, settings.maxClockSkewSeconds()),
                () -> assertEquals(12000, settings.maxMessageChars()),
                () -> assertEquals(8192, settings.maxMetadataChars()));
    }

    @Test
    void valuesBelowExistingMinimumsAreClamped() {
        OpsChannelInboundProtocolSettings settings =
                new OpsChannelInboundProtocolSettings(1, 2, 3);

        assertAll(
                () -> assertEquals(30, settings.maxClockSkewSeconds()),
                () -> assertEquals(1000, settings.maxMessageChars()),
                () -> assertEquals(1024, settings.maxMetadataChars()));
    }
}
