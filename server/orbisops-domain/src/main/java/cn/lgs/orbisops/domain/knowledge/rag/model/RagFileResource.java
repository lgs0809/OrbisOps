package cn.lgs.orbisops.domain.knowledge.rag.model;

import java.io.IOException;
import java.io.InputStream;

/** Framework-neutral uploaded/file resource consumed by RAG ingestion. */
public interface RagFileResource {

    String name();

    String originalFilename();

    String contentType();

    long size();

    InputStream openStream() throws IOException;

    default boolean empty() {
        return size() <= 0L;
    }

    default String getName() {
        return name();
    }

    default String getOriginalFilename() {
        return originalFilename();
    }

    default String getContentType() {
        return contentType();
    }

    default long getSize() {
        return size();
    }

    default boolean isEmpty() {
        return empty();
    }

    default InputStream getInputStream() throws IOException {
        return openStream();
    }

    default byte[] getBytes() throws IOException {
        return readAllBytes();
    }

    default String fileName() {
        String original = originalFilename();
        return original == null || original.isBlank() ? safe(name()) : original;
    }

    default byte[] readAllBytes() throws IOException {
        try (InputStream input = openStream()) {
            return input.readAllBytes();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
