package cn.lgs.orbisops.trigger.ops.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record OpsChannelMessage(String externalMessageId,
                                String externalConversationId,
                                String senderId,
                                String text,
                                long timestamp,
                                Map<String, Object> metadata,
                                String messageType,
                                List<OpsChannelAttachment> attachments,
                                OpsChannelAction action) {
    public OpsChannelMessage {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        messageType = messageType == null || messageType.isBlank() ? "TEXT" : messageType.trim().toUpperCase(Locale.ROOT);
        attachments = attachments == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public OpsChannelMessage(String externalMessageId,
                             String externalConversationId,
                             String senderId,
                             String text,
                             long timestamp,
                             Map<String, Object> metadata) {
        this(externalMessageId, externalConversationId, senderId, text, timestamp, metadata, "TEXT", List.of(), null);
    }
}
