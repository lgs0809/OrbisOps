package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;

import java.time.Instant;
import java.util.List;

/** Pure translation from DingTalk Stream chatbot payloads into the shared Channel contract. */
final class OpsDingTalkProtocolCodec {

    DecodedMessage decode(ChatbotMessage message) {
        if (message == null || message.getText() == null) return DecodedMessage.empty();
        String content = text(message.getText().getContent());
        String msgId = text(message.getMsgId());
        String staffId = text(message.getSenderStaffId());
        String senderId = staffId.isBlank() ? text(message.getSenderId()) : staffId;
        boolean group = "2".equals(text(message.getConversationType()));
        String rawConversationId = group ? text(message.getConversationId()) : senderId;
        String conversationId = rawConversationId.isBlank() ? "" : (group ? "group:" : "user:") + rawConversationId;
        if (content.isBlank() || msgId.isBlank() || senderId.isBlank() || conversationId.isBlank()) {
            return DecodedMessage.empty();
        }

        long createdAt = message.getCreateAt();
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId,
                group ? ChannelConversationRef.ConversationKind.GROUP : ChannelConversationRef.ConversationKind.DIRECT);
        ChannelInboundEnvelope envelope = new ChannelInboundEnvelope(
                new ChannelMessageRef(msgId, conversation),
                new ChannelExternalPrincipal(senderId, text(message.getSenderNick()), ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(content.trim()),
                List.of(),
                createdAt > 0 ? Instant.ofEpochMilli(createdAt) : Instant.now(),
                msgId);

        String sessionWebhook = text(message.getSessionWebhook());
        long expiresAtMillis = message.getSessionWebhookExpiredTime();
        Instant expiresAt = sessionWebhook.isBlank()
                ? null
                : expiresAtMillis > 0 ? Instant.ofEpochMilli(expiresAtMillis) : Instant.now().plusSeconds(300);
        return new DecodedMessage(envelope, sessionWebhook, expiresAt, group ? staffId : "");
    }

    record DecodedMessage(ChannelInboundEnvelope envelope,
                          String sessionWebhook,
                          Instant sessionWebhookExpiresAt,
                          String atUserId) {
        DecodedMessage {
            sessionWebhook = sessionWebhook == null ? "" : sessionWebhook.trim();
            atUserId = atUserId == null ? "" : atUserId.trim();
        }

        static DecodedMessage empty() {
            return new DecodedMessage(null, "", null, "");
        }

        boolean hasReplyContext() {
            return envelope != null && !sessionWebhook.isBlank() && sessionWebhookExpiresAt != null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
