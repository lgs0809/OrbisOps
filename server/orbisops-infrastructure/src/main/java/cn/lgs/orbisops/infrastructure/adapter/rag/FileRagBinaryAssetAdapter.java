package cn.lgs.orbisops.infrastructure.adapter.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local filesystem storage for parsed RAG binary assets. */
@Component
public class FileRagBinaryAssetAdapter implements RagBinaryAssetPort {

    private final Path root;

    public FileRagBinaryAssetAdapter(
            @Value("${orbisops.rag.parse.media-storage-dir:data/rag-media}") String storageDir) {
        this.root = Path.of(StringUtils.hasText(storageDir) ? storageDir : "data/rag-media")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public Path store(String knowledge, String source, String fileName, byte[] content) throws IOException {
        Path directory = resolveUnderRoot(safeSegment(knowledge), safeSegment(source));
        Files.createDirectories(directory);
        Path target = resolveUnderRoot(directory, safeFileName(fileName));
        Files.write(target, content == null ? new byte[0] : content);
        return target;
    }

    @Override
    public boolean isRegularFile(Path path) {
        try {
            Path resolved = resolveExisting(path);
            return resolved != null && Files.isRegularFile(resolved);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public byte[] read(Path path) throws IOException {
        Path resolved = resolveExisting(path);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IOException("RAG_BINARY_ASSET_NOT_FOUND:" + path);
        }
        return Files.readAllBytes(resolved);
    }

    private Path resolveUnderRoot(String first, String second) {
        return ensureUnderRoot(root.resolve(first).resolve(second).normalize());
    }

    private Path resolveUnderRoot(Path directory, String fileName) {
        return ensureUnderRoot(directory.resolve(fileName).normalize());
    }

    private Path resolveExisting(Path path) {
        if (path == null) return null;
        Path resolved = path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
        return ensureUnderRoot(resolved);
    }

    private Path ensureUnderRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("RAG_BINARY_ASSET_PATH_OUTSIDE_ROOT");
        }
        return path;
    }

    private String safeSegment(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.contains("/") || safe.contains("\\") || ".".equals(safe) || "..".equals(safe)) {
            throw new IllegalArgumentException("RAG_BINARY_ASSET_SEGMENT_INVALID");
        }
        return safe;
    }

    private String safeFileName(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank() || safe.contains("/") || safe.contains("\\") || ".".equals(safe) || "..".equals(safe)) {
            throw new IllegalArgumentException("RAG_BINARY_ASSET_FILE_NAME_INVALID");
        }
        return safe;
    }
}
