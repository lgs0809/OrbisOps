package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import java.math.BigDecimal;
import java.util.*;

/** Deterministic checks over immutable, authorized tool outputs; tool completion is not a check. */
public final class TaskAcceptancePolicy {
    public boolean isReceiptMetadata(String pointer, Map<String,Object> content) {
        if (!TaskReceiptEvidencePolicy.scopedObservation(content)) return false;
        if (pointer == null || !pointer.startsWith("/")) return false;
        String first = pointer.split("/", -1)[1].replace("~1", "/").replace("~0", "~").toLowerCase(Locale.ROOT);
        return Set.of("status", "scope", "queryid", "queryfingerprint", "observedat", "kind", "source").contains(first);
    }
    public void validate(TaskAcceptanceRequest request) {
        if (request == null || request.requestId() == null || !request.requestId().matches("[a-zA-Z0-9-]{8,80}")
                || request.revision() < 1 || request.goalReview() == null || request.goalReview().trim().length() < 8
                || request.goalReview().length() > 2000 || request.criteria().isEmpty()
                || request.criteria().size() > TaskAcceptanceRequest.MAX_CRITERIA)
            throw new IllegalArgumentException("TASK_ACCEPTANCE_ASSERTIONS_REQUIRED");
        Set<String> unique = new HashSet<>();
        for (var c : request.criteria()) {
            if (c == null || c.resultId() == null || c.resultId().length() > 100 || c.resultId().isBlank()
                    || c.outputHash() == null || !c.outputHash().matches("[0-9a-f]{64}")
                    || c.pointer() == null || !c.pointer().startsWith("/") || c.pointer().length() > 400
                    || c.operator() == null || !Set.of("EQ", "LE", "GE").contains(c.operator())
                    || !(c.expected() instanceof String || c.expected() instanceof Number || c.expected() instanceof Boolean)
                    || String.valueOf(c.expected()).length() > 2000)
                throw new IllegalArgumentException("TASK_ACCEPTANCE_ASSERTION_INVALID");
            // Lower and upper bounds are distinct assertions over the same immutable business field.
            if (!unique.add(c.resultId() + ":" + c.pointer() + ":" + c.operator()))
                throw new IllegalArgumentException("TASK_ACCEPTANCE_DUPLICATE_ASSERTION");
            if (!"EQ".equals(c.operator())) number(c.expected());
        }
    }

    public Map<String, Object> check(TaskAcceptanceRequest.Criterion criterion, Map<String, Object> normalized) {
        if (isReceiptMetadata(criterion.pointer(), normalized))
            throw new IllegalArgumentException("TASK_ACCEPTANCE_NEEDS_TASK_RESULT_NOT_RECEIPT_METADATA");
        Object actual = at(new TaskAcceptanceObservabilityProjection().content(normalized), criterion.pointer());
        String verdict = "UNKNOWN";
        if (actual instanceof String || actual instanceof Boolean || actual instanceof Number) {
            try {
                boolean pass = switch (criterion.operator()) {
                    case "LE" -> number(actual).compareTo(number(criterion.expected())) <= 0;
                    case "GE" -> number(actual).compareTo(number(criterion.expected())) >= 0;
                    default -> actual instanceof Number && criterion.expected() instanceof Number
                            ? number(actual).compareTo(number(criterion.expected())) == 0
                            : Objects.equals(actual, criterion.expected());
                };
                verdict = pass ? "PASSED" : "FAILED";
            } catch (IllegalArgumentException ignored) { /* Missing/nonnumeric results stay unknown. */ }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", criterion.resultId()); result.put("outputHash", criterion.outputHash());
        result.put("pointer", criterion.pointer()); result.put("operator", criterion.operator());
        result.put("expected", criterion.expected()); result.put("actual", actual); result.put("verdict", verdict);
        return result;
    }

    private BigDecimal number(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("TASK_ACCEPTANCE_NUMBER_REQUIRED");
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("TASK_ACCEPTANCE_FINITE_NUMBER_REQUIRED"); }
    }
    private Object at(Object source, String pointer) {
        Object current = source;
        for (String raw : pointer.substring(1).split("/", -1)) {
            if (raw.matches(".*~(?:[^01]|$).*")) return null;
            String key = raw.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map) current = map.get(key);
            else if (current instanceof List<?> list && key.matches("0|[1-9][0-9]{0,5}")) {
                int index = Integer.parseInt(key); current = index < list.size() ? list.get(index) : null;
            } else return null;
        }
        return current;
    }
}
