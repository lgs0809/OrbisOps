package cn.lgs.orbisops.trigger.ops.channel;

import com.alibaba.fastjson.JSON;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class OpsChannelSignature {
    private OpsChannelSignature() { }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("渠道签名计算失败", e);
        }
    }

    public static boolean verify(String secret, String payload, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(sign(secret, payload).getBytes(StandardCharsets.UTF_8), supplied.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String inboundPayload(OpsChannelMessage message, long timestamp) {
        if (message == null) {
            throw new IllegalArgumentException("渠道消息不能为空");
        }
        return String.join(".",
                value(message.externalMessageId()),
                value(message.externalConversationId()),
                value(message.senderId()),
                String.valueOf(timestamp),
                sha256(value(message.text())),
                sha256(value(message.messageType())),
                sha256(canonicalAttachments(message.attachments())),
                sha256(canonicalAction(message.action())));
    }

    public static String outboundPayload(String jsonBody, long timestamp) {
        return timestamp + "." + sha256(value(jsonBody));
    }

    public static String contentHash(String content) {
        return sha256(value(content));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("渠道消息摘要计算失败", e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String canonicalAttachments(List<OpsChannelAttachment> attachments) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OpsChannelAttachment attachment : attachments == null ? List.<OpsChannelAttachment>of() : attachments) {
            if (attachment == null) {
                rows.add(Map.of());
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attachmentId", value(attachment.attachmentId()));
            row.put("fileName", value(attachment.fileName()));
            row.put("mediaType", value(attachment.mediaType()));
            row.put("sizeBytes", attachment.sizeBytes());
            row.put("contentRef", value(attachment.contentRef()));
            row.put("contentHash", value(attachment.contentHash()));
            rows.add(row);
        }
        return JSON.toJSONString(rows);
    }

    private static String canonicalAction(OpsChannelAction action) {
        if (action == null) return "{}";
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actionId", value(action.actionId()));
        row.put("actionType", value(action.actionType()));
        row.put("value", value(action.value()));
        row.put("parameters", canonicalValue(action.parameters()));
        return JSON.toJSONString(row);
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sorted = new TreeMap<>();
            source.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalValue(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> source) {
            List<Object> rows = new ArrayList<>();
            source.forEach(item -> rows.add(canonicalValue(item)));
            return rows;
        }
        return value;
    }
}
