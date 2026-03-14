package cn.lgs.orbisops.application.channel;

import java.util.Arrays;

/** Durable binary storage boundary for Channel attachments. Provider URLs/tokens must never escape adapter code. */
public interface ChannelAttachmentAssetPort {

    StoredAttachment store(StoreAttachment command);

    byte[] read(String contentRef);

    record StoreAttachment(String projectId,
                           String channelId,
                           String attachmentId,
                           String fileName,
                           String contentType,
                           byte[] content) {
        public StoreAttachment {
            projectId = required(projectId, "CHANNEL_ATTACHMENT_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ATTACHMENT_CHANNEL_ID_REQUIRED");
            attachmentId = required(attachmentId, "CHANNEL_ATTACHMENT_ID_REQUIRED");
            fileName = required(fileName, "CHANNEL_ATTACHMENT_NAME_REQUIRED");
            contentType = required(contentType, "CHANNEL_ATTACHMENT_CONTENT_TYPE_REQUIRED");
            content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        }

        @Override
        public byte[] content() {
            return Arrays.copyOf(content, content.length);
        }
    }

    record StoredAttachment(String contentRef, String contentHash, long sizeBytes) {
        public StoredAttachment {
            contentRef = required(contentRef, "CHANNEL_ATTACHMENT_CONTENT_REF_REQUIRED");
            contentHash = required(contentHash, "CHANNEL_ATTACHMENT_HASH_REQUIRED");
            if (sizeBytes < 0) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_INVALID");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
