package cn.lgs.orbisops.application.channel.provider;

public record ChannelAttachment(String attachmentId,
                                String fileName,
                                String contentType,
                                long sizeBytes,
                                String contentRef,
                                String contentHash) {
    public ChannelAttachment {
        attachmentId = required(attachmentId, "CHANNEL_ATTACHMENT_ID_REQUIRED");
        fileName = required(fileName, "CHANNEL_ATTACHMENT_NAME_REQUIRED");
        contentType = required(contentType, "CHANNEL_ATTACHMENT_CONTENT_TYPE_REQUIRED");
        if (sizeBytes < 0) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_INVALID");
        contentRef = required(contentRef, "CHANNEL_ATTACHMENT_CONTENT_REF_REQUIRED");
        contentHash = required(contentHash, "CHANNEL_ATTACHMENT_HASH_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
