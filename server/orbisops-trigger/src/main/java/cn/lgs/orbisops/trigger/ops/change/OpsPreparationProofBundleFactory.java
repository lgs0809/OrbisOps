package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationContext;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationProof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds trusted/untrusted proof views and the typed Domain preparation context. */
final class OpsPreparationProofBundleFactory {

    ProofBundle create(Input input) {
        if (input == null) throw new IllegalArgumentException("PREPARATION_PROOF_INPUT_REQUIRED");
        Map<String, Object> preflight = firstResult(
                input.activePrepare().get("preflightResult"),
                preflightFallback(input.request(), input.mcpSteps(), input.toolBindings()));
        Map<String, Object> dryRun = firstResult(
                input.activePrepare().get("dryRunResult"),
                dryRunFallback(input.request()));
        ChangePackagePreparationContext context = new ChangePackagePreparationContext(
                proof(preflight),
                proof(dryRun),
                input.mcpSteps().stream().map(this::operation).toList(),
                completeToolBindings(input.toolBindings()),
                input.trustedEvidencePresent(),
                repairIntent(input.request()),
                text(input.request().get("packageType"), ""),
                text(input.request().get("riskLevel"), ""),
                changedFiles(input.request()));
        return new ProofBundle(
                preflight,
                dryRun,
                context,
                notVerifiedItems(preflight, dryRun, context.requiresTrustedValidationProof()));
    }

    private Map<String, Object> preflightFallback(
            Map<String, Object> request,
            List<Map<String, Object>> mcpSteps,
            List<Map<String, Object>> toolBindings) {
        Object provided = request.get("preflightResult");
        if (provided instanceof Map<?, ?> map) {
            return untrustedUserInput("preflight", map);
        }
        boolean hasExecutableShape = !mcpSteps.isEmpty()
                && mcpSteps.stream()
                .map(this::operation)
                .allMatch(ChangePackagePreparationOperation::completeShape);
        boolean hasToolBinding = completeToolBindings(toolBindings);
        return new LinkedHashMap<>(Map.of(
                "status", hasExecutableShape && hasToolBinding ? "PASSED" : "FAILED",
                "parameterCheck", hasExecutableShape ? "PASSED" : "FAILED",
                "toolPolicyCheck", hasToolBinding ? "PASSED" : "PENDING",
                "targetResourceCheck", "UNKNOWN",
                "rollbackMaterialCheck", "PENDING",
                "warnings", List.of(
                        "PREPARE 只完成非生产预检查；包中显式 validation operation 仍需真实 validation proof。")));
    }

    private Map<String, Object> dryRunFallback(Map<String, Object> request) {
        Object provided = request.get("dryRunResult");
        if (provided instanceof Map<?, ?> map) {
            return untrustedUserInput("dryRun", map);
        }
        return new LinkedHashMap<>(Map.of(
                "supported", false,
                "executed", false,
                "status", "NOT_SUPPORTED",
                "warnings", List.of(
                        "当前项目工具未返回 dry-run 证明，不能伪造成功。")));
    }

    private Map<String, Object> firstResult(Object preferred,
                                            Map<String, Object> fallback) {
        return preferred instanceof Map<?, ?> map ? copy(map) : fallback;
    }

    private ChangePackagePreparationProof proof(Map<String, Object> source) {
        return new ChangePackagePreparationProof(
                text(source == null ? null : source.get("status"), ""),
                text(source == null
                        ? null
                        : firstNonNull(source.get("source"), source.get("proofSource")), ""),
                source == null || Boolean.TRUE.equals(source.get("untrusted")));
    }

    private ChangePackagePreparationOperation operation(Map<String, Object> source) {
        return new ChangePackagePreparationOperation(
                text(source.get("operationId"), ""),
                text(source.get("toolName"), ""),
                text(source.get("mcpId"), ""),
                ChangePackagePreparationOperation.normalizeEffectType(
                        text(source.get("effectType"), "UNKNOWN")),
                text(source.get("effectScope"), "UNKNOWN"),
                text(source.get("mutability"), "UNKNOWN"),
                text(source.get("riskLevel"), "HIGH"),
                text(source.get("resourceScope"), ""),
                Boolean.TRUE.equals(source.get("writesTargetResource")));
    }

    private boolean completeToolBindings(List<Map<String, Object>> toolBindings) {
        return !toolBindings.isEmpty()
                && toolBindings.stream().allMatch(binding ->
                Boolean.TRUE.equals(binding.get("schemaBound")));
    }

    private boolean repairIntent(Map<String, Object> request) {
        return !text(request.get("repairWorkspaceId"), "").isBlank()
                || !text(request.get("repairCommit"), "").isBlank()
                || request.containsKey("changedFiles");
    }

    private List<String> changedFiles(Map<String, Object> request) {
        Object value = firstNonNull(
                request.get("changedFiles"),
                request.get("changedFilesJson"));
        if (value instanceof Iterable<?> iterable) {
            List<String> files = new ArrayList<>();
            for (Object item : iterable) {
                String file = text(item, "");
                if (!file.isBlank()) files.add(file);
            }
            return List.copyOf(files);
        }
        String file = text(value, "");
        return file.isBlank() ? List.of() : List.of(file);
    }

    private List<String> notVerifiedItems(Map<String, Object> preflight,
                                          Map<String, Object> validationProof,
                                          boolean validationRequired) {
        List<String> items = new ArrayList<>();
        if (!trustedPassed(preflight)) items.add("preflight 未完整通过");
        if (validationRequired && !trustedPassed(validationProof)) {
            items.add("显式 validation operation 未执行或未通过");
        }
        return List.copyOf(items);
    }

    private boolean trustedPassed(Map<String, Object> result) {
        if (result == null || Boolean.TRUE.equals(result.get("untrusted"))) return false;
        String source = text(firstNonNull(
                result.get("source"), result.get("proofSource")), "");
        if ("UNTRUSTED_USER_INPUT".equalsIgnoreCase(source)) return false;
        String status = text(result.get("status"), "");
        return "PASSED".equalsIgnoreCase(status)
                || "SUCCEEDED".equalsIgnoreCase(status);
    }

    private Map<String, Object> untrustedUserInput(String type, Map<?, ?> map) {
        Map<String, Object> original = copy(map);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("status", "UNTRUSTED_USER_INPUT");
        data.put("trusted", false);
        data.put("untrusted", true);
        data.put("source", "UNTRUSTED_USER_INPUT");
        data.put("originalStatus", text(original.get("status"), ""));
        data.put("original", original);
        data.put("warnings", List.of(
                "用户请求中携带的验证结果只作为证据记录，不能作为 trusted proof 或 gate 通过依据。"));
        return data;
    }

    private Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> data = new LinkedHashMap<>();
        map.forEach((key, value) -> data.put(String.valueOf(key), value));
        return data;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record Input(Map<String, Object> request,
                 Map<String, Object> activePrepare,
                 List<Map<String, Object>> mcpSteps,
                 List<Map<String, Object>> toolBindings,
                 boolean trustedEvidencePresent) {
        Input {
            request = immutableMap(request);
            activePrepare = immutableMap(activePrepare);
            mcpSteps = mcpSteps == null ? List.of() : List.copyOf(mcpSteps);
            toolBindings = toolBindings == null ? List.of() : List.copyOf(toolBindings);
        }
    }

    record ProofBundle(Map<String, Object> preflight,
                       Map<String, Object> dryRun,
                       ChangePackagePreparationContext context,
                       List<String> notVerifiedItems) {
        ProofBundle {
            preflight = immutableMap(preflight);
            dryRun = immutableMap(dryRun);
            if (context == null) {
                throw new IllegalArgumentException("PREPARATION_CONTEXT_REQUIRED");
            }
            notVerifiedItems = notVerifiedItems == null
                    ? List.of()
                    : List.copyOf(notVerifiedItems);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
