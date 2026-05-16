package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.ControlledCodeFilePort;
import cn.lgs.orbisops.domain.repair.service.ControlledCodePolicy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Component
public class LocalControlledCodeFileAdapter implements ControlledCodeFilePort {

    private final ControlledCodePolicy policy = new ControlledCodePolicy();

    @Override
    public String read(Path root, String relativePath, boolean mustExist) {
        Path file = resolve(root, relativePath, mustExist);
        if (!Files.exists(file) && !mustExist) return "";
        try {
            if (Files.size(file) > ControlledCodePolicy.MAX_FILE_BYTES || binary(file)) {
                throw new IllegalArgumentException("拒绝读取大文件或二进制文件");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取文件失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<SearchHit> grep(
            Path root,
            String glob,
            String query,
            boolean regex,
            boolean caseSensitive,
            int limit) {
        Path safeRoot = root(root);
        Pattern pattern = Pattern.compile(regex ? query : Pattern.quote(query),
                caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        List<SearchHit> hits = new ArrayList<>();
        walk(safeRoot, glob, Math.max(1, limit), file -> {
            if (hits.size() >= limit) return;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size() && hits.size() < limit; index++) {
                if (pattern.matcher(lines.get(index)).find()) {
                    hits.add(new SearchHit(
                            safeRoot.relativize(file).toString().replace('\\', '/'),
                            index + 1,
                            lines.get(index)));
                }
            }
        });
        return List.copyOf(hits);
    }

    @Override
    public List<FileEntry> glob(Path root, String pattern, int limit) {
        Path safeRoot = root(root);
        List<FileEntry> entries = new ArrayList<>();
        walk(safeRoot, pattern, Math.max(1, limit), file -> entries.add(new FileEntry(
                safeRoot.relativize(file).toString().replace('\\', '/'),
                Files.size(file),
                Files.getLastModifiedTime(file).toString())));
        return List.copyOf(entries);
    }

    @Override
    public boolean exists(Path root, String relativePath) {
        return Files.isRegularFile(resolve(root, relativePath, false));
    }

    @Override
    public void write(Path root, String relativePath, String content) {
        Path file = resolve(root, relativePath, false);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("写入文件失败：" + e.getMessage(), e);
        }
    }

    @Override
    public Path directory(Path root, String relativePath) {
        Path safeRoot = root(root);
        String relative = relativePath == null ? "" : relativePath.trim();
        if (relative.isBlank()) return safeRoot;
        Path resolved = safeRoot.resolve(policy.safeRelativePath(relative)).normalize();
        try {
            Path real = resolved.toRealPath();
            if (!real.startsWith(safeRoot.toRealPath()) || !Files.isDirectory(real)) {
                throw new SecurityException("Bash cwd 不在允许根目录内");
            }
            return real;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Bash cwd 不在允许根目录内", e);
        }
    }

    @Override
    public ProcessOutput execute(Path cwd, List<String> command, int timeoutMs, int outputLimitBytes) {
        long started = System.nanoTime();
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory(cwd, "").toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            AtomicBoolean truncated = new AtomicBoolean(false);
            Thread reader = new Thread(
                    () -> copyLimited(process.getInputStream(), output, outputLimitBytes, truncated),
                    "controlled-code-output");
            reader.setDaemon(true);
            reader.start();
            boolean done = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) process.destroyForcibly();
            reader.join(2000);
            int exitCode = done ? process.exitValue() : 124;
            return new ProcessOutput(
                    exitCode,
                    output.toString(StandardCharsets.UTF_8),
                    truncated.get(),
                    (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("受控命令执行失败：" + e.getMessage(), e);
        }
    }

    private Path resolve(Path root, String relativePath, boolean mustExist) {
        Path safeRoot = root(root);
        String relative = policy.safeRelativePath(relativePath);
        Path path = safeRoot.resolve(relative).normalize();
        if (!path.startsWith(safeRoot)) throw new SecurityException("文件路径逃逸 repair workspace");
        try {
            if (mustExist) {
                Path real = path.toRealPath();
                if (!real.startsWith(safeRoot.toRealPath()) || !Files.isRegularFile(real)) {
                    throw new SecurityException("文件不在允许根目录内");
                }
                if (Files.size(real) > ControlledCodePolicy.MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("文件超过读取上限");
                }
                return real;
            }
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)
                    && !parent.toRealPath().startsWith(safeRoot.toRealPath())) {
                throw new SecurityException("父目录逃逸允许根目录");
            }
            return path;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析代码文件失败：" + e.getMessage(), e);
        }
    }

    private void walk(Path root, String glob, int limit, FileConsumer consumer) {
        String expression = glob == null || glob.isBlank() ? "**/*" : glob;
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + expression);
            try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
                List<Path> candidates = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !path.toString().replace('\\', '/').contains("/.git/"))
                        .filter(path -> matcher.matches(root.relativize(path)))
                        .limit(limit)
                        .toList();
                for (Path file : candidates) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    try {
                        policy.readablePath(relative);
                    } catch (SecurityException denied) {
                        continue;
                    }
                    if (Files.size(file) <= ControlledCodePolicy.MAX_FILE_BYTES && !binary(file)) {
                        consumer.accept(file);
                    }
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("文件遍历失败：" + e.getMessage(), e);
        }
    }

    private Path root(Path root) {
        if (root == null) throw new IllegalArgumentException("CONTROLLED_CODE_ROOT_REQUIRED");
        try {
            Path real = root.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(real)) throw new IllegalArgumentException("CONTROLLED_CODE_ROOT_NOT_DIRECTORY");
            return real;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析代码根目录失败：" + e.getMessage(), e);
        }
    }

    private boolean binary(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            for (byte value : input.readNBytes(4096)) if (value == 0) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void copyLimited(
            InputStream input,
            ByteArrayOutputStream output,
            int limit,
            AtomicBoolean truncated) {
        byte[] buffer = new byte[8192];
        int acceptedTotal = 0;
        try (input) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int accepted = Math.min(read, Math.max(0, limit - acceptedTotal));
                if (accepted > 0) {
                    output.write(buffer, 0, accepted);
                    acceptedTotal += accepted;
                }
                if (accepted < read) truncated.set(true);
            }
        } catch (Exception ignored) {
            // Process exit code remains authoritative.
        }
    }

    @FunctionalInterface
    private interface FileConsumer {
        void accept(Path path) throws Exception;
    }
}
