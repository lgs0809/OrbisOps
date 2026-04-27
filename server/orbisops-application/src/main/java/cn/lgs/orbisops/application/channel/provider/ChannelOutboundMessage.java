package cn.lgs.orbisops.application.channel.provider;

import cn.lgs.orbisops.application.channel.ChannelOutboundMetadata;

import java.util.List;

public record ChannelOutboundMessage(ChannelConversationRef conversation,
                                     ChannelRichContent content,
                                     List<ChannelAttachment> attachments,
                                     ChannelOutboundMetadata metadata) {
    public ChannelOutboundMessage {
        if (conversation == null) throw new IllegalArgumentException("CHANNEL_CONVERSATION_REQUIRED");
        if (content == null) throw new IllegalArgumentException("CHANNEL_CONTENT_REQUIRED");
        attachments = attachments == null || attachments.isEmpty() ? List.of() : List.copyOf(attachments);
        metadata = metadata == null ? ChannelOutboundMetadata.from(null) : metadata;
    }
}
