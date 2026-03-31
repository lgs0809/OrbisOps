package cn.lgs.orbisops.domain.toolset.service;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Security and output policy for local filesystem and process operations. */
public final class LocalHostPolicy {

    public String allowedFile(Object value, String allowedRoots) {
        return allowedPath(
                value,
                allowedRoots,
                "LOCAL_LOG_PATH_NOT_ALLOWED：日志文件不在白名单路径内");
    }

    public String allowedDirectory(Object value, String allowedRoots) {
        return allowedPath(
                value,
                allowedRoots,
                "DOCKER_COMPOSE_PATH_NOT_ALLOWED：composeDir 不在白名单路径内");
    }

    public String dockerName(Object value) {
        String name = required(value, "docker name required");
        if (!name.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                    "非法 Docker 资源名称：" + name);
        }
        return name;
    }

    public String mask(Object value) {
        return text(value)
                .replaceAll(
                        "(?i)(password|passwd|pwd|secret|token|access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|authorization|bearer|jwt|session|cookie)\\s*[:=]\\s*[^\\s,;&\"}]+",
                        "$1=***")
                .replaceAll(
                        "(?i)([a-z][a-z0-9+.-]*://[^\\s/@:]+:)([^\\s/@]+)(@)",
                        "$1***$3");
    }

    public String abbreviate(Object value, int maximum) {
        String result = text(value);
        int limit = Math.max(1, maximum);
        return result.length() <= limit
                ? result
                : result.substring(0, limit);
    }

    public int limit(Object value, int maximum, int fallback) {
        int upper = Math.max(1, maximum);
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(text(value));
            return Math.max(1, Math.min(upper, parsed));
        } catch (RuntimeException ignored) {
            return Math.max(1, Math.min(upper, fallback));
        }
    }

    private String allowedPath(
            Object value,
            String allowedRoots,
            String error) {
        Path path = Path.of(required(value, "path required"))
                .toAbsolutePath()
                .normalize();
        List<Path> roots = roots(allowedRoots);
        if (roots.stream().noneMatch(path::startsWith)) {
            throw new SecurityException(error);
        }
        return path.toString();
    }

    private List<Path> roots(String value) {
        return Arrays.stream(text(value).split(","))
                .map(String::trim)
                .filter(root -> !root.isBlank())
                .map(root -> Path.of(root).toAbsolutePath().normalize())
                .toList();
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
