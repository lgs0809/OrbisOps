package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageCleanupPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ChangePackageCleanupUseCase {

    private static final ChangePackageCleanupPolicy CLEANUP_POLICY = new ChangePackageCleanupPolicy();

    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageEventRepository eventRepository;
    private final ChangePackageCleanupPort cleanupPort;
    private final ChangePackageAuditPort auditPort;
    private final ChangePackageTransactionPort transactionPort;

    public ChangePackageCleanupUseCase(IChangePackageCurrentRepository currentRepository,
                                       IChangePackageEventRepository eventRepository,
                                       ChangePackageCleanupPort cleanupPort,
                                       ChangePackageAuditPort auditPort,
                                       ChangePackageTransactionPort transactionPort) {
        this.currentRepository = required(currentRepository, "CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.eventRepository = required(eventRepository, "CHANGE_PACKAGE_EVENT_REPOSITORY_REQUIRED");
        this.cleanupPort = required(cleanupPort, "CHANGE_PACKAGE_CLEANUP_PORT_REQUIRED");
        this.auditPort = required(auditPort, "CHANGE_PACKAGE_AUDIT_PORT_REQUIRED");
        this.transactionPort = required(transactionPort, "CHANGE_PACKAGE_TRANSACTION_PORT_REQUIRED");
    }

    public Map<String, Object> cleanup(ChangePackageCommands.Cleanup command) {
        ChangePackageCommands.Cleanup requiredCommand = required(command, "CHANGE_PACKAGE_CLEANUP_COMMAND_REQUIRED");
        return transactionPort.required(() -> cleanupInTransaction(requiredCommand));
    }

    private Map<String, Object> cleanupInTransaction(ChangePackageCommands.Cleanup command) {
        ChangePackageCurrent current = current(command.packageId());
        CLEANUP_POLICY.requireCleanable(current);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packageId", current.packageId());
        payload.put("branchName", text(current.state().nullable(ChangePackageCurrentField.BRANCH_NAME)));
        String boundWorkspaceId = CLEANUP_POLICY.boundWorkspaceId(current);
        String requestedWorkspaceId = command.request().repairWorkspaceId();
        if (!requestedWorkspaceId.isBlank()
                && !boundWorkspaceId.isBlank()
                && !requestedWorkspaceId.equals(boundWorkspaceId)) {
            Map<String, Object> rejected = Map.of(
                    "requestedWorkspaceId", requestedWorkspaceId,
                    "boundWorkspaceId", boundWorkspaceId);
            appendEvent(current.packageId(), "CLEANUP_REJECTED", command.actor(),
                    "请求清理的 workspace 与 ChangePackage 绑定不一致", rejected);
            auditPort.record(current.projectId(), "cleanup-rejected", current.packageId(), current, rejected);
            throw new SecurityException(
                    "CLEANUP_REJECTED：不能清理未绑定到当前 ChangePackage 的 repairWorkspace");
        }
        String workspaceId = requestedWorkspaceId.isBlank() ? boundWorkspaceId : requestedWorkspaceId;
        if (workspaceId.isBlank()) {
            payload.put("status", "NO_TEMP_RESOURCE");
            payload.put("summary", "当前记录未绑定可由平台删除的真实 local worktree/远程 repair branch，未执行清理动作。");
            appendEvent(current.packageId(), "CLEANUP_NO_TEMP_RESOURCE", command.actor(),
                    "没有可自动清理的真实临时资源", payload);
            auditPort.record(current.projectId(), "cleanup", current.packageId(), current, payload);
            return payload;
        }
        try {
            ChangePackageRepairCleanupOutcome cleanup = cleanupPort.cleanupRepairWorkspace(
                    current,
                    workspaceId,
                    command.actor());
            payload.putAll(cleanup.payload());
            payload.put("status", cleanup.status());
            payload.put("summary", "已按 ChangePackage 清理绑定的 repair worktree。");
            appendEvent(current.packageId(), "CLEANUP_REPAIR_WORKSPACE", command.actor(),
                    "已清理 repair worktree：" + workspaceId, payload);
            auditPort.record(current.projectId(), "cleanup", current.packageId(), current, payload);
            return payload;
        } catch (RuntimeException error) {
            payload.put("status", "FAILED");
            payload.put("error", text(error.getMessage()));
            appendEvent(current.packageId(), "CLEANUP_FAILED", command.actor(),
                    "清理 repair worktree 失败：" + text(error.getMessage()), payload);
            auditPort.record(current.projectId(), "cleanup-failed", current.packageId(), current, payload);
            throw new IllegalStateException("ChangePackage 清理失败：" + text(error.getMessage()), error);
        }
    }

    private void appendEvent(String packageId,
                             String type,
                             String actor,
                             String summary,
                             Map<String, Object> payload) {
        events().append(new ChangePackageEvent(
                0L, "cpe-" + UUID.randomUUID(), packageId, type,
                text(actor), summary, payload, null));
    }

    private ChangePackageCurrent current(String packageId) {
        IChangePackageCurrentRepository repository = currents();
        return repository.find(packageId)
                .orElseThrow(() -> new IllegalArgumentException("ChangePackage 不存在：" + packageId));
    }

    private IChangePackageCurrentRepository currents() {
        if (!currentRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
        return currentRepository;
    }

    private IChangePackageEventRepository events() {
        if (!eventRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_EVENT_STORE_UNAVAILABLE");
        return eventRepository;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }
}
