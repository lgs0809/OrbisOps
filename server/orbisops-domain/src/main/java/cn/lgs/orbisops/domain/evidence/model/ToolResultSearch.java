package cn.lgs.orbisops.domain.evidence.model;

import java.util.List;

public record ToolResultSearch(String resultId, List<Hit> hits) {

    public ToolResultSearch {
        resultId = required(resultId, "TOOL_RESULT_ID_REQUIRED");
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public record Hit(int line, String snippet) {
        public Hit {
            if (line < 1) throw new IllegalArgumentException("TOOL_RESULT_SEARCH_LINE_INVALID");
            snippet = snippet == null ? "" : snippet;
        }
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
