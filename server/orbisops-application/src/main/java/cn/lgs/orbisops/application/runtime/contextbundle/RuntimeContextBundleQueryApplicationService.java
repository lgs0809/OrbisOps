package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.adapter.repository.IRuntimeContextBundleRepository;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;

public final class RuntimeContextBundleQueryApplicationService {

    private final IRuntimeContextBundleRepository repository;

    public RuntimeContextBundleQueryApplicationService(IRuntimeContextBundleRepository repository) {
        if (repository == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_REPOSITORY_REQUIRED");
        this.repository = repository;
    }

    public RuntimeContextBundleSnapshot require(String bundleId, String submittedHash) {
        String id = required(bundleId, "Prepare 必须提供后端生成的 contextBundleId");
        String hash = required(submittedHash, "Prepare 必须提供后端生成的 contextBundleHash");
        RuntimeContextBundleSnapshot snapshot = repository.find(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Runtime Context Bundle 不存在或未持久化：" + id));
        if (!hash.equals(snapshot.bundleHash())) {
            throw new SecurityException("Runtime Context Bundle hash 不匹配，拒绝使用 request 伪造上下文");
        }
        return snapshot;
    }

    public RuntimeContextBundleSnapshot latestForSession(String sessionId, String projectId) {
        String session = required(sessionId, "按会话生成 ChangePackage 必须绑定已持久化 Runtime Context Bundle");
        String project = required(projectId, "按会话生成 ChangePackage 必须提供 projectId");
        return repository.latestForSession(session, project)
                .orElseThrow(() -> new IllegalStateException(
                        "会话尚无后端持久化 Runtime Context Bundle，不能生成 ChangePackage：" + session));
    }

    public RuntimeContextBundleSnapshot latestCompletedForSession(
            String sessionId,
            String projectId,
            String actor) {
        String session = required(sessionId, "按会话生成 ChangePackage 必须绑定已持久化 Runtime Context Bundle");
        String project = required(projectId, "按会话生成 ChangePackage 必须提供 projectId");
        String owner = required(actor, "按会话生成 ChangePackage 必须绑定已认证发起人");
        return repository.latestCompletedForSession(session, project, owner)
                .orElseThrow(() -> new IllegalStateException(
                        "当前会话没有已成功完成且属于当前用户的 Work Session，不能生成 ChangePackage：" + session));
    }

    private String required(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }
}
