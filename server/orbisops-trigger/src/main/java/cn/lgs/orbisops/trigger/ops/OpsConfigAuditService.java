package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.audit.ConfigAuditApplicationService;
import cn.lgs.orbisops.trigger.application.audit.OpsConfigAuditMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config Audit compatibility ACL.
 *
 * Existing trigger callers keep their stable facade while typed Application and
 * Infrastructure own audit policy, persistence, query and readiness behavior.
 */
@Service
public class OpsConfigAuditService {

    private final ConfigAuditApplicationService audits;
    private final OpsConfigAuditMapper mapper;

    public OpsConfigAuditService(
            ConfigAuditApplicationService audits,
            OpsConfigAuditMapper mapper) {
        this.audits = audits;
        this.mapper = mapper;
    }

    public void record(String module, String action, String targetId, Object before, Object after) {
        record("", module, action, targetId, before, after);
    }

    public void record(
            String projectId,
            String module,
            String action,
            String targetId,
            Object before,
            Object after) {
        if (!StringUtils.hasText(module) || !StringUtils.hasText(action)) return;
        try {
            audits.record(mapper.adminCommand(projectId, module, action, targetId, before, after));
        } catch (Exception e) {
            throw new IllegalStateException("记录配置审计失败，安全主链路必须 fail closed module="
                    + module + " action=" + action + " targetId=" + targetId
                    + " reason=" + e.getMessage(), e);
        }
    }

    public void recordIdempotent(
            String projectId,
            String module,
            String action,
            String targetId,
            Object before,
            Object after,
            String deliveryKey) {
        if (!StringUtils.hasText(module) || !StringUtils.hasText(action)) return;
        try {
            audits.recordIdempotent(
                    mapper.adminCommand(projectId, module, action, targetId, before, after),
                    text(deliveryKey));
        } catch (Exception e) {
            throw new IllegalStateException("记录配置审计失败，安全主链路必须 fail closed module="
                    + module + " action=" + action + " targetId=" + targetId
                    + " reason=" + e.getMessage(), e);
        }
    }

    public void recordRuntimeEvent(
            String projectId,
            String agentId,
            String userId,
            String module,
            String action,
            String targetId,
            String riskLevel,
            String resultStatus,
            Object payload) {
        if (!StringUtils.hasText(module) || !StringUtils.hasText(action)) return;
        try {
            audits.record(mapper.runtimeCommand(
                    projectId, agentId, userId, module, action, targetId,
                    riskLevel, resultStatus, payload));
        } catch (Exception e) {
            throw new IllegalStateException("记录运行审计失败，安全主链路必须 fail closed module="
                    + module + " action=" + action + " targetId=" + targetId
                    + " reason=" + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> list(String projectId, String module, int limit) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("查询审计记录必须提供 projectId");
        }
        return search(new AuditQuery(projectId, null, null, module, null, null, null, null, limit));
    }

    public List<Map<String, Object>> search(AuditQuery query) {
        AuditQuery safe = query == null ? AuditQuery.empty() : query;
        return mapper.views(audits.search(toApplicationQuery(safe)));
    }

    public Map<String, Object> detail(String auditId) {
        if (!StringUtils.hasText(auditId)) return Map.of();
        return audits.detail(auditId).map(mapper::view).orElse(Map.of());
    }

    public Map<String, Object> exportOne(String auditId) {
        Map<String, Object> record = detail(auditId);
        if (record.isEmpty()) return Map.of();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", LocalDateTime.now());
        export.put("record", record);
        record(
                text(record.get("project_id")),
                "audit-export",
                "export",
                text(record.getOrDefault("audit_id", auditId)),
                null,
                Map.of(
                        "auditId", text(record.getOrDefault("audit_id", auditId)),
                        "moduleName", text(record.get("module_name")),
                        "targetId", text(record.get("target_id"))));
        return export;
    }

    public Map<String, Object> exportSearch(AuditQuery query) {
        AuditQuery safe = query == null ? AuditQuery.empty() : query;
        List<Map<String, Object>> records = search(safe);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("projectId", text(safe.projectId()));
        filters.put("userId", text(safe.userId()));
        filters.put("agentId", text(safe.agentId()));
        filters.put("moduleName", text(safe.moduleName()));
        filters.put("actionName", text(safe.actionName()));
        filters.put("riskLevel", text(safe.riskLevel()));
        filters.put("startTime", text(safe.startTime()));
        filters.put("endTime", text(safe.endTime()));
        filters.put("limit", safe.limit());

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", LocalDateTime.now());
        export.put("masked", true);
        export.put("filters", filters);
        export.put("recordCount", records.size());
        export.put("records", records);
        record(
                text(safe.projectId()),
                "audit-export",
                "export-list",
                StringUtils.hasText(safe.projectId()) ? safe.projectId() : "__GLOBAL__",
                null,
                Map.of("recordCount", records.size(), "filters", filters));
        return export;
    }

    public Map<String, Object> getPolicy(String projectId) {
        return mapper.view(audits.policy(projectId));
    }

    public Map<String, Object> updatePolicy(Map<String, Object> request) {
        String projectId = text(request == null ? null : request.get("projectId"));
        Map<String, Object> before = getPolicy(projectId);
        Map<String, Object> after = mapper.view(audits.updatePolicy(mapper.policy(request)));
        record(after.get("project_id") == null ? projectId : text(after.get("project_id")),
                "audit-policy", "update",
                after.get("project_id") == null ? "GLOBAL" : text(after.get("project_id")),
                before, after);
        return after;
    }

    public List<Map<String, Object>> listForOperator(String operator, int limit) {
        if (!StringUtils.hasText(operator)) return List.of();
        return mapper.views(audits.listForOperator(operator, limit));
    }

    public Map<String, Object> readiness() {
        return mapper.view(audits.readiness());
    }

    private cn.lgs.orbisops.application.audit.AuditQuery toApplicationQuery(AuditQuery query) {
        return new cn.lgs.orbisops.application.audit.AuditQuery(
                query.projectId(), query.userId(), query.agentId(), query.moduleName(), query.actionName(),
                query.riskLevel(), query.startTime(), query.endTime(),
                Math.max(1, Math.min(query.limit(), 500)));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record AuditQuery(
            String projectId,
            String userId,
            String agentId,
            String moduleName,
            String actionName,
            String riskLevel,
            String startTime,
            String endTime,
            int limit) {

        public static AuditQuery empty() {
            return new AuditQuery(null, null, null, null, null, null, null, null, 100);
        }
    }
}
