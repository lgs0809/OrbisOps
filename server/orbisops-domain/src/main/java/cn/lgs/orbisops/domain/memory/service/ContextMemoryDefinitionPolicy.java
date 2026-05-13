package cn.lgs.orbisops.domain.memory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Domain policy for Context Memory classifications, lifecycle values and scope consistency. */
public class ContextMemoryDefinitionPolicy {

    private static final Set<String> SCOPE_TYPES = Set.of("USER", "PROJECT");
    private static final Set<String> MEMORY_TYPES = Set.of(
            "USER_PREFERENCE",
            "USER_WORKFLOW",
            "USER_DOMAIN_FOCUS",
            "PROJECT_CONTEXT",
            "PROJECT_CONVENTION",
            "PROJECT_GLOSSARY");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "ARCHIVED", "DISABLED");

    public String normalizeScope(String scopeType, boolean required) {
        if (!hasText(scopeType)) {
            if (required) throw new IllegalArgumentException("scopeType 不能为空");
            return "";
        }
        String normalized = scopeType.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(normalized)) {
            if (required) throw new IllegalArgumentException("scopeType 只允许 USER/PROJECT");
            return "";
        }
        return normalized;
    }

    public String normalizeMemoryType(String memoryType, boolean required) {
        if (!hasText(memoryType)) {
            if (required) throw new IllegalArgumentException("memoryType 不能为空");
            return "";
        }
        String normalized = memoryType.trim().toUpperCase(Locale.ROOT);
        if (!MEMORY_TYPES.contains(normalized)) {
            if (required) throw new IllegalArgumentException("memoryType 不在允许范围：" + memoryType);
            return "";
        }
        return normalized;
    }

    public String normalizeStatus(String status, boolean required) {
        if (!hasText(status)) return required ? "ACTIVE" : "";
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            if (required) throw new IllegalArgumentException("status 只允许 ACTIVE/ARCHIVED/DISABLED");
            return "";
        }
        return normalized;
    }

    public void validateScopeAndType(String scopeType, String memoryType) {
        if ("USER".equals(scopeType) && !value(memoryType).startsWith("USER_")) {
            throw new IllegalArgumentException("USER scope 只能保存 USER_* 类型 Memory");
        }
        if ("PROJECT".equals(scopeType) && !value(memoryType).startsWith("PROJECT_")) {
            throw new IllegalArgumentException("PROJECT scope 只能保存 PROJECT_* 类型 Memory");
        }
    }

    public BigDecimal normalizeConfidence(BigDecimal confidence) {
        BigDecimal value = confidence == null ? BigDecimal.valueOf(0.8D) : confidence;
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return value;
    }

    public List<String> memoryTypesForScene(String scene) {
        String normalized = value(scene).toUpperCase(Locale.ROOT);
        if (containsAny(normalized, "OPS", "TROUBLE", "DIAGNOSIS", "ALERT")) {
            return List.of("PROJECT_GLOSSARY", "PROJECT_CONVENTION", "USER_PREFERENCE");
        }
        if (containsAny(normalized, "PLAN", "DISCUSS", "DESIGN", "方案")) {
            return List.of("PROJECT_CONTEXT", "PROJECT_CONVENTION", "USER_WORKFLOW", "USER_DOMAIN_FOCUS");
        }
        if (containsAny(normalized, "DOC", "REPORT", "WRITE", "文档")) {
            return List.of("PROJECT_CONTEXT", "PROJECT_CONVENTION", "PROJECT_GLOSSARY", "USER_PREFERENCE");
        }
        if (containsAny(normalized, "SKILL", "EVOLVER")) {
            return List.of("PROJECT_CONTEXT", "PROJECT_CONVENTION", "PROJECT_GLOSSARY");
        }
        return List.of("USER_PREFERENCE", "PROJECT_CONTEXT");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
