package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChannelPayloadCipherTest {

    @Test
    void aesGcmEnvelopeDoesNotExposePlaintextAndBindsAssociatedData() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        OpsChannelPayloadCipher cipher = new OpsChannelPayloadCipher(
                new MockEnvironment().withProperty("OPS_CHANNEL_INBOUND_ENCRYPTION_KEY", key));

        String encrypted = cipher.encrypt("password=secret-value", "channel-1\nmessage-1");

        assertTrue(cipher.available());
        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("secret-value"));
        assertEquals("password=secret-value", cipher.decrypt(encrypted, "channel-1\nmessage-1"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, "channel-1\nother-message"));
    }

    @Test
    void missingOrInvalidKeyIsUnavailableAndFailsClosed() {
        OpsChannelPayloadCipher missing = new OpsChannelPayloadCipher(new MockEnvironment());
        OpsChannelPayloadCipher invalid = new OpsChannelPayloadCipher(
                new MockEnvironment().withProperty("OPS_CHANNEL_INBOUND_ENCRYPTION_KEY", "not-base64"));

        assertFalse(missing.available());
        assertFalse(invalid.available());
        assertThrows(IllegalStateException.class, () -> missing.encrypt("payload", "aad"));
    }
}
