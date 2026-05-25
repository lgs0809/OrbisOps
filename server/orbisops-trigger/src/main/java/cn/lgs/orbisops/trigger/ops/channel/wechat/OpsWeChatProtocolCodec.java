package cn.lgs.orbisops.trigger.ops.channel.wechat;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Official Account secure-callback codec. It intentionally supports text DMs only. */
final class OpsWeChatProtocolCodec {

    Optional<ChannelInboundEnvelope> inbound(String verificationToken,
                                             String encodingAesKey,
                                             String appId,
                                             ChannelInboundPacket packet) {
        if (packet == null || packet.body().length == 0) throw new IllegalArgumentException("WECHAT_CALLBACK_BODY_REQUIRED");
        String timestamp = packet.firstHeader("X-WeChat-Timestamp");
        String nonce = packet.firstHeader("X-WeChat-Nonce");
        String messageSignature = packet.firstHeader("X-WeChat-Msg-Signature");
        Element outer = root(packet.body());
        String encrypted = required(text(outer, "Encrypt"), "WECHAT_ENCRYPTED_PAYLOAD_REQUIRED");
        verifySignature(verificationToken, timestamp, nonce, encrypted, messageSignature);
        byte[] plain = decrypt(encodingAesKey, encrypted, appId);
        Element message = root(plain);
        String messageType = text(message, "MsgType").toLowerCase();
        if (!"text".equals(messageType)) return Optional.empty();

        String fromUser = required(text(message, "FromUserName"), "WECHAT_SENDER_OPENID_REQUIRED");
        String content = required(text(message, "Content"), "WECHAT_TEXT_CONTENT_REQUIRED");
        long createTime = positiveLong(text(message, "CreateTime"), "WECHAT_CREATE_TIME_REQUIRED");
        String messageId = text(message, "MsgId");
        if (messageId.isBlank()) messageId = fromUser + ":" + createTime;
        ChannelConversationRef conversation = new ChannelConversationRef(fromUser, ChannelConversationRef.ConversationKind.DIRECT);
        ChannelMessageRef messageRef = new ChannelMessageRef(messageId, conversation);
        ChannelExternalPrincipal sender = new ChannelExternalPrincipal(
                fromUser, "", ChannelExternalPrincipal.PrincipalKind.USER);
        return Optional.of(new ChannelInboundEnvelope(
                messageRef,
                sender,
                ChannelRichContent.text(content),
                List.of(),
                Instant.ofEpochSecond(createTime),
                messageId));
    }

    void validateEncodingAesKey(String encodingAesKey) {
        key(encodingAesKey);
    }

    String verifyChallenge(String verificationToken,
                           String encodingAesKey,
                           String appId,
                           String timestamp,
                           String nonce,
                           String messageSignature,
                           String echoStr) {
        String encryptedEcho = required(echoStr, "WECHAT_ECHOSTR_REQUIRED");
        verifySignature(verificationToken, timestamp, nonce, encryptedEcho, messageSignature);
        return new String(decrypt(encodingAesKey, encryptedEcho, appId), StandardCharsets.UTF_8);
    }

    String signature(String verificationToken, String timestamp, String nonce, String encrypted) {
        List<String> values = new ArrayList<>(List.of(
                required(verificationToken, "WECHAT_VERIFICATION_TOKEN_REQUIRED"),
                required(timestamp, "WECHAT_TIMESTAMP_REQUIRED"),
                required(nonce, "WECHAT_NONCE_REQUIRED"),
                required(encrypted, "WECHAT_ENCRYPTED_PAYLOAD_REQUIRED")));
        values.sort(Comparator.naturalOrder());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(String.join("", values).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("WECHAT_SIGNATURE_ALGORITHM_UNAVAILABLE", failure);
        }
    }

    private void verifySignature(String verificationToken,
                                 String timestamp,
                                 String nonce,
                                 String encrypted,
                                 String suppliedSignature) {
        String expected = signature(verificationToken, timestamp, nonce, encrypted);
        String supplied = required(suppliedSignature, "WECHAT_MSG_SIGNATURE_REQUIRED").toLowerCase();
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("WECHAT_CALLBACK_SIGNATURE_INVALID");
        }
    }

    private byte[] decrypt(String encodingAesKey, String encrypted, String expectedAppId) {
        byte[] key = key(encodingAesKey);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            byte[] padded = cipher.doFinal(Base64.getDecoder().decode(required(encrypted, "WECHAT_ENCRYPTED_PAYLOAD_REQUIRED")));
            byte[] clear = unpad(padded);
            if (clear.length < 20) throw new SecurityException("WECHAT_CALLBACK_PAYLOAD_INVALID");
            int messageLength = ByteBuffer.wrap(clear, 16, 4).getInt();
            if (messageLength < 0 || 20L + messageLength > clear.length) {
                throw new SecurityException("WECHAT_CALLBACK_MESSAGE_LENGTH_INVALID");
            }
            byte[] message = java.util.Arrays.copyOfRange(clear, 20, 20 + messageLength);
            String appId = new String(clear, 20 + messageLength, clear.length - 20 - messageLength, StandardCharsets.UTF_8);
            if (!required(expectedAppId, "WECHAT_APP_ID_REQUIRED").equals(appId)) {
                throw new SecurityException("WECHAT_CALLBACK_APP_ID_MISMATCH");
            }
            return message;
        } catch (SecurityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SecurityException("WECHAT_CALLBACK_DECRYPT_FAILED", failure);
        }
    }

    private byte[] key(String encodingAesKey) {
        String value = required(encodingAesKey, "WECHAT_ENCODING_AES_KEY_REQUIRED");
        String padded = value;
        while (padded.length() % 4 != 0) padded += "=";
        try {
            byte[] decoded = Base64.getDecoder().decode(padded);
            if (decoded.length != 32) throw new IllegalArgumentException("WECHAT_ENCODING_AES_KEY_INVALID");
            return decoded;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("WECHAT_ENCODING_AES_KEY_INVALID", failure);
        }
    }

    private byte[] unpad(byte[] value) {
        if (value == null || value.length == 0) throw new SecurityException("WECHAT_CALLBACK_PADDING_INVALID");
        int pad = value[value.length - 1] & 0xff;
        if (pad < 1 || pad > 32 || pad > value.length) throw new SecurityException("WECHAT_CALLBACK_PADDING_INVALID");
        for (int index = value.length - pad; index < value.length; index++) {
            if ((value[index] & 0xff) != pad) throw new SecurityException("WECHAT_CALLBACK_PADDING_INVALID");
        }
        return java.util.Arrays.copyOf(value, value.length - pad);
    }

    private Element root(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            return document.getDocumentElement();
        } catch (Exception failure) {
            throw new IllegalArgumentException("WECHAT_CALLBACK_XML_INVALID", failure);
        }
    }

    private String text(Element element, String tagName) {
        if (element == null) return "";
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) return "";
        String value = nodes.item(0).getTextContent();
        return value == null ? "" : value.trim();
    }

    private long positiveLong(String value, String reasonCode) {
        try {
            long parsed = Long.parseLong(required(value, reasonCode));
            if (parsed <= 0) throw new NumberFormatException("non-positive");
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(reasonCode, failure);
        }
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
