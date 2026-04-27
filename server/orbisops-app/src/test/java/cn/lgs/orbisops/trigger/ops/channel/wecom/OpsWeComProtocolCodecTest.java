package cn.lgs.orbisops.trigger.ops.channel.wecom;

import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWeComProtocolCodecTest {

    private final OpsWeComProtocolCodec codec = new OpsWeComProtocolCodec();

    @Test
    void subscribeFrameContainsBotIdAndSecretOnlyInsideProviderTransport() {
        JSONObject frame = JSON.parseObject(codec.subscribe("req-1", "bot-1", "secret-value"));
        assertEquals("aibot_subscribe", frame.getString("cmd"));
        assertEquals("req-1", frame.getJSONObject("headers").getString("req_id"));
        assertEquals("bot-1", frame.getJSONObject("body").getString("bot_id"));
        assertEquals("secret-value", frame.getJSONObject("body").getString("secret"));
    }

    @Test
    void parsesTextCallbackIntoTypedInboundEnvelope() {
        String raw = """
                {
                  "cmd":"aibot_msg_callback",
                  "headers":{"req_id":"callback-1"},
                  "body":{
                    "msgid":"msg-1","aibotid":"bot-1","chatid":"room-1","chattype":"group",
                    "from":{"userid":"user-1"},"create_time":1710000000,"msgtype":"text",
                    "text":{"content":"check checkout"},"response_url":"https://example.invalid/reply"
                  }
                }
                """;

        var frame = codec.inbound(raw).orElseThrow();
        assertEquals("callback-1", frame.requestId());
        assertEquals("msg-1", frame.envelope().message().externalMessageId());
        assertEquals("room-1", frame.envelope().message().conversation().externalConversationId());
        assertEquals("user-1", frame.envelope().sender().externalPrincipalId());
        assertEquals("check checkout", frame.envelope().content().plainText());
        assertEquals("msg-1", frame.envelope().idempotencyKey());
    }

    @Test
    void parsesTemplateCardEventAsOpaqueInteractiveAction() {
        String raw = """
                {
                  "cmd":"aibot_event_callback",
                  "headers":{"req_id":"event-1"},
                  "body":{
                    "msgid":"msg-card-1","chatid":"room-1","chattype":"group","from":{"userid":"user-1"},
                    "create_time":1710000000,"msgtype":"event",
                    "event":{"eventtype":"template_card_event","template_card_event":{"event_key":"opaque-action-123","task_id":"task-1"}}
                  }
                }
                """;

        var frame = codec.inbound(raw).orElseThrow();
        assertEquals("task-1", frame.taskId());
        assertEquals("opaque-action-123", frame.envelope().content().actions().get(0).opaqueActionToken());
    }

    @Test
    void keepsReplyAndProactivePushAsDifferentCommands() {
        JSONObject reply = JSON.parseObject(codec.replyMarkdown("callback-1", "done"));
        JSONObject proactive = JSON.parseObject(codec.proactiveMarkdown("send-1", "room-1", "done"));
        assertEquals("aibot_respond_msg", reply.getString("cmd"));
        assertEquals("callback-1", reply.getJSONObject("headers").getString("req_id"));
        assertEquals("aibot_send_msg", proactive.getString("cmd"));
        assertEquals("room-1", proactive.getJSONObject("body").getString("chatid"));
    }

    @Test
    void approvalCardExposesOnlyOpaqueActionKeysNotChangePackageAuthorityFields() {
        String approveKey = "opaque-action-a";
        String rejectKey = "opaque-action-b";
        JSONObject frame = JSON.parseObject(codec.approvalCard(
                "card-1", "room-1", "Approval Required", "Review this governed change",
                List.of(
                        new ChannelInteractiveAction("approve-id", "Approve", approveKey,
                                ChannelInteractiveAction.ActionStyle.PRIMARY),
                        new ChannelInteractiveAction("reject-id", "Reject", rejectKey,
                                ChannelInteractiveAction.ActionStyle.DANGER))));

        JSONObject body = frame.getJSONObject("body");
        JSONObject card = body.getJSONObject("template_card");
        assertEquals("aibot_send_msg", frame.getString("cmd"));
        assertEquals("template_card", body.getString("msgtype"));
        assertEquals(approveKey, card.getJSONArray("button_list").getJSONObject(0).getString("key"));
        assertEquals(rejectKey, card.getJSONArray("button_list").getJSONObject(1).getString("key"));
        String raw = frame.toJSONString();
        assertEquals(false, raw.contains("packageId"));
        assertEquals(false, raw.contains("packageVersion"));
        assertEquals(false, raw.contains("packageHash"));
        assertEquals(false, raw.contains("approve-id"));
        assertEquals(false, raw.contains("reject-id"));
    }

    @Test
    void parsesProviderAckByRequestId() {
        var ack = codec.ack("{\"headers\":{\"req_id\":\"send-1\"},\"errcode\":0,\"errmsg\":\"ok\"}");
        assertTrue(ack.success());
        assertEquals("send-1", ack.requestId());
    }
}
