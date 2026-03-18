package cn.lgs.orbisops.domain.evidence.model;

import java.util.List;

public record ToolResultPage(
        String resultId,
        int offset,
        int limit,
        int totalLines,
        List<String> lines,
        String outputHash) {

    public ToolResultPage {
        resultId = required(resultId, "TOOL_RESULT_ID_REQUIRED");
        offset = Math.max(0, offset);
        limit = Math.max(1, limit);
        totalLines = Math.max(0, totalLines);
        lines = lines == null ? List.of() : List.copyOf(lines);
        outputHash = required(outputHash, "TOOL_RESULT_OUTPUT_HASH_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
