package cn.lgs.orbisops.trigger.ops.channel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChannelOutboxMetadataCodecTest {

    private final OpsChannelOutboxMetadataCodec codec = new OpsChannelOutboxMetadataCodec();

    @Test
    void encodeAndDecodeMustPreserveStableMetadataIncludingNullValues() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("messageType", "CHAT_REPLY");
        metadata.put("optional", null);

        Map<String, Object> decoded = codec.decode(codec.encode(metadata));

        assertEquals("CHAT_REPLY", decoded.get("messageType"));
        assertTrue(decoded.containsKey("optional"));
        assertNull(decoded.get("optional"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("changed", true));
    }

    @Test
    void blankAndInvalidJsonMustDegradeToEmptyMetadata() {
        assertTrue(codec.decode("").isEmpty());
        assertTrue(codec.decode("not-json").isEmpty());
        assertEquals("{}", codec.encode(null));
    }
}
