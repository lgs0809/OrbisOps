package cn.lgs.orbisops.application.channel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelDeliveryResultTest {

    @Test
    void typedDeliveryStatusOverridesConflictingProtocolPayload() {
        ChannelDeliveryResult result = new ChannelDeliveryResult(true,
                Map.of("delivered", false, "externalMessageId", "remote-1"));

        assertEquals(true, result.delivered());
        assertEquals(true, result.payload().get("delivered"));
        assertEquals("remote-1", result.payload().get("externalMessageId"));
    }

    @Test
    void payloadIsDefensivelyCopiedAndImmutable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("externalMessageId", "remote-1");

        ChannelDeliveryResult result = new ChannelDeliveryResult(false, source);
        source.put("externalMessageId", "changed");

        assertEquals("remote-1", result.payload().get("externalMessageId"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.payload().put("new", "value"));
    }
}
