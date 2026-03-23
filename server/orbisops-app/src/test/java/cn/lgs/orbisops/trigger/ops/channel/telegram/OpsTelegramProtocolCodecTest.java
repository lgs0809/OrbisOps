package cn.lgs.orbisops.trigger.ops.channel.telegram;

import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsTelegramProtocolCodecTest {

    private final OpsTelegramProtocolCodec codec = new OpsTelegramProtocolCodec();

    @Test
    void decodesDirectMessageAndPreservesStableUpdateIdempotency() {
        JSONObject update = JSON.parseObject("""
                {
                  "update_id": 101,
                  "message": {
                    "message_id": 7,
                    "from": {"id": 42, "username": "alice", "is_bot": false},
                    "chat": {"id": 42, "type": "private"},
                    "text": "check production latency"
                  }
                }
                """);

        var decoded = codec.decode(configuration(false), update);

        assertNotNull(decoded.inbound());
        assertEquals("7", decoded.inbound().message().externalMessageId());
        assertEquals("42", decoded.inbound().sender().externalPrincipalId());
        assertEquals("check production latency", decoded.inbound().content().plainText());
        assertEquals("telegram:update:101", decoded.inbound().idempotencyKey());
    }

    @Test
    void groupMessageRequiresConfiguredBotMentionAndStripsItBeforeRuntime() {
        JSONObject withoutMention = JSON.parseObject("""
                {"update_id": 102, "message": {"message_id": 8,
                  "from": {"id": 43, "username": "bob", "is_bot": false},
                  "chat": {"id": -1001, "type": "supergroup"},
                  "text": "check latency"}}
                """);
        JSONObject withMention = JSON.parseObject("""
                {"update_id": 103, "message": {"message_id": 9,
                  "from": {"id": 43, "username": "bob", "is_bot": false},
                  "chat": {"id": -1001, "type": "supergroup"},
                  "text": "@orbisops_bot check latency"}}
                """);

        assertNull(codec.decode(configuration(true), withoutMention).inbound());
        var inbound = codec.decode(configuration(true), withMention).inbound();
        assertNotNull(inbound);
        assertEquals("check latency", inbound.content().plainText());
        assertEquals("-1001", inbound.message().conversation().externalConversationId());
    }

    @Test
    void decodesCallbackAsOpaqueUnifiedApprovalAction() {
        String actionValue = "a".repeat(64);
        JSONObject update = JSON.parseObject("""
                {"update_id": 104, "callback_query": {
                  "id": "callback-1",
                  "from": {"id": 44, "username": "approver"},
                  "data": "%s",
                  "message": {"message_id": 10, "chat": {"id": 44, "type": "private"}}
                }}
                """.formatted(actionValue));

        var decoded = codec.decode(configuration(false), update);

        assertNotNull(decoded.interactive());
        assertEquals(actionValue, decoded.interactive().action().opaqueActionToken());
        assertEquals("callback-1", decoded.callbackQueryId());
    }

    @Test
    void rendersInlineKeyboardOnlyWhenOpaqueActionFitsTelegramLimit() {
        ChannelRichContent valid = new ChannelRichContent(
                "Approve change", "", List.of(new ChannelInteractiveAction(
                "approve", "Approve", "b".repeat(64), ChannelInteractiveAction.ActionStyle.PRIMARY)));
        Map<String, Object> markup = codec.replyMarkup(valid);
        assertNotNull(markup.get("inline_keyboard"));

        ChannelRichContent oversized = new ChannelRichContent(
                "Approve change", "", List.of(new ChannelInteractiveAction(
                "approve", "Approve", "c".repeat(65), ChannelInteractiveAction.ActionStyle.PRIMARY)));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> codec.replyMarkup(oversized));
        assertEquals("TELEGRAM_CALLBACK_DATA_TOO_LARGE", failure.getMessage());
    }

    private OpsTelegramChannelConfiguration configuration(boolean requireMention) {
        return new OpsTelegramChannelConfiguration(
                "channel-telegram", "project-1", "${env:TELEGRAM_BOT_TOKEN}",
                ChannelConnectionMode.LONG_CONNECTION, requireMention, "orbisops_bot");
    }
}
