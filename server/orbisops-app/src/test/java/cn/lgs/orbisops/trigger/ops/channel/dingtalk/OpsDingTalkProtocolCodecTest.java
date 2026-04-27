package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsDingTalkProtocolCodecTest {

    private final OpsDingTalkProtocolCodec codec = new OpsDingTalkProtocolCodec();

    @Test
    void groupMessageUsesStableConversationTargetAndStaffIdentity() {
        ChatbotMessage message = baseMessage();
        when(message.getConversationType()).thenReturn("2");
        when(message.getConversationId()).thenReturn("cid-group-1");
        when(message.getSenderStaffId()).thenReturn("staff-1");
        when(message.getSenderId()).thenReturn("union-fallback");
        when(message.getSessionWebhook()).thenReturn("https://oapi.dingtalk.com/robot/sendBySession/test");
        when(message.getSessionWebhookExpiredTime()).thenReturn(Instant.now().plusSeconds(240).toEpochMilli());

        var decoded = codec.decode(message);

        assertEquals("group:cid-group-1", decoded.envelope().message().conversation().externalConversationId());
        assertEquals(ChannelConversationRef.ConversationKind.GROUP, decoded.envelope().message().conversation().kind());
        assertEquals("staff-1", decoded.envelope().sender().externalPrincipalId());
        assertEquals("msg-1", decoded.envelope().message().externalMessageId());
        assertEquals("msg-1", decoded.envelope().idempotencyKey());
        assertEquals("investigate checkout latency", decoded.envelope().content().plainText());
        assertTrue(decoded.hasReplyContext());
        assertEquals("staff-1", decoded.atUserId());
    }

    @Test
    void directMessageUsesUserTargetAndFallsBackToSenderIdWhenStaffIdMissing() {
        ChatbotMessage message = baseMessage();
        when(message.getConversationType()).thenReturn("1");
        when(message.getSenderStaffId()).thenReturn("");
        when(message.getSenderId()).thenReturn("external-user-1");
        when(message.getSessionWebhook()).thenReturn("");
        when(message.getSessionWebhookExpiredTime()).thenReturn(0L);

        var decoded = codec.decode(message);

        assertEquals("user:external-user-1", decoded.envelope().message().conversation().externalConversationId());
        assertEquals(ChannelConversationRef.ConversationKind.DIRECT, decoded.envelope().message().conversation().kind());
        assertEquals("external-user-1", decoded.envelope().sender().externalPrincipalId());
        assertFalse(decoded.hasReplyContext());
        assertEquals("", decoded.sessionWebhook());
        assertNull(decoded.sessionWebhookExpiresAt());
        assertEquals("", decoded.atUserId());
    }

    @Test
    void invalidProviderPayloadIsIgnoredInsteadOfInventingIdentifiers() {
        ChatbotMessage message = mock(ChatbotMessage.class, RETURNS_DEEP_STUBS);
        when(message.getText().getContent()).thenReturn("hello");
        when(message.getMsgId()).thenReturn("");
        when(message.getSenderStaffId()).thenReturn("staff-1");
        when(message.getConversationType()).thenReturn("2");
        when(message.getConversationId()).thenReturn("cid-group-1");

        var decoded = codec.decode(message);

        assertNull(decoded.envelope());
        assertFalse(decoded.hasReplyContext());
    }

    private ChatbotMessage baseMessage() {
        ChatbotMessage message = mock(ChatbotMessage.class, RETURNS_DEEP_STUBS);
        when(message.getText().getContent()).thenReturn(" investigate checkout latency ");
        when(message.getMsgId()).thenReturn("msg-1");
        when(message.getSenderNick()).thenReturn("Alice");
        when(message.getCreateAt()).thenReturn(1_720_000_000_000L);
        return message;
    }
}
