package cn.lgs.orbisops.trigger.application.audit;

import cn.lgs.orbisops.application.audit.ConfigAuditCommand;
import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditReadiness;
import cn.lgs.orbisops.domain.audit.service.AuditPolicyFactory;
import cn.lgs.orbisops.domain.audit.service.ConfigAuditMaskingPolicy;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.common.TraceContext;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OpsConfigAuditMapper {

    private final AuditPolicyFactory policyFactory = new AuditPolicyFactory();
    private final ConfigAuditMaskingPolicy maskingPolicy = new ConfigAuditMaskingPolicy();

    public ConfigAuditCommand adminCommand(
            String explicitProjectId,
            String module,
            String action,
            String targetId,
            Object before,
            Object after) {
        AdminAuthService.AuthPrincipal principal = currentPrincipal();
        HttpServletRequest request = currentRequest();
        String projectId = value(explicitProjectId);
        if (projectId.isBlank()) projectId = resolveTextField("projectId", after, before);
        return new ConfigAuditCommand(
                projectId,
                resolveTextField("agentId", after, before),
                module,
                action,
                module,
                value(targetId),
                resolveTextField("riskLevel", after, before),
                adminResultStatus(action, after),
                principal == null ? "" : value(principal.userId()),
                principal == null ? "" : value(principal.username()),
                principal == null ? "" : value(principal.scope()),
                clientIp(request),
                value(TraceContext.currentTraceId()),
                safeJson(before),
                safeJson(after));
    }

    /**
     * Admin mutations historically defaulted every audit row to SUCCESS. That
     * conflated "the audit write completed" with "the business operation
     * succeeded", which made actions such as landing-failed appear successful
     * in the governance UI. Failure outcomes are part of the command boundary:
     * infer them from the explicit failure action family, while retaining
     * SUCCESS for ordinary state changes (including a successful reject).
     */
    private String adminResultStatus(String action, Object after) {
        String normalizedAction = value(action).toLowerCase(Locale.ROOT);
        String normalizedPayloadStatus = value(resolveTextField("status", after))
                .toUpperCase(Locale.ROOT);
        boolean failureAction = normalizedAction.matches(".*(?:failed|failure|error|blocked|denied|timeout)$");
        boolean failurePayload = normalizedPayloadStatus.contains("FAIL")
                || normalizedPayloadStatus.contains("ERROR")
                || normalizedPayloadStatus.contains("BLOCK")
                || normalizedPayloadStatus.contains("DENIED");
        if (failureAction || failurePayload) {
            return normalizedPayloadStatus.contains("BLOCK")
                    || normalizedPayloadStatus.contains("DENIED")
                    || normalizedAction.endsWith("blocked")
                    || normalizedAction.endsWith("denied")
                    ? "BLOCKED"
                    : "FAILED";
        }
        return "SUCCESS";
    }

    public ConfigAuditCommand runtimeCommand(
            String projectId,
            String agentId,
            String userId,
            String module,
            String action,
            String targetId,
            String riskLevel,
            String resultStatus,
            Object payload) {
        return new ConfigAuditCommand(
                projectId,
                agentId,
                module,
                action,
                module,
                targetId,
                value(riskLevel).isBlank() ? "LOW" : riskLevel,
                value(resultStatus).isBlank() ? "UNKNOWN" : resultStatus,
                userId,
                userId,
                "agent-user",
                "",
                value(TraceContext.currentTraceId()),
                null,
                safeJson(payload));
    }

    public AuditPolicy policy(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        Map<String, Object> normalized = new LinkedHashMap<>(safe);
        String projectId = value(normalized.get("projectId"));
        if (projectId.isBlank()) normalized.put("projectId", "GLOBAL");
        return policyFactory.create(normalized);
    }

    public List<Map<String, Object>> views(List<ConfigAuditEntry> entries) {
        return entries == null ? List.of() : entries.stream().map(this::view).toList();
    }

    public Map<String, Object> view(ConfigAuditEntry entry) {
        if (entry == null) return Map.of();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entry.id());
        row.put("audit_id", entry.auditId());
        row.put("project_id", entry.projectId());
        row.put("agent_id", entry.agentId());
        row.put("module_name", entry.moduleName());
        row.put("action_name", entry.actionName());
        row.put("target_type", entry.targetType());
        row.put("target_id", entry.targetId());
        row.put("risk_level", entry.riskLevel());
        row.put("result_status", entry.resultStatus());
        row.put("operator_id", entry.operatorId());
        row.put("operator_name", entry.operatorName());
        row.put("operator_role", entry.operatorRole());
        row.put("client_ip", entry.clientIp());
        row.put("trace_id", entry.traceId());
        row.put("before_json", maskingPolicy.mask(entry.beforeJson()));
        row.put("after_json", maskingPolicy.mask(entry.afterJson()));
        row.put("create_time", entry.createTime());
        return row;
    }

    public Map<String, Object> view(ConfigAuditPolicySnapshot snapshot) {
        if (snapshot == null) return Map.of();
        AuditPolicy policy = snapshot.policy();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("project_id", policy.projectId());
        row.put("retention_days", policy.retentionDays());
        row.put("masking_enabled", policy.maskingEnabled() ? 1 : 0);
        row.put("export_approval_required", policy.exportApprovalRequired() ? 1 : 0);
        row.put("high_risk_confirmation_required", policy.highRiskConfirmationRequired() ? 1 : 0);
        row.put("replay_enabled", policy.replayEnabled() ? 1 : 0);
        row.put("status", policy.status().name());
        row.put("persistence", snapshot.persistent());
        if (!snapshot.updateTime().isBlank()) row.put("update_time", snapshot.updateTime());
        return row;
    }

    public Map<String, Object> view(ConfigAuditReadiness readiness) {
        if (readiness == null) return Map.of();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("store", readiness.store());
        row.put("autoInit", readiness.autoInit());
        row.put("memoryFallbackAllowed", readiness.memoryFallbackAllowed());
        row.put("status", readiness.status());
        if (!readiness.reason().isBlank()) row.put("reason", readiness.reason());
        return row;
    }

    private String resolveTextField(String field, Object... candidates) {
        for (Object candidate : candidates) {
            String found = findTextField(JSON.toJSON(candidate), field, 0);
            if (StringUtils.hasText(found)) return found.trim();
        }
        return "";
    }

    private String findTextField(Object candidate, String field, int depth) {
        if (candidate == null || depth > 5) return null;
        if (candidate instanceof Map<?, ?> map) {
            Object direct = map.get(field);
            if (direct == null) direct = map.get(toSnakeCase(field));
            if (direct != null && StringUtils.hasText(String.valueOf(direct))) {
                return String.valueOf(direct);
            }
            for (Object nested : map.values()) {
                String found = findTextField(nested, field, depth + 1);
                if (StringUtils.hasText(found)) return found;
            }
        } else if (candidate instanceof Collection<?> collection) {
            for (Object nested : collection) {
                String found = findTextField(nested, field, depth + 1);
                if (StringUtils.hasText(found)) return found;
            }
        } else if (candidate instanceof String text && text.trim().startsWith("{")) {
            try {
                return findTextField(JSON.parse(text), field, depth + 1);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String safeJson(Object payload) {
        if (payload == null) return null;
        String json = payload instanceof String string ? string : JSON.toJSONString(payload);
        return maskingPolicy.mask(json);
    }

    private AdminAuthService.AuthPrincipal currentPrincipal() {
        HttpServletRequest request = currentRequest();
        Object principal = request == null ? null
                : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        return principal instanceof AdminAuthService.AuthPrincipal value ? value : null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return "";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) return forwarded.split(",")[0].trim();
        return value(request.getRemoteAddr());
    }

    private String toSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }
}
