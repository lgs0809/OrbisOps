package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.channel.ChannelPayloadCipherPort;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Repository
public class OpsChannelPayloadCipher implements ChannelPayloadCipherPort {

    private static final String VERSION = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    public OpsChannelPayloadCipher(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean available() {
        try {
            return key().length == 32;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(value(associatedData).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value(plaintext).getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return VERSION + ":" + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("CHANNEL_INBOUND_ENCRYPTION_FAILED", e);
        }
    }

    @Override
    public String decrypt(String protectedPayload, String associatedData) {
        try {
            if (!StringUtils.hasText(protectedPayload) || !protectedPayload.startsWith(VERSION + ":")) {
                throw new IllegalArgumentException("CHANNEL_INBOUND_ENVELOPE_UNSUPPORTED");
            }
            byte[] envelope = Base64.getDecoder().decode(protectedPayload.substring(VERSION.length() + 1));
            if (envelope.length <= NONCE_BYTES) throw new IllegalArgumentException("CHANNEL_INBOUND_ENVELOPE_INVALID");
            byte[] nonce = Arrays.copyOfRange(envelope, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(envelope, NONCE_BYTES, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(value(associatedData).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CHANNEL_INBOUND_DECRYPTION_FAILED", e);
        }
    }

    private byte[] key() {
        String encoded = firstText(
                environment.getProperty("OPS_CHANNEL_INBOUND_ENCRYPTION_KEY"),
                System.getenv("OPS_CHANNEL_INBOUND_ENCRYPTION_KEY"));
        if (!StringUtils.hasText(encoded)) throw new IllegalStateException("CHANNEL_INBOUND_ENCRYPTION_KEY_MISSING");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CHANNEL_INBOUND_ENCRYPTION_KEY_INVALID", e);
        }
        if (decoded.length != 32) throw new IllegalStateException("CHANNEL_INBOUND_ENCRYPTION_KEY_INVALID_LENGTH");
        return decoded;
    }

    private String firstText(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value;
        return "";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
