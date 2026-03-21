package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Resolves and binds the authoritative Runtime Context Bundle for PREPARE. */
final class OpsPreparationContextBundleService {

    private final Supplier<OpsRuntimeContextBundleAdapter> bundleServiceSupplier;

    OpsPreparationContextBundleService(
            Supplier<OpsRuntimeContextBundleAdapter> bundleServiceSupplier) {
        this.bundleServiceSupplier = bundleServiceSupplier;
    }

    Map<String, Object> bindLatestForSession(String sessionId,
                                             Map<String, Object> request,
                                             String actor) {
        Map<String, Object> safe = new LinkedHashMap<>(request == null ? Map.of() : request);
        safe.put("sessionId", requireText(
                sessionId,
                "创建 ChangePackage 必须提供 sessionId"));
        String projectId = requireText(
                safe.get("projectId"),
                "按会话生成 ChangePackage 必须提供 projectId");
        OpsRuntimeContextBundleAdapter service = requireService(
                "Runtime Context Bundle Service 未初始化，不能按会话生成 ChangePackage");
        Map<String, Object> authoritative = service.latestCompletedBundleForSession(
                sessionId,
                projectId,
                requireText(actor, "按会话生成 ChangePackage 必须提供 actor"));
        safe.put("contextBundleId", requireText(
                authoritative.get("contextBundleId"),
                "后端 Runtime Context Bundle 缺少 contextBundleId"));
        safe.put("contextBundleHash", requireText(
                authoritative.get("contextBundleHash"),
                "后端 Runtime Context Bundle 缺少 contextBundleHash"));
        safe.put("runId", requireText(
                authoritative.get("runId"),
                "后端 Runtime Context Bundle 缺少 canonical runId"));
        safe.put("sourceRunId", safe.get("runId"));
        return safe;
    }

    Map<String, Object> validateRevision(Map<String, Object> request) {
        Map<String, Object> safe = new LinkedHashMap<>(request == null ? Map.of() : request);
        Map<String, Object> bundle = requireForRequest(safe);
        applyAuthoritative(safe, bundle);
        return safe;
    }

    Map<String, Object> requireForRequest(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String contextBundleId = requireText(
                safe.get("contextBundleId"),
                "CONTEXT_BUNDLE_REQUIRED：Prepare 必须提供 contextBundleId");
        String contextBundleHash = requireText(
                safe.get("contextBundleHash"),
                "CONTEXT_BUNDLE_HASH_REQUIRED：Prepare 必须提供 contextBundleHash");
        OpsRuntimeContextBundleAdapter service = requireService(
                "Runtime Context Bundle Service 未初始化，Prepare 禁止信任 request 传入的上下文 hash");
        Map<String, Object> bundle = service.requireBundle(
                contextBundleId,
                contextBundleHash);
        verifyScope(safe, bundle);
        return bundle;
    }

    void applyAuthoritative(Map<String, Object> target,
                            Map<String, Object> bundle) {
        target.put("contextBundleId", bundle.get("contextBundleId"));
        target.put("contextBundleHash", bundle.get("contextBundleHash"));
        target.put("memoryContextRefs", bundle.getOrDefault("memoryContextRefs", List.of()));
        target.put("memoryContextHash", text(bundle.get("memoryContextHash"), ""));
        target.put("usedSkillVersionRefs", bundle.getOrDefault("usedSkillVersionRefs", List.of()));
        target.put("usedSkillRefsHash", text(bundle.get("usedSkillRefsHash"), ""));
        target.put("toolsetRefs", bundle.getOrDefault("toolsetRefs", List.of()));
        target.put("policyRefs", bundle.getOrDefault("policyRefs", List.of()));
        target.put("policyHash", text(bundle.get("policyHash"), ""));
        target.put("toolsetBoundaryHash", text(bundle.get("toolsetBoundaryHash"), ""));
        target.put("runtimeBoundaryHash", text(bundle.get("runtimeBoundaryHash"), ""));
        target.put("contextApprovalBoundaryHash", text(bundle.get("approvalBoundaryHash"), ""));
        target.put("runId", text(bundle.get("runId"), ""));
        target.put("agentId", text(bundle.get("agentId"), ""));
        int agentVersion = positiveInt(bundle.get("agentVersion"));
        if (agentVersion <= 0) {
            throw new IllegalStateException(
                    "CONTEXT_BUNDLE_AGENT_VERSION_REQUIRED：Runtime Context Bundle 缺少冻结的 Agent 版本");
        }
        target.put("agentVersion", agentVersion);
        target.put("agentSnapshot", bundle.getOrDefault("agentSnapshot", Map.of()));
        target.put("agentRunExecutionContext", bundle.getOrDefault("agentRunExecutionContext", Map.of()));
    }

    List<Object> skillRefValues(Map<String, Object> contextBundle,
                                String key) {
        Object refs = contextBundle == null
                ? null
                : contextBundle.get("usedSkillVersionRefs");
        if (!(refs instanceof Iterable<?> iterable)) return List.of();
        List<Object> values = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map && map.containsKey(key)) {
                values.add(map.get(key));
            }
        }
        return List.copyOf(values);
    }

    private void verifyScope(Map<String, Object> request,
                             Map<String, Object> bundle) {
        String requestedProject = requireText(
                request.get("projectId"),
                "PREPARE 必须提供 projectId");
        if (!requestedProject.equals(text(bundle.get("projectId"), ""))) {
            throw new SecurityException(
                    "CONTEXT_BUNDLE_PROJECT_MISMATCH：不能跨项目复用 Runtime Context Bundle");
        }
        String requestedRun = text(firstNonNull(
                request.get("runId"), request.get("sourceRunId")), "");
        if (requestedRun.isBlank()
                || !requestedRun.equals(text(bundle.get("runId"), ""))) {
            throw new SecurityException(
                    "CONTEXT_BUNDLE_RUN_MISMATCH：ChangePackage 必须绑定当前 Work Session 的 canonical runId");
        }
        String requestedSession = text(request.get("sessionId"), "");
        if (!requestedSession.isBlank()
                && !requestedSession.equals(text(bundle.get("sessionId"), ""))) {
            throw new SecurityException(
                    "CONTEXT_BUNDLE_SESSION_MISMATCH：不能跨会话复用 Runtime Context Bundle");
        }
    }

    private OpsRuntimeContextBundleAdapter requireService(String message) {
        OpsRuntimeContextBundleAdapter service = bundleServiceSupplier == null
                ? null
                : bundleServiceSupplier.get();
        if (service == null) throw new IllegalStateException(message);
        return service;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String requireText(Object value, String message) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private int positiveInt(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
