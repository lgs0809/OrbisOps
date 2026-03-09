package cn.lgs.orbisops.trigger.ops.channel;

public record OpsChannelAttachment(String attachmentId,
                                   String fileName,
                                   String mediaType,
                                   long sizeBytes,
                                   String contentRef,
                                   String contentHash) {
}
