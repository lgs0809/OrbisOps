package cn.lgs.orbisops.trigger.ops.channel.slack;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.ButtonElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSlackProtocolCodecTest {

    private final OpsSlackProtocolCodec codec = new OpsSlackProtocolCodec();

    @Test
    void groupMessageRequiresAppMentionWhenConfigured() {
        var decoded = codec.decode(configuration(true), eventEnvelope("message", "channel", "hello", false));
        assertNull(decoded.inbound());
    }

    @Test
    void appMentionIsAcceptedAndLeadingMentionIsRemoved() {
        var decoded = codec.decode(configuration(true), eventEnvelope("app_mention", "channel", "<@B123> investigate checkout errors", false));

        assertEquals("env-1", decoded.envelopeId());
        assertEquals("investigate checkout errors", decoded.inbound().content().plainText());
        assertEquals("U123", decoded.inbound().sender().externalPrincipalId());
        assertEquals("C123", decoded.inbound().message().conversation().externalConversationId());
        assertEquals(ChannelConversationRef.ConversationKind.GROUP, decoded.inbound().message().conversation().kind());
        assertEquals("1710000000.001", decoded.inbound().message().externalMessageId());
        assertEquals("1710000000.001", decoded.inbound().message().threadId());
        assertEquals("env-1", decoded.inbound().idempotencyKey());
    }

    @Test
    void directMessageDoesNotRequireMentionAndBotLoopIsIgnored() {
        var direct = codec.decode(configuration(true), eventEnvelope("message", "im", "why is prod slow", false));
        var bot = codec.decode(configuration(false), eventEnvelope("message", "channel", "loop", true));

        assertEquals("why is prod slow", direct.inbound().content().plainText());
        assertEquals(ChannelConversationRef.ConversationKind.DIRECT, direct.inbound().message().conversation().kind());
        assertEquals("", direct.inbound().message().threadId());
        assertNull(bot.inbound());
    }

    @Test
    void nestedSlackThreadKeepsRootAsTypedReplyAnchor() {
        String raw = """
                {
                  "type":"events_api",
                  "envelope_id":"env-thread-1",
                  "payload":{
                    "event":{
                      "type":"app_mention",
                      "user":"U123",
                      "channel":"C123",
                      "channel_type":"channel",
                      "ts":"1710000001.002",
                      "thread_ts":"1710000000.001",
                      "text":"<@B123> continue investigation"
                    }
                  }
                }
                """;

        var decoded = codec.decode(configuration(true), raw);

        assertEquals("1710000001.002", decoded.inbound().message().externalMessageId());
        assertEquals("1710000000.001", decoded.inbound().message().threadId());
        assertEquals("C123", decoded.inbound().message().conversation().externalConversationId());
    }

    @Test
    void fileOnlyMessageProducesSafeSummaryAndFileDescriptorWithoutPersistingPrivateUrl() {
        String raw = """
                {
                  "type":"events_api",
                  "envelope_id":"env-file-1",
                  "payload":{
                    "event":{
                      "type":"message",
                      "subtype":"file_share",
                      "user":"U123",
                      "channel":"D123",
                      "channel_type":"im",
                      "ts":"1710000002.003",
                      "text":"",
                      "files":[{
                        "id":"F123",
                        "name":"incident.log",
                        "mimetype":"text/plain",
                        "size":12,
                        "url_private_download":"https://files.slack.com/files-pri/T1-F123/download/incident.log"
                      }]
                    }
                  }
                }
                """;

        var decoded = codec.decode(configuration(true), raw);

        assertEquals("Shared attachment: incident.log", decoded.inbound().content().plainText());
        assertEquals(1, decoded.files().size());
        assertEquals("F123", decoded.files().get(0).fileId());
        assertEquals("incident.log", decoded.files().get(0).fileName());
        assertEquals("text/plain", decoded.files().get(0).mediaType());
        assertEquals(12L, decoded.files().get(0).sizeBytes());
        assertTrue(decoded.inbound().attachments().isEmpty());
    }

    @Test
    void interactiveActionCarriesOnlyOpaqueActionKeyIntoUnifiedDispatcherEnvelope() {
        String raw = """
                {
                  "type":"interactive",
                  "envelope_id":"env-action-1",
                  "payload":{
                    "type":"block_actions",
                    "user":{"id":"U789"},
                    "channel":{"id":"C789"},
                    "message":{"ts":"1710000000.999"},
                    "actions":[{"action_id":"orbisops:approve","value":"opaque-action-key"}]
                  }
                }
                """;

        var decoded = codec.decode(configuration(true), raw);

        assertEquals("opaque-action-key", decoded.interactive().action().opaqueActionToken());
        assertEquals("U789", decoded.interactive().actor().externalPrincipalId());
        assertEquals("C789", decoded.interactive().message().conversation().externalConversationId());
        assertEquals("1710000000.999", decoded.interactive().message().externalMessageId());
        assertEquals("env-action-1", decoded.interactive().idempotencyKey());
    }

    @Test
    void blockKitButtonsContainOpaqueValueButNoChangePackageAuthorityFields() {
        ChannelRichContent content = new ChannelRichContent(
                "Approval required",
                "*Approval required*",
                List.of(new ChannelInteractiveAction(
                        "approve", "Approve", "opaque-action-key", ChannelInteractiveAction.ActionStyle.PRIMARY)));

        List<LayoutBlock> blocks = codec.blocks(content);

        assertEquals(2, blocks.size());
        ActionsBlock actions = (ActionsBlock) blocks.get(1);
        ButtonElement button = (ButtonElement) actions.getElements().get(0);
        assertEquals("opaque-action-key", button.getValue());
        assertEquals("orbisops:approve", button.getActionId());
        String serialized = com.alibaba.fastjson2.JSON.toJSONString(blocks);
        assertTrue(serialized.contains("opaque-action-key"));
        assertTrue(!serialized.contains("packageId") && !serialized.contains("versionHash") && !serialized.contains("approvedHash"));
    }

    private OpsSlackChannelConfiguration configuration(boolean requireMention) {
        return new OpsSlackChannelConfiguration(
                "channel-slack", "project-1", "${env:SLACK_BOT_TOKEN}", "${env:SLACK_APP_TOKEN}",
                cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode.LONG_CONNECTION,
                requireMention);
    }

    private String eventEnvelope(String eventType, String channelType, String text, boolean bot) {
        String botFields = bot ? "\"bot_id\":\"B999\",\"subtype\":\"bot_message\"," : "";
        return """
                {
                  "type":"events_api",
                  "envelope_id":"env-1",
                  "payload":{
                    "event":{
                      "type":"%s",
                      %s
                      "user":"U123",
                      "channel":"C123",
                      "channel_type":"%s",
                      "ts":"1710000000.001",
                      "text":"%s"
                    }
                  }
                }
                """.formatted(eventType, botFields, channelType, text);
    }
}
