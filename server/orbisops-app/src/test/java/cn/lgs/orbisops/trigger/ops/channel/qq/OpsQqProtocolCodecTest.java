package cn.lgs.orbisops.trigger.ops.channel.qq;

import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpsQqProtocolCodecTest {

    private final OpsQqProtocolCodec codec = new OpsQqProtocolCodec();

    @Test
    void mapsC2cAndGroupAtMessagesToStableConversationKinds() {
        JSONObject c2c = JSONObject.of(
                "id", "msg-1",
                "content", "inspect latency",
                "author", JSONObject.of("user_openid", "USER_A"));
        JSONObject group = JSONObject.of(
                "id", "msg-2",
                "group_openid", "GROUP_A",
                "content", "<@bot> inspect errors",
                "author", JSONObject.of("member_openid", "MEMBER_A", "username", "alice", "bot", false));

        var direct = codec.decodeDispatch("C2C_MESSAGE_CREATE", c2c).inbound();
        var grouped = codec.decodeDispatch("GROUP_AT_MESSAGE_CREATE", group).inbound();

        assertNotNull(direct);
        assertEquals("c2c:USER_A", direct.message().conversation().externalConversationId());
        assertEquals("qq:message:msg-1", direct.idempotencyKey());
        assertNotNull(grouped);
        assertEquals("group:GROUP_A", grouped.message().conversation().externalConversationId());
        assertEquals("inspect errors", grouped.content().plainText());
        assertNull(codec.decodeDispatch("GROUP_MESSAGE_CREATE", group).inbound());
    }

    @Test
    void interactionUsesOfficialButtonDataAsExistingOpaqueActionToken() {
        String actionValue = "a".repeat(64);
        JSONObject event = JSONObject.of(
                "id", "interaction-1",
                "group_openid", "GROUP_A",
                "group_member_openid", "MEMBER_A",
                "data", JSONObject.of("resolved", JSONObject.of(
                        "button_data", actionValue,
                        "message_id", "msg-approval")));

        var decoded = codec.decodeDispatch("INTERACTION_CREATE", event);

        assertNotNull(decoded.interactive());
        assertEquals(actionValue, decoded.interactive().action().opaqueActionToken());
        assertEquals("group:GROUP_A", decoded.interactive().message().conversation().externalConversationId());
        assertEquals("qq:interaction:interaction-1", decoded.interactive().idempotencyKey());
    }

    @Test
    void approvalKeyboardUsesCallbackAllUsersAndSingleClickSemantics() {
        ChannelRichContent content = new ChannelRichContent("Approval", "", List.of(
                new ChannelInteractiveAction("approve", "Approve", "b".repeat(64), ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelInteractiveAction("reject", "Reject", "c".repeat(64), ChannelInteractiveAction.ActionStyle.DANGER)));

        Map<String, Object> keyboard = codec.keyboard(content);
        Map<?, ?> body = (Map<?, ?>) keyboard.get("content");
        List<?> rows = (List<?>) body.get("rows");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        List<?> buttons = (List<?>) row.get("buttons");
        Map<?, ?> approve = (Map<?, ?>) buttons.get(0);
        Map<?, ?> action = (Map<?, ?>) approve.get("action");
        Map<?, ?> permission = (Map<?, ?>) action.get("permission");

        assertEquals(1, action.get("type"));
        assertEquals(2, permission.get("type"));
        assertEquals(1, action.get("click_limit"));
        assertEquals("b".repeat(64), action.get("data"));
        assertEquals("orbisops-approval", approve.get("group_id"));
    }
}
