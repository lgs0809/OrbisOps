package cn.lgs.orbisops.domain.skill.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Framework-neutral policy for Skill Package artifact paths, content and static security checks. */
public final class SkillPackageArtifactPolicy {

    public static final String ENCODING_UTF8 = "UTF8";
    public static final String ENCODING_BASE64 = "BASE64";

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "txt", "json", "yaml", "yml", "xml", "sql", "sh", "py", "js", "ts",
            "tsx", "jsx", "java", "kt", "properties", "toml", "csv", "css", "scss", "html",
            "graphql", "proto", "ini", "conf", "mustache", "hbs", "svg");
    private static final Set<String> BINARY_ASSET_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "docx", "xlsx", "pptx",
            "ttf", "otf", "woff", "woff2");
    private static final Set<String> ALLOWED_ARTIFACT_ROLES = Set.of(
            "RESOURCE", "SCRIPT", "TEMPLATE", "EVAL", "REFERENCE", "ASSET", "METADATA");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?im)\\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|secret[_-]?key|private[_-]?key|credential|authorization)\\b\\s*[:=]\\s*[\\\"']?([^\\s\\\"'#,;}{]{4,})");
    private static final List<String> PRIVATE_KEY_MARKERS = List.of(
            "-----begin private key-----", "-----begin rsa private key-----",
            "-----begin ec private key-----", "-----begin openssh private key-----");
    private static final List<String> POLICY_BYPASS_TEXT = List.of(
            "绕过审批", "跳过审批", "绕过沙箱", "跳过沙箱", "绕过 landingruntime",
            "绕过 tool router", "关闭审计", "bypass approval", "skip approval",
            "bypass sandbox", "disable audit");
    private static final List<String> POLICY_BYPASS_NEGATION_SUFFIXES = List.of(
            "不", "不能", "不得", "不可", "不要", "不允许", "禁止", "严禁", "避免", "防止",
            "do not", "don't", "must not", "should not", "may not", "cannot", "can't", "never",
            "do not attempt to", "must never", "avoid", "prevent", "forbid", "prohibit");
    private static final List<String> SCRIPT_FORBIDDEN = List.of(
            "kubectl apply", "kubectl delete", "kubectl patch", "kubectl scale",
            "helm upgrade", "helm delete", "terraform apply", "terraform destroy",
            "docker restart", "docker rm", "docker system prune", "redis-cli", "mysql ", "psql ",
            "git push", "git reset", "git clean", "git merge", "git rebase",
            "rm -rf", "sudo ", "ssh ", "scp ", "curl ", "wget ", "nc ");

    private SkillPackageArtifactPolicy() {
    }

    public static ValidatedArtifact validate(Object pathValue,
                                             Object roleValue,
                                             Object mediaTypeValue,
                                             Object encodingValue,
                                             Object contentValue,
                                             boolean executable,
                                             long maxArtifactBytes) {
        String path = normalizePath(pathValue);
        String requestedRole = text(roleValue, "RESOURCE").toUpperCase(Locale.ROOT);
        String role = "ENTRYPOINT".equals(requestedRole)
                ? requestedRole
                : normalizeRole(requestedRole);
        String mediaType = text(mediaTypeValue, inferMediaType(path));
        String encoding = normalizeEncoding(encodingValue, path);
        String content = contentValue == null ? "" : String.valueOf(contentValue);
        if (executable) {
            throw new IllegalArgumentException("SKILL_PACKAGE_EXECUTABLE_ARTIFACT_FORBIDDEN：" + path);
        }
        String extension = extension(path);
        if (!TEXT_EXTENSIONS.contains(extension) && !BINARY_ASSET_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_TYPE_FORBIDDEN：" + path);
        }
        boolean binaryAsset = BINARY_ASSET_EXTENSIONS.contains(extension);
        if (binaryAsset && !ENCODING_BASE64.equals(encoding)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_BINARY_ARTIFACT_REQUIRES_BASE64：" + path);
        }
        if (!binaryAsset && !ENCODING_UTF8.equals(encoding)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_TEXT_ARTIFACT_REQUIRES_UTF8：" + path);
        }
        if (binaryAsset && ("SCRIPT".equals(role) || path.startsWith("scripts/"))) {
            throw new IllegalArgumentException("SKILL_PACKAGE_BINARY_SCRIPT_FORBIDDEN：" + path);
        }
        byte[] bytes = bytes(path, role, encoding, content);
        if (bytes.length > maxArtifactBytes) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_TOO_LARGE：" + path);
        }
        return new ValidatedArtifact(path, role, mediaType, encoding, content,
                sha256(bytes), bytes.length);
    }

    public static String normalizePath(Object value) {
        String raw = text(value, "").replace('\\', '/');
        if (raw.isBlank() || raw.length() > 240 || raw.startsWith("/")
                || raw.matches("^[A-Za-z]:.*") || raw.contains("\u0000")) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_PATH_INVALID：" + raw);
        }
        for (String segment : raw.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_PATH_TRAVERSAL：" + raw);
            }
        }
        String normalized;
        try {
            normalized = Path.of(raw).normalize().toString().replace('\\', '/');
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_PATH_INVALID：" + raw, e);
        }
        if (!raw.equals(normalized)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_PATH_NOT_CANONICAL：" + raw);
        }
        return normalized;
    }

    public static String normalizeRole(Object value) {
        String role = text(value, "RESOURCE").toUpperCase(Locale.ROOT);
        if (!ALLOWED_ARTIFACT_ROLES.contains(role)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_ROLE_INVALID：" + role);
        }
        return role;
    }

    public static String normalizeEncoding(Object value, String path) {
        String encoding = text(value, BINARY_ASSET_EXTENSIONS.contains(extension(path))
                ? ENCODING_BASE64 : ENCODING_UTF8).toUpperCase(Locale.ROOT);
        if (!Set.of(ENCODING_UTF8, ENCODING_BASE64).contains(encoding)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ENCODING_INVALID：" + encoding);
        }
        return encoding;
    }

    public static String inferMediaType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) return "text/markdown; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/yaml; charset=utf-8";
        if (lower.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (lower.endsWith(".csv")) return "text/csv; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        return "text/plain; charset=utf-8";
    }

    private static byte[] bytes(String path, String role, String encoding, String content) {
        if (ENCODING_BASE64.equals(encoding)) {
            try {
                return Base64.getDecoder().decode(content);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("SKILL_PACKAGE_BASE64_INVALID：" + path, e);
            }
        }
        if (looksBinary(content)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_TEXT_CONTAINS_BINARY_CONTROL：" + path);
        }
        scanText(path, role, content);
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static void scanText(String path, String role, String content) {
        String lower = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (PRIVATE_KEY_MARKERS.stream().anyMatch(lower::contains)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_PRIVATE_KEY_FORBIDDEN：" + path);
        }
        if (containsPolicyBypassDirective(lower)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_POLICY_BYPASS_FORBIDDEN：" + path);
        }
        Matcher matcher = SECRET_ASSIGNMENT.matcher(content == null ? "" : content);
        while (matcher.find()) {
            String supplied = matcher.group(2);
            if (!placeholderSecret(supplied)) {
                throw new IllegalArgumentException("SKILL_PACKAGE_PLAINTEXT_SECRET_FORBIDDEN：" + path);
            }
        }
        if ("SCRIPT".equals(role) || path.startsWith("scripts/")) {
            if (SCRIPT_FORBIDDEN.stream().anyMatch(lower::contains)
                    || lower.contains("http://") || lower.contains("https://")
                    || lower.contains("| sh") || lower.contains("| bash")) {
                throw new IllegalArgumentException("SKILL_PACKAGE_UNSAFE_SCRIPT_FORBIDDEN：" + path);
            }
        }
    }

    private static boolean containsPolicyBypassDirective(String lower) {
        for (String phrase : POLICY_BYPASS_TEXT) {
            int offset = 0;
            while (offset < lower.length()) {
                int index = lower.indexOf(phrase, offset);
                if (index < 0) break;
                int sentenceStart = Math.max(
                        Math.max(lower.lastIndexOf('\n', index - 1), lower.lastIndexOf('。', index - 1)),
                        Math.max(lower.lastIndexOf('；', index - 1), lower.lastIndexOf(';', index - 1))) + 1;
                int sentenceEnd = lower.length();
                for (char delimiter : new char[]{'\n', '。', '；', ';'}) {
                    int candidate = lower.indexOf(delimiter, index + phrase.length());
                    if (candidate >= 0) sentenceEnd = Math.min(sentenceEnd, candidate);
                }
                String sentence = lower.substring(sentenceStart, sentenceEnd)
                        .replaceAll("\\s+", " ")
                        .trim();
                int phraseInSentence = sentence.indexOf(phrase);
                String prefix = phraseInSentence < 0 ? "" : sentence.substring(0, phraseInSentence).trim();
                String suffix = phraseInSentence < 0 ? "" : sentence.substring(phraseInSentence + phrase.length()).trim();
                boolean negated = POLICY_BYPASS_NEGATION_SUFFIXES.stream().anyMatch(prefix::endsWith);
                boolean guardRule = isPolicyGuardRule(prefix, suffix);
                if (!negated && !guardRule) return true;
                offset = index + phrase.length();
            }
        }
        return false;
    }

    private static boolean isPolicyGuardRule(String prefix, String suffix) {
        boolean conditionalSubject = (prefix.contains("如果") || prefix.contains("若") || prefix.contains("当"))
                && (prefix.contains("要求") || prefix.contains("请求") || prefix.contains("试图") || prefix.contains("尝试"));
        boolean rejectionOutcome = suffix.contains("拒绝") || suffix.contains("禁止") || suffix.contains("阻断")
                || suffix.contains("不得") || suffix.contains("不能") || suffix.contains("不允许");
        boolean englishConditional = (prefix.contains("if ") || prefix.startsWith("if"))
                && (prefix.contains("ask") || prefix.contains("request") || prefix.contains("attempt") || prefix.contains("try"));
        boolean englishRejection = suffix.contains("reject") || suffix.contains("deny") || suffix.contains("refuse")
                || suffix.contains("block") || suffix.contains("forbid") || suffix.contains("prohibit");
        return (conditionalSubject && rejectionOutcome) || (englishConditional && englishRejection);
    }

    private static boolean placeholderSecret(String supplied) {
        String value = text(supplied, "").toLowerCase(Locale.ROOT);
        return value.startsWith("${") || value.startsWith("{{") || value.startsWith("<")
                || value.contains("change-me") || value.contains("placeholder") || value.contains("example")
                || value.contains("redacted") || value.contains("***") || value.contains("secretref")
                || value.contains("credentialref") || value.startsWith("env:");
    }

    private static boolean looksBinary(String content) {
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == 0 || c < 0x09 || (c > 0x0D && c < 0x20)) return true;
        }
        return false;
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("计算 Skill artifact hash 失败", e);
        }
    }

    private static String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    public record ValidatedArtifact(String path,
                                    String role,
                                    String mediaType,
                                    String encoding,
                                    String content,
                                    String contentHash,
                                    long sizeBytes) {
    }
}
