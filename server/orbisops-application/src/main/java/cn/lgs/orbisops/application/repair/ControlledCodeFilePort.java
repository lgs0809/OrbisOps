package cn.lgs.orbisops.application.repair;

import java.nio.file.Path;
import java.util.List;

public interface ControlledCodeFilePort {

    String read(Path root, String relativePath, boolean mustExist);

    List<SearchHit> grep(
            Path root,
            String glob,
            String query,
            boolean regex,
            boolean caseSensitive,
            int limit);

    List<FileEntry> glob(Path root, String pattern, int limit);

    boolean exists(Path root, String relativePath);

    void write(Path root, String relativePath, String content);

    Path directory(Path root, String relativePath);

    ProcessOutput execute(Path cwd, List<String> command, int timeoutMs, int outputLimitBytes);

    record SearchHit(String path, int line, String text) {
    }

    record FileEntry(String path, long sizeBytes, String modifiedAt) {
    }

    record ProcessOutput(int exitCode, String output, boolean truncated, long durationMs) {
    }
}
