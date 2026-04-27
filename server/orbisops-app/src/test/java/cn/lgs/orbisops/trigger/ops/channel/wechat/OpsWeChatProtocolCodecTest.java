package cn.lgs.orbisops.trigger.ops.channel.wechat;

import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWeChatProtocolCodecTest {

    private static final String VERIFICATION = String.join("-", "fixture", "verification", "value");
    private static final String APP_ID = "wx-test-app";
    private static final String AES_KEY = Base64.getEncoder().withoutPadding().encodeToString(keyBytes());
    private static final String TIMESTAMP = "1786953600";
    private static final String NONCE = "nonce-42";

    private final OpsWeChatProtocolCodec codec = new OpsWeChatProtocolCodec();

    @Test
    void secureTextCallbackDecryptsIntoCanonicalDirectMessage() throws Exception {
        String xml = "<xml>"
                + "<ToUserName><![CDATA[official-account]]></ToUserName>"
                + "<FromUserName><![CDATA[openid-user-1]]></FromUserName>"
                + "<CreateTime>1786953600</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[check checkout errors]]></Content>"
                + "<MsgId>90001</MsgId>"
                + "</xml>";
        String encrypted = encrypt(xml, APP_ID);
        ChannelInboundPacket packet = packet(encrypted, codec.signature(VERIFICATION, TIMESTAMP, NONCE, encrypted));

        var decoded = codec.inbound(VERIFICATION, AES_KEY, APP_ID, packet);

        assertTrue(decoded.isPresent());
        assertEquals("90001", decoded.get().message().externalMessageId());
        assertEquals("openid-user-1", decoded.get().message().conversation().externalConversationId());
        assertEquals("openid-user-1", decoded.get().sender().externalPrincipalId());
        assertEquals("check checkout errors", decoded.get().content().plainText());
    }

    @Test
    void invalidMessageSignatureIsRejectedBeforeDecrypt() throws Exception {
        String encrypted = encrypt("<xml><MsgType>text</MsgType></xml>", APP_ID);
        ChannelInboundPacket packet = packet(encrypted, "0000000000000000000000000000000000000000");

        assertEquals("WECHAT_CALLBACK_SIGNATURE_INVALID",
                assertThrows(SecurityException.class, () -> codec.inbound(VERIFICATION, AES_KEY, APP_ID, packet)).getMessage());
    }

    @Test
    void embeddedAppIdMismatchIsRejected() throws Exception {
        String xml = "<xml><FromUserName>openid</FromUserName><CreateTime>1786953600</CreateTime>"
                + "<MsgType>text</MsgType><Content>hello</Content><MsgId>1</MsgId></xml>";
        String encrypted = encrypt(xml, "wx-other-app");
        ChannelInboundPacket packet = packet(encrypted, codec.signature(VERIFICATION, TIMESTAMP, NONCE, encrypted));

        assertEquals("WECHAT_CALLBACK_APP_ID_MISMATCH",
                assertThrows(SecurityException.class, () -> codec.inbound(VERIFICATION, AES_KEY, APP_ID, packet)).getMessage());
    }

    @Test
    void nonTextCallbackIsAuthenticatedButIgnored() throws Exception {
        String xml = "<xml><FromUserName>openid</FromUserName><CreateTime>1786953600</CreateTime>"
                + "<MsgType>image</MsgType><MediaId>media-1</MediaId><MsgId>2</MsgId></xml>";
        String encrypted = encrypt(xml, APP_ID);
        ChannelInboundPacket packet = packet(encrypted, codec.signature(VERIFICATION, TIMESTAMP, NONCE, encrypted));

        assertTrue(codec.inbound(VERIFICATION, AES_KEY, APP_ID, packet).isEmpty());
    }

    @Test
    void secureUrlChallengeIsVerifiedAndDecrypted() throws Exception {
        String encrypted = encrypt("challenge-ok", APP_ID);
        String signature = codec.signature(VERIFICATION, TIMESTAMP, NONCE, encrypted);

        assertEquals("challenge-ok",
                codec.verifyChallenge(VERIFICATION, AES_KEY, APP_ID, TIMESTAMP, NONCE, signature, encrypted));
    }

    @Test
    void encodingAesKeyMustDecodeToExactlyThirtyTwoBytes() {
        codec.validateEncodingAesKey(AES_KEY);
        assertThrows(IllegalArgumentException.class, () -> codec.validateEncodingAesKey("too-short"));
        assertFalse(AES_KEY.endsWith("="));
    }

    private ChannelInboundPacket packet(String encrypted, String signature) {
        String outer = "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>";
        return new ChannelInboundPacket("application/xml", outer.getBytes(StandardCharsets.UTF_8), List.of(
                new ChannelInboundPacket.TransportHeader("X-WeChat-Timestamp", TIMESTAMP),
                new ChannelInboundPacket.TransportHeader("X-WeChat-Nonce", NONCE),
                new ChannelInboundPacket.TransportHeader("X-WeChat-Msg-Signature", signature)));
    }

    private String encrypt(String message, String appId) throws Exception {
        byte[] key = Base64.getDecoder().decode(AES_KEY + "=");
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] appIdBytes = appId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer clear = ByteBuffer.allocate(16 + 4 + messageBytes.length + appIdBytes.length);
        clear.put(new byte[16]);
        clear.putInt(messageBytes.length);
        clear.put(messageBytes);
        clear.put(appIdBytes);
        byte[] padded = pad(clear.array());
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    }

    private byte[] pad(byte[] value) {
        int pad = 32 - (value.length % 32);
        byte[] result = java.util.Arrays.copyOf(value, value.length + pad);
        java.util.Arrays.fill(result, value.length, result.length, (byte) pad);
        return result;
    }

    private static byte[] keyBytes() {
        byte[] value = new byte[32];
        for (int index = 0; index < value.length; index++) value[index] = (byte) (index + 1);
        return value;
    }
}
