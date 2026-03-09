package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps legacy operation snapshots into typed pre-approval validation facts. */
final class OpsPreApprovalOperationMapper {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "operationId",
            "toolName",
            "adapterType",
            "arguments",
            "resourceScope",
            "targetEnvironment",
            "riskLevel",
            "effectType",
            "effectScope",
            "mutability",
            "readOnly",
            "writesTargetResource",
            "requiresChangePackage",
            "requiresApproval");

    List<MappedOperation> map(List<Map<String, Object>> operations) {
        if (operations == null || operations.isEmpty()) return List.of();
        List<MappedOperation> mapped = new ArrayList<>(operations.size());
        for (Map<String, Object> operation : operations) {
            Map<String, Object> raw = operation == null ? Map.of() : operation;
            Set<String> presentFields = new LinkedHashSet<>();
            for (String field : REQUIRED_FIELDS) {
                if (hasText(raw.get(field))) presentFields.add(field);
            }
            ChangePackageValidationOperation facts = new ChangePackageValidationOperation(
                    text(raw.get("operationId")),
                    text(raw.get("toolName")),
                    text(raw.get("adapterType")),
                    text(firstNonNull(raw.get("mcpId"), raw.get("toolsetId"))),
                    text(raw.get("resourceScope")),
                    text(raw.get("targetEnvironment")),
                    text(raw.get("riskLevel")),
                    text(raw.get("effectType")),
                    text(raw.get("effectScope")),
                    text(raw.get("mutability")),
                    presentFields,
                    bool(raw.get("writesTargetResource"), true),
                    bool(raw.get("requiresChangePackage"), true));
            mapped.add(new MappedOperation(raw, facts));
        }
        return List.copyOf(mapped);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record MappedOperation(Map<String, Object> raw,
                           ChangePackageValidationOperation facts) {
        MappedOperation {
            raw = raw == null || raw.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
            if (facts == null) throw new IllegalArgumentException("VALIDATION_OPERATION_FACTS_REQUIRED");
        }

        String remoteToolName() {
            Object value = raw.get("remoteToolName");
            return value == null || String.valueOf(value).trim().isEmpty()
                    ? facts.toolName()
                    : String.valueOf(value).trim();
        }

        Object arguments() {
            Object value = raw.get("arguments");
            return value == null ? Map.of() : value;
        }
    }
}
