package cn.lgs.orbisops.domain.repair.service;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ControlledCodePolicy {

    public static final int DEFAULT_READ_LIMIT = 200;
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private static final Set<String> READ_ONLY_COMMANDS = Set.of(
            "pwd", "ls", "find", "rg", "grep", "cat", "head", "tail", "sed");
    private static final Set<String> TEST_COMMANDS = Set.of(
            "mvn", "./mvnw", "npm", "pnpm", "yarn", "gradle", "./gradlew", "make",
            "java", "python", "python3", "pytest", "go", "cargo", "curl");
    private static final Set<String> DANGEROUS_TOKENS = Set.of(
            "sudo", "su", "ssh", "scp", "wget", "nc", "telnet", "rm", "mv", "cp", "chmod", "chown",
            "kubectl", "helm", "terraform", "ansible-playbook", "docker", "mysql", "psql", "redis-cli",
            "-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprintf", "-fls",
            "-i", "--in-place", "--pre", "--hostname-bin");
    private static final Pattern SECRET_LINE = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|authorization|bearer|jwt|session|cookie)\\s*[:=]\\s*[^\\s]+");

    public String readablePath(String path) {
        String safe = safeRelativePath(path);
        if (sensitiveRawDenied(Path.of(safe).getFileName().toString())) {
            throw new SecurityException("该敏感文件默认禁止读取原文：" + safe);
        }
        return safe;
    }

    public String writablePath(String path) {
        String safe = safeRelativePath(path);
        String name = Path.of(safe).getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals(".env") || name.startsWith(".env.") || sensitiveRawDenied(name)) {
            throw new SecurityException("该敏感文件禁止通过代码修复工具写入：" + safe);
        }
        return safe;
    }

    public String safeRelativePath(String filePath) {
        String normalized = value(filePath).replace('\\', '/');
        if (normalized.isBlank()) throw new IllegalArgumentException("文件路径不能为空");
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || normalized.startsWith("/") || normalized.contains("\u0000")
                || normalized.contains("\n") || normalized.contains("\r")
                || ".".equals(path.toString()) || path.startsWith("..")
                || normalized.equals(".git") || normalized.startsWith(".git/")) {
            throw new SecurityException("文件路径不安全");
        }
        return path.toString().replace('\\', '/');
    }

    public String content(String content) {
        String value = content == null ? "" : content;
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("code.write 拒绝大文件或二进制内容");
        }
        return value;
    }

    public int readStart(int value) {
        return Math.max(1, value);
    }

    public int readLimit(int value) {
        return Math.max(1, Math.min(value <= 0 ? DEFAULT_READ_LIMIT : value, 1000));
    }

    public int grepLimit(int value) {
        return Math.max(1, Math.min(value <= 0 ? 50 : value, 200));
    }

    public int globLimit(int value) {
        return Math.max(1, Math.min(value <= 0 ? 100 : value, 500));
    }

    public int timeoutMs(int value) {
        return Math.max(1000, Math.min(value <= 0 ? 30_000 : value, 300_000));
    }

    public List<String> command(String command, ControlledCodeEffect effect, String workspaceId) {
        List<String> tokens = tokenize(command);
        validateCommand(tokens, effect, value(workspaceId));
        return tokens;
    }

    public String writerIdentity(String runId, String actor) {
        String run = value(runId);
        return run.isBlank() ? "actor:" + required(actor, "writer actor") : "run:" + run;
    }

    public void requireReadBeforeWrite(String expectedHash, String currentContent) {
        if (!sha256(currentContent).equals(value(expectedHash))) {
            throw new SecurityException("写入前必须先通过 code.read 读取该文件，且文件读取后不能被外部修改");
        }
    }

    public int occurrences(String source, String needle) {
        String requiredNeedle = required(needle, "code.edit 必须提供 oldString");
        int count = 0;
        int index = 0;
        while ((index = value(source).indexOf(requiredNeedle, index)) >= 0) {
            count++;
            index += requiredNeedle.length();
        }
        return count;
    }

    public String maskSecrets(String text) {
        return SECRET_LINE.matcher(text == null ? "" : text).replaceAll(match -> {
            String current = match.group();
            int equals = current.indexOf('=');
            int colon = current.indexOf(':');
            int split = equals < 0 ? colon : colon < 0 ? equals : Math.min(equals, colon);
            return split < 0 ? "***" : current.substring(0, split + 1) + "***";
        });
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算 hash 失败", e);
        }
    }

    public boolean proofRequired(ControlledCodeEffect effect, int exitCode, String packageId,
                                 int packageVersion, String packageHash) {
        return exitCode == 0 && effect != null && effect.testOrVerify()
                && !value(packageId).isBlank() && packageVersion > 0 && !value(packageHash).isBlank();
    }

    public String required(String value, String message) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    public String value(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> tokenize(String command) {
        String value = required(command, "code.bash 必须提供 command");
        if (value.contains("\n") || value.contains("\r")) {
            throw new SecurityException("Bash 命令不能包含换行");
        }
        return Arrays.stream(value.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private void validateCommand(List<String> tokens, ControlledCodeEffect effect, String workspaceId) {
        if (tokens.isEmpty()) throw new IllegalArgumentException("命令不能为空");
        ControlledCodeEffect expected = effect == null ? ControlledCodeEffect.READ_ONLY : effect;
        String first = tokens.get(0);
        if (tokens.stream().anyMatch(DANGEROUS_TOKENS::contains)) {
            throw new SecurityException("Bash 命令包含默认拒绝的命令：" + first);
        }
        String joined = String.join(" ", tokens);
        if (joined.contains("|") || joined.contains(";") || joined.contains("&&") || joined.contains("||")
                || joined.contains("`") || joined.contains("$(") || joined.contains(">") || joined.contains("<")) {
            throw new SecurityException("Bash 不允许 shell 管道、重定向或命令替换");
        }
        if ("git".equals(first)) {
            String sub = tokens.size() > 1 ? tokens.get(1) : "";
            if (Set.of("push", "pull", "fetch", "reset", "clean", "merge", "rebase").contains(sub)) {
                throw new SecurityException("Bash 默认拒绝 git " + sub);
            }
            if (Set.of("add", "commit").contains(sub)) {
                if (workspaceId.isBlank()) {
                    throw new SecurityException("git add/commit 只能在 repair workspace 内执行");
                }
                return;
            }
            if (Set.of("status", "diff", "log", "show", "blame", "grep", "rev-parse", "ls-files").contains(sub)) {
                return;
            }
            throw new SecurityException("不允许的 git 子命令：" + sub);
        }
        if (expected == ControlledCodeEffect.READ_ONLY && READ_ONLY_COMMANDS.contains(first)) return;
        if (expected.testOrVerify() && TEST_COMMANDS.contains(first)
                && !workspaceId.isBlank() && allowedTestCommand(tokens)) return;
        throw new SecurityException("命令不在当前 expectedEffect 白名单内：" + first + " / " + expected.name());
    }

    private boolean allowedTestCommand(List<String> tokens) {
        String first = tokens.get(0);
        if ("mvn".equals(first) || "./mvnw".equals(first)) {
            return tokens.stream().noneMatch(token -> Set.of(
                    "deploy", "release:perform", "release:prepare").contains(token));
        }
        if (Set.of("npm", "pnpm", "yarn").contains(first)) {
            return tokens.stream().noneMatch(token -> Set.of("publish", "login", "owner").contains(token));
        }
        if ("gradle".equals(first) || "./gradlew".equals(first)) {
            return tokens.stream().noneMatch(token -> Set.of("publish", "uploadArchives").contains(token));
        }
        if ("cargo".equals(first)) {
            return tokens.stream().noneMatch(token -> Set.of("publish", "login", "owner").contains(token));
        }
        if ("curl".equals(first)) {
            return tokens.stream()
                    .filter(token -> token.startsWith("http://") || token.startsWith("https://"))
                    .allMatch(token -> token.matches("https?://(localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]+)?(?:/.*)?"));
        }
        if (first.startsWith("./")) {
            return !first.contains("../") && !first.equals("./");
        }
        return TEST_COMMANDS.contains(first);
    }

    private boolean sensitiveRawDenied(String fileName) {
        String name = value(fileName).toLowerCase(Locale.ROOT);
        return name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12") || name.endsWith(".jks")
                || name.equals("id_rsa") || name.equals("id_dsa") || name.equals("credentials");
    }
}
