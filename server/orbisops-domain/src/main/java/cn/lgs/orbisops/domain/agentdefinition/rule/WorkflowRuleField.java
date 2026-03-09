package cn.lgs.orbisops.domain.agentdefinition.rule;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record WorkflowRuleField(String path, List<String> segments) {

    private static final Set<String> ALLOWED_ROOTS = Set.of(
            "input", "runtime", "nodeOutput", "toolResult",
            "evidence", "approval", "error");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_PATH_LENGTH = 256;
    private static final int MAX_SEGMENTS = 16;

    public WorkflowRuleField(String path) {
        this(path, parse(path));
    }

    public WorkflowRuleField {
        path = path == null ? "" : path.trim();
        segments = segments == null ? List.of() : List.copyOf(segments);
        if (path.isBlank() || path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_INVALID:" + path);
        }
        if (segments.size() < 2 || segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_DEPTH_INVALID:" + path);
        }
        if (!ALLOWED_ROOTS.contains(segments.get(0))) {
            throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_ROOT_FORBIDDEN:" + segments.get(0));
        }
        if (segments.stream().anyMatch(segment -> !SEGMENT.matcher(segment).matches())) {
            throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_SEGMENT_INVALID:" + path);
        }
    }

    public String root() {
        return segments.get(0);
    }

    public List<String> nestedSegments() {
        return segments.subList(1, segments.size());
    }

    public static Set<String> allowedRoots() {
        return ALLOWED_ROOTS;
    }

    private static List<String> parse(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.contains("[") || normalized.contains("]")
                || normalized.contains("(") || normalized.contains(")")
                || normalized.contains("#") || normalized.contains("@")) {
            throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_SYNTAX_FORBIDDEN:" + normalized);
        }
        return normalized.isBlank() ? List.of() : List.of(normalized.split("\\."));
    }
}
