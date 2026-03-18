package cn.lgs.orbisops.domain.evidence.service;

import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultDraft;
import cn.lgs.orbisops.domain.evidence.model.ToolResultPage;
import cn.lgs.orbisops.domain.evidence.model.ToolResultSearch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

public final class ToolResultPolicy {

    private final SensitiveDataRedactionPolicy redaction = new SensitiveDataRedactionPolicy();

    public Prepared prepare(ToolResultDraft draft) {
        if (draft == null) throw new IllegalArgumentException("TOOL_RESULT_DRAFT_REQUIRED");
        String output = maskSecrets(draft.output());
        String preview = truncateUtf8(output, draft.budget().maxBytes());
        return new Prepared(
                sha256(draft.query()),
                output,
                preview,
                !preview.equals(output),
                sha256(output));
    }

    public void authorize(ToolResult result, String projectId, String userId) {
        if (result == null) throw new IllegalArgumentException("TOOL_RESULT_REQUIRED");
        String project = value(projectId);
        String user = value(userId);
        if (!project.isBlank() && !project.equals(result.projectId())) {
            throw new SecurityException("USER_PERMISSION_DENIED：当前用户不能读取其他项目工具结果");
        }
        if (!result.userId().isBlank() && !user.isBlank() && !user.equals(result.userId())) {
            throw new SecurityException("USER_PERMISSION_DENIED：当前用户不能读取他人工具结果");
        }
    }

    public ToolResultPage page(ToolResult result, int offset, int limit) {
        List<String> lines = result.fullOutput().lines().toList();
        int start = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 10_000));
        int end = Math.min(lines.size(), start + safeLimit);
        return new ToolResultPage(
                result.resultId(), start, safeLimit, lines.size(),
                start >= lines.size() ? List.of() : lines.subList(start, end),
                result.outputHash());
    }

    public ToolResultSearch search(ToolResult result, String expression) {
        String pattern = required(expression, "TOOL_RESULT_SEARCH_PATTERN_REQUIRED");
        Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        List<ToolResultSearch.Hit> hits = new ArrayList<>();
        List<String> lines = result.fullOutput().lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (compiled.matcher(line).find()) hits.add(new ToolResultSearch.Hit(index + 1, line));
        }
        return new ToolResultSearch(result.resultId(), hits);
    }

    public int listLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    public String maskSecrets(String text) {
        return redaction.redactText(text);
    }

    public String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算工具输出 hash 失败", e);
        }
    }

    public String truncateUtf8(String text, int maxBytes) {
        String source = text == null ? "" : text;
        if (source.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return source;
        StringBuilder result = new StringBuilder();
        int accepted = 0;
        for (int index = 0; index < source.length();) {
            int codePoint = source.codePointAt(index);
            String token = new String(Character.toChars(codePoint));
            int bytes = token.getBytes(StandardCharsets.UTF_8).length;
            if (accepted + bytes > maxBytes) break;
            result.append(token);
            accepted += bytes;
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public record Prepared(
            String inputHash,
            String fullOutput,
            String preview,
            boolean truncated,
            String outputHash) {
    }
}
