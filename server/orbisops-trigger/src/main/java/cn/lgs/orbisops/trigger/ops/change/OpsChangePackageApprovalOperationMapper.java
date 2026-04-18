package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps legacy operation snapshots into typed approval safety facts. */
final class OpsChangePackageApprovalOperationMapper {

    private static final OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER =
            new OpsPreApprovalStructuredValueReader();
    private static final List<String> REQUIRED_FIELDS = List.of(
            "operationId",
            "adapterType",
            "arguments",
            "resourceScope",
            "riskLevel",
            "targetEnvironment",
            "effectType",
            "effectScope",
            "mutability",
            "readOnly",
            "writesTargetResource",
            "requiresChangePackage",
            "requiresApproval");

    List<ChangePackageApprovalOperation> map(List<Map<String, Object>> operations) {
        if (operations == null || operations.isEmpty()) return List.of();
        List<ChangePackageApprovalOperation> result = new ArrayList<>(operations.size());
        for (Map<String, Object> operation : operations) {
            result.add(map(operation));
        }
        return List.copyOf(result);
    }

    ChangePackageApprovalOperation map(Map<String, Object> operation) {
        Map<String, Object> raw = operation == null ? Map.of() : operation;
        Set<String> presentFields = new LinkedHashSet<>();
        for (String field : REQUIRED_FIELDS) {
            if (hasValue(raw.get(field))) presentFields.add(field);
        }
        Object toolName = firstNonNull(raw.get("toolName"), raw.get("remoteToolName"));
        if (hasValue(toolName)) presentFields.add("toolName");
        return new ChangePackageApprovalOperation(
                raw,
                text(raw.get("operationId")),
                text(firstNonNull(raw.get("toolsetId"), raw.get("mcpId"))),
                text(toolName),
                text(raw.get("adapterType")),
                text(raw.get("resourceScope")),
                text(raw.get("riskLevel")),
                text(raw.get("targetEnvironment")),
                text(raw.get("effectType")),
                text(raw.get("effectScope")),
                text(raw.get("mutability")),
                presentFields,
                bool(raw.get("writesTargetResource"), true),
                bool(raw.get("requiresChangePackage"), true),
                bool(raw.get("requiresApproval"), true),
                structurePresent(firstNonNull(raw.get("preconditions"), raw.get("precondition"))),
                structurePresent(firstNonNull(raw.get("postCheck"), raw.get("postCheckPlan"))),
                structurePresent(firstNonNull(raw.get("rollbackPlan"), raw.get("rollbackSteps"))),
                structurePresent(raw.get("rollbackPrecondition")),
                structurePresent(raw.get("manualFallback")),
                text(firstNonNull(raw.get("argumentsHash"), raw.get("arguments_hash"))),
                text(firstNonNull(raw.get("preconditionHash"), raw.get("precondition_hash"))),
                text(firstNonNull(raw.get("postCheckHash"), raw.get("post_check_hash"))),
                text(firstNonNull(raw.get("rollbackHash"), raw.get("rollback_hash"))),
                text(firstNonNull(raw.get("operationHash"), raw.get("operation_hash"))));
    }

    private boolean structurePresent(Object value) {
        return !STRUCTURED_VALUE_READER.object(value).isEmpty();
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        if (!normalized.isBlank()) {
            return Boolean.parseBoolean(normalized) || "1".equals(normalized);
        }
        return fallback;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private boolean hasValue(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
