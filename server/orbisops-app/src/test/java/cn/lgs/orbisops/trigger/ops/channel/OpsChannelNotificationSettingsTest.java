package cn.lgs.orbisops.trigger.ops.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsChannelNotificationSettingsTest {

    @Test
    void defaultsMustPreserveHistoricalConfiguration() {
        OpsChannelNotificationSettings settings = OpsChannelNotificationSettings.defaults();

        assertEquals(12_000, settings.maxMessageChars());
        assertEquals(8, settings.maxAttempts());
        assertEquals(120, settings.leaseSeconds());
    }

    @Test
    void valuesMustApplyStableSafetyClamps() {
        OpsChannelNotificationSettings minimum = new OpsChannelNotificationSettings(1, 0, 1);
        OpsChannelNotificationSettings maximum = new OpsChannelNotificationSettings(20_000, 99, 300);

        assertEquals(500, minimum.maxMessageChars());
        assertEquals(1, minimum.maxAttempts());
        assertEquals(15, minimum.leaseSeconds());
        assertEquals(20_000, maximum.maxMessageChars());
        assertEquals(20, maximum.maxAttempts());
        assertEquals(300, maximum.leaseSeconds());
    }
}
