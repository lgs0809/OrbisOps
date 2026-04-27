package cn.lgs.orbisops.trigger.ops.channel;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpsChannelSignatureTest {

    @Test
    void signatureIsStableAndRejectsTampering() {
        String signature = OpsChannelSignature.sign("secret", "1700000000.message");
        assertTrue(OpsChannelSignature.verify("secret", "1700000000.message", signature));
        assertFalse(OpsChannelSignature.verify("secret", "1700000000.changed", signature));
        assertFalse(OpsChannelSignature.verify("other", "1700000000.message", signature));
    }

    @Test
    void inboundSignatureCoversConversationSenderAndContent() {
        long timestamp = 1_700_000_000L;
        OpsChannelMessage message = new OpsChannelMessage("m-1", "room-1", "user-1", "查询错误日志", timestamp, null);
        String signature = OpsChannelSignature.sign("secret", OpsChannelSignature.inboundPayload(message, timestamp));

        assertTrue(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(message, timestamp), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(
                new OpsChannelMessage("m-1", "room-1", "other-user", "查询错误日志", timestamp, null), timestamp), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(
                new OpsChannelMessage("m-1", "other-room", "user-1", "查询错误日志", timestamp, null), timestamp), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(
                new OpsChannelMessage("m-1", "room-1", "user-1", "直接重启生产", timestamp, null), timestamp), signature));
    }

    @Test
    void outboundSignatureCoversTimestampAndCompleteBody() {
        String body = "{\"conversationId\":\"room-1\",\"content\":\"完成\"}";
        long timestamp = 1_700_000_000L;
        String signature = OpsChannelSignature.sign("secret", OpsChannelSignature.outboundPayload(body, timestamp));

        assertTrue(OpsChannelSignature.verify("secret", OpsChannelSignature.outboundPayload(body, timestamp), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.outboundPayload(body, timestamp + 1), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.outboundPayload(body + " ", timestamp), signature));
    }

    @Test
    void inboundSignatureCoversAttachmentsAndButtonActions() {
        long timestamp = 1_700_000_000L;
        OpsChannelAttachment attachment = new OpsChannelAttachment(
                "attachment-1", "error.log", "text/plain", 128,
                "bridge://files/attachment-1", "a".repeat(64));
        OpsChannelAction action = new OpsChannelAction("retry", "BUTTON", "retry-once", Map.of("attempt", 1));
        OpsChannelMessage original = new OpsChannelMessage(
                "m-2", "room-1", "user-1", "查看附件", timestamp, Map.of(), "ACTION", List.of(attachment), action);
        String signature = OpsChannelSignature.sign("secret", OpsChannelSignature.inboundPayload(original, timestamp));

        OpsChannelMessage changedAttachment = new OpsChannelMessage(
                "m-2", "room-1", "user-1", "查看附件", timestamp, Map.of(), "ACTION",
                List.of(new OpsChannelAttachment("attachment-1", "error.log", "text/plain", 128,
                        "bridge://files/attachment-1", "b".repeat(64))), action);
        OpsChannelMessage changedAction = new OpsChannelMessage(
                "m-2", "room-1", "user-1", "查看附件", timestamp, Map.of(), "ACTION", List.of(attachment),
                new OpsChannelAction("approve", "BUTTON", "approve", Map.of("attempt", 1)));

        assertTrue(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(original, timestamp), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(changedAttachment, timestamp), signature));
        assertFalse(OpsChannelSignature.verify("secret", OpsChannelSignature.inboundPayload(changedAction, timestamp), signature));
    }

    @Test
    void actionParameterInsertionOrderDoesNotChangeSignaturePayload() {
        long timestamp = 1_700_000_000L;
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("timeout", 30);
        first.put("options", Map.of("retry", 2, "mode", "safe"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("options", Map.of("mode", "safe", "retry", 2));
        second.put("timeout", 30);
        OpsChannelMessage one = new OpsChannelMessage("m-3", "room", "user", "", timestamp, Map.of(),
                "ACTION", List.of(), new OpsChannelAction("retry", "BUTTON", "retry", first));
        OpsChannelMessage two = new OpsChannelMessage("m-3", "room", "user", "", timestamp, Map.of(),
                "ACTION", List.of(), new OpsChannelAction("retry", "BUTTON", "retry", second));

        assertEquals(OpsChannelSignature.inboundPayload(one, timestamp), OpsChannelSignature.inboundPayload(two, timestamp));
    }
}
