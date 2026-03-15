package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectEmergencyStopAcceptancePort;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads the persisted, project-scoped emergency-stop restart-drill fact. */
public final class OpsProjectEmergencyStopAcceptanceAdapter
        implements ProjectEmergencyStopAcceptancePort {

    private final IConfigAuditRepository audits;

    public OpsProjectEmergencyStopAcceptanceAdapter(IConfigAuditRepository audits) {
        this.audits = audits;
    }

    @Override
    public EmergencyStopAcceptanceFact acceptance(String projectId) {
        if (audits == null) return EmergencyStopAcceptanceFact.notValidated();
        String id = text(projectId);
        if (id.isBlank()) return EmergencyStopAcceptanceFact.notValidated();
        try {
            ConfigAuditEntry activation = latest(audits.search(criteria(id,
                            "tool_execution_emergency_stop_activated")),
                    "tool_execution_emergency_stop_activated", true,
                    "production-like process restart drill");
            ConfigAuditEntry release = latest(audits.search(criteria(id,
                            "tool_execution_emergency_stop_released")),
                    "tool_execution_emergency_stop_released", false,
                    "production-like process restart drill complete");
            if (activation != null && release != null && ordered(activation, release)) {
                String activationId = activation.auditId().isBlank()
                        ? String.valueOf(activation.id()) : activation.auditId();
                String releaseId = release.auditId().isBlank()
                        ? String.valueOf(release.id()) : release.auditId();
                return new EmergencyStopAcceptanceFact(
                        true,
                        "已投影持久化 production-like 急停重启验收事实（activate="
                                + activationId + ", release=" + releaseId + ")");
            }
            return new EmergencyStopAcceptanceFact(
                    false,
                    "急停治理能力已配置，但尚无完整的 production-like 急停激活→重启恢复→释放成功事实");
        } catch (RuntimeException error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
            if (message.length() > 240) message = message.substring(0, 240);
            return new EmergencyStopAcceptanceFact(
                    false,
                    "急停环境验收事实读取失败，Readiness 保持 fail-closed：" + message);
        }
    }

    private ConfigAuditCriteria criteria(String projectId, String action) {
        return new ConfigAuditCriteria(
                projectId,
                "",
                "",
                "tool-execution",
                action,
                "",
                "",
                "",
                50);
    }

    private ConfigAuditEntry latest(
            List<ConfigAuditEntry> entries,
            String action,
            boolean active,
            String reason) {
        return entries == null ? null : entries.stream()
                .filter(entry -> action.equals(entry.actionName()))
                .filter(entry -> "SUCCESS".equalsIgnoreCase(entry.resultStatus()))
                .filter(entry -> containsJson(entry.afterJson(), "\"active\":" + active))
                .filter(entry -> containsJson(entry.afterJson(), reason))
                .max(Comparator.comparing(ConfigAuditEntry::id, Comparator.nullsFirst(Long::compareTo)))
                .orElse(null);
    }

    private boolean ordered(ConfigAuditEntry activation, ConfigAuditEntry release) {
        if (activation.id() != null && release.id() != null) return activation.id() < release.id();
        if (activation.createTime() != null && release.createTime() != null) {
            return !activation.createTime().isAfter(release.createTime());
        }
        return false;
    }

    private boolean containsJson(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(fragment.toLowerCase(Locale.ROOT));
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
