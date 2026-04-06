package cn.lgs.orbisops.infrastructure.adapter.channel;

import cn.lgs.orbisops.application.channel.ChannelAttachmentAssetPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Local durable attachment store. The public contentRef never exposes a host filesystem path. */
@Component
public final class FileChannelAttachmentAssetAdapter implements ChannelAttachmentAssetPort {

    private static final String REF_PREFIX = "object://channel/";
    private final Path root;

    public FileChannelAttachmentAssetAdapter(
            @Value("${orbisops.channel.attachment.storage-dir:data/channel-attachments}") String storageDir) {
        this.root = Path.of(StringUtils.hasText(storageDir) ? storageDir : "data/channel-attachments")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public StoredAttachment store(StoreAttachment command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_STORE_COMMAND_REQUIRED");
        byte[] content = command.content();
        String objectId = UUID.randomUUID().toString();
        Path target = resolve(objectId);
        Path temporary = resolve(objectId + ".tmp");
        try {
            Files.createDirectories(root);
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredAttachment(REF_PREFIX + objectId, sha256(content), content.length);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // original storage failure remains authoritative
            }
            throw new IllegalStateException("CHANNEL_ATTACHMENT_STORE_FAILED", failure);
        }
    }

    @Override
    public byte[] read(String contentRef) {
        String objectId = objectId(contentRef);
        Path target = resolve(objectId);
        if (!Files.isRegularFile(target)) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_NOT_FOUND");
        try {
            return Files.readAllBytes(target);
        } catch (IOException failure) {
            throw new IllegalStateException("CHANNEL_ATTACHMENT_READ_FAILED", failure);
        }
    }

    private String objectId(String contentRef) {
        String normalized = contentRef == null ? "" : contentRef.trim();
        if (!normalized.startsWith(REF_PREFIX)) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_CONTENT_REF_UNTRUSTED");
        String objectId = normalized.substring(REF_PREFIX.length());
        try {
            UUID.fromString(objectId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("CHANNEL_ATTACHMENT_CONTENT_REF_INVALID", invalid);
        }
        return objectId;
    }

    private Path resolve(String objectId) {
        Path resolved = root.resolve(objectId).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_PATH_OUTSIDE_ROOT");
        return resolved;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", impossible);
        }
    }
}
