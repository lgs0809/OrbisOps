package cn.lgs.orbisops.trigger.ops.channel.discord;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsDiscordProtocolCodecTest {

    private final OpsDiscordProtocolCodec codec = new OpsDiscordProtocolCodec();

    @Test
    void decodesDirectMessagesWithoutPrivilegedMessageContentRequirement() {
        var message = JSON.parseObject("""
                {"id":"100","channel_id":"200","content":"inspect latency",
                 "author":{"id":"300","username":"alice","bot":false},"mentions":[]}
                """);

        var decoded = codec.decodeDispatch(configuration(true, false), "999", "MESSAGE_CREATE", message);

        assertNotNull(decoded.inbound());
        assertEquals("100", decoded.inbound().message().externalMessageId());
        assertEquals("200", decoded.inbound().message().conversation().externalConversationId());
        assertEquals("inspect latency", decoded.inbound().content().plainText());
        assertEquals("discord:message:100", decoded.inbound().idempotencyKey());
    }

    @Test
    void guildMentionGateRequiresBotMentionAndStripsIt() {
        var ignored = JSON.parseObject("""
                {"id":"101","channel_id":"201","guild_id":"400","content":"inspect latency",
                 "author":{"id":"301","username":"bob","bot":false},"mentions":[]}
                """);
        var accepted = JSON.parseObject("""
                {"id":"102","channel_id":"201","guild_id":"400","content":"<@999> inspect latency",
                 "author":{"id":"301","username":"bob","bot":false},"mentions":[{"id":"999"}]}
                """);

        assertNull(codec.decodeDispatch(configuration(true, false), "999", "MESSAGE_CREATE", ignored).inbound());
        var inbound = codec.decodeDispatch(configuration(true, false), "999", "MESSAGE_CREATE", accepted).inbound();
        assertNotNull(inbound);
        assertEquals("inspect latency", inbound.content().plainText());
    }

    @Test
    void componentInteractionCarriesOnlyOpaqueActionIntoUnifiedEnvelope() {
        String actionValue = "a".repeat(64);
        JSONObject interaction = new JSONObject();
        interaction.put("id", "500");
        interaction.put("type", 3);
        interaction.put("channel_id", "200");
        interaction.put("guild_id", "400");
        interaction.put("token", "test-interaction-credential");
        interaction.put("data", JSONObject.of("component_type", 2, "custom_id", actionValue));
        interaction.put("member", JSONObject.of("user", JSONObject.of("id", "300", "username", "approver")));
        interaction.put("message", JSONObject.of("id", "100", "channel_id", "200"));

        var decoded = codec.decodeDispatch(configuration(true, false), "999", "INTERACTION_CREATE", interaction);

        assertNotNull(decoded.interactive());
        assertEquals(actionValue, decoded.interactive().action().opaqueActionToken());
        assertEquals("500", decoded.interactionId());
        assertEquals("test-interaction-credential", decoded.interactionToken());
    }

    @Test
    void legacyButtonComponentsFitCurrentOpaqueApprovalTokenAndRespectProviderLimits() {
        ChannelRichContent content = new ChannelRichContent("Approval", "", List.of(
                new ChannelInteractiveAction("approve", "Approve", "b".repeat(64), ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelInteractiveAction("reject", "Reject", "c".repeat(64), ChannelInteractiveAction.ActionStyle.DANGER)));

        var rows = codec.components(content);

        assertEquals(1, rows.size());
        assertEquals(2, ((List<?>) rows.get(0).get("components")).size());
        assertEquals("DISCORD_CUSTOM_ID_TOO_LARGE", assertThrows(IllegalArgumentException.class, () ->
                codec.components(new ChannelRichContent("Approval", "", List.of(
                        new ChannelInteractiveAction("approve", "Approve", "x".repeat(101), ChannelInteractiveAction.ActionStyle.PRIMARY))))).getMessage());
        assertEquals("DISCORD_MESSAGE_TOO_LARGE", assertThrows(IllegalArgumentException.class, () ->
                codec.renderText(ChannelRichContent.text("x".repeat(2001)))).getMessage());
    }

    private OpsDiscordChannelConfiguration configuration(boolean requireMention, boolean messageContentIntent) {
        return new OpsDiscordChannelConfiguration(
                "channel-discord", "project-1", "${env:DISCORD_BOT_CREDENTIAL}",
                ChannelConnectionMode.LONG_CONNECTION, requireMention, messageContentIntent);
    }
}
