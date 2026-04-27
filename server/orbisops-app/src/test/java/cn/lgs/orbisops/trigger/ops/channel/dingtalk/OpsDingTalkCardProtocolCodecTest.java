package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsDingTalkCardProtocolCodecTest {

    private final OpsDingTalkCardProtocolCodec codec = new OpsDingTalkCardProtocolCodec();

    @Test
    void cardDataContainsOnlyPresentationAndOpaqueActionHandles() {
        ChannelRichContent content = new ChannelRichContent(
                "Approval required", "**Approval required**", List.of(
                new ChannelInteractiveAction("approve", "Approve", "opaque-approve", ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelInteractiveAction("reject", "Reject", "opaque-reject", ChannelInteractiveAction.ActionStyle.DANGER)));

        var data = codec.cardData(content);

        assertEquals("**Approval required**", data.get("markdown"));
        assertEquals("opaque-approve", data.get("primaryActionToken"));
        assertEquals("opaque-reject", data.get("secondaryActionToken"));
        String serialized = com.alibaba.fastjson.JSON.toJSONString(data);
        assertFalse(serialized.contains("packageId"));
        assertFalse(serialized.contains("versionHash"));
        assertFalse(serialized.contains("approvedHash"));
    }

    @Test
    void callbackMapsGroupSpaceAndOpaqueTokenIntoUnifiedEnvelope() {
        String content = "{\"cardPrivateData\":{\"params\":{\"actionToken\":\"opaque-approve\"}}}";
        String message = "{"
                + "\"userId\":\"staff-1\","
                + "\"spaceId\":\"dtv1.card//IM_GROUP.cid-1\","
                + "\"outTrackId\":\"card-1\","
                + "\"content\":" + com.alibaba.fastjson.JSON.toJSONString(content)
                + "}";

        var decoded = codec.decodeCallback(message);

        assertEquals("opaque-approve", decoded.envelope().action().opaqueActionToken());
        assertEquals("staff-1", decoded.envelope().actor().externalPrincipalId());
        assertEquals("card-1", decoded.envelope().message().externalMessageId());
        assertEquals("group:cid-1", decoded.envelope().message().conversation().externalConversationId());
        assertEquals(ChannelConversationRef.ConversationKind.GROUP, decoded.envelope().message().conversation().kind());
        assertEquals("card-1:staff-1:opaque-approve", decoded.envelope().idempotencyKey());
    }

    @Test
    void callbackMapsDirectSpaceAndFailsClosedWithoutActionToken() {
        String validContent = "{\"cardPrivateData\":{\"params\":{\"opaqueActionToken\":\"opaque-reject\"}}}";
        String valid = "{"
                + "\"userId\":\"staff-2\","
                + "\"spaceId\":\"dtv1.card//IM_ROBOT.staff-2\","
                + "\"outTrackId\":\"card-2\","
                + "\"content\":" + com.alibaba.fastjson.JSON.toJSONString(validContent)
                + "}";
        String invalidContent = "{\"cardPrivateData\":{\"params\":{}}}";
        String invalid = "{"
                + "\"userId\":\"staff-2\","
                + "\"spaceId\":\"dtv1.card//IM_ROBOT.staff-2\","
                + "\"outTrackId\":\"card-2\","
                + "\"content\":" + com.alibaba.fastjson.JSON.toJSONString(invalidContent)
                + "}";

        var decoded = codec.decodeCallback(valid);
        var rejected = codec.decodeCallback(invalid);

        assertEquals("user:staff-2", decoded.envelope().message().conversation().externalConversationId());
        assertEquals(ChannelConversationRef.ConversationKind.DIRECT, decoded.envelope().message().conversation().kind());
        assertEquals("opaque-reject", decoded.envelope().action().opaqueActionToken());
        assertNull(rejected.envelope());
    }

    @Test
    void terminalCallbackResponseDisablesActionsAndUpdatesCardByKey() {
        var response = codec.callbackUpdateResponse("Approved by Alice");

        assertEquals(Boolean.TRUE, response.getJSONObject("cardUpdateOptions").getBoolean("updateCardDataByKey"));
        var params = response.getJSONObject("cardData").getJSONObject("cardParamMap");
        assertEquals("true", params.getString("resolved"));
        assertEquals("false", params.getString("hasActions"));
        assertTrue(params.getString("markdown").contains("Approved"));
    }
}
