package cn.lgs.orbisops.trigger.application.audit;

import cn.lgs.orbisops.application.audit.ConfigAuditCommand;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsConfigAuditMapperTest {

    private final OpsConfigAuditMapper mapper = new OpsConfigAuditMapper();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void mapsAdminIdentityClientIpNestedScopeAndMasksPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.8, 10.0.0.9");
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "admin", false));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ConfigAuditCommand command = mapper.adminCommand(
                "", "skill", "update", "skill-1",
                Map.of("project_id", "project-old"),
                Map.of("definition", Map.of(
                        "projectId", "project-1",
                        "agent_id", "agent-1",
                        "api_key", "secret-value")));

        assertEquals("project-1", command.projectId());
        assertEquals("agent-1", command.agentId());
        assertEquals("u1", command.operatorId());
        assertEquals("alice", command.operatorName());
        assertEquals("admin", command.operatorRole());
        assertEquals("10.0.0.8", command.clientIp());
        assertFalse(command.afterJson().contains("secret-value"));
    }

    @Test
    void runtimeCommandPreservesLegacyUnknownDefaults() {
        ConfigAuditCommand command = mapper.runtimeCommand(
                "project-1", "agent-1", "u1", "runtime", "execute", "run-1", "", "", Map.of());

        assertEquals("LOW", command.riskLevel());
        assertEquals("UNKNOWN", command.resultStatus());
        assertEquals("agent-user", command.operatorRole());
    }

    @Test
    void adminCommandPreservesBusinessFailureOutcome() {
        ConfigAuditCommand landing = mapper.adminCommand(
                "project-1", "change-package", "landing-failed", "cp-1", null,
                Map.of("status", "LANDING_FAILED", "reasonCode", "UPSTREAM_UNAVAILABLE"));
        ConfigAuditCommand ordinaryReject = mapper.adminCommand(
                "project-1", "change-package", "reject", "cp-2", null,
                Map.of("status", "REJECTED"));

        assertEquals("FAILED", landing.resultStatus());
        assertEquals("SUCCESS", ordinaryReject.resultStatus());
    }

    @Test
    void mapsTypedEntryAndPolicyToLegacySnakeCaseContract() {
        ConfigAuditEntry entry = new ConfigAuditEntry(
                9L, "audit-1", "project-1", "agent-1", "skill", "update", "skill", "skill-1",
                "MEDIUM", "SUCCESS", "u1", "alice", "admin", "127.0.0.1", "trace-1",
                null, "{\"token\":\"plain\"}", LocalDateTime.of(2026, 7, 23, 18, 0));

        Map<String, Object> row = mapper.view(entry);
        Map<String, Object> policy = mapper.view(new ConfigAuditPolicySnapshot(
                new AuditPolicy("project-1", 90, true, false, true, true, AuditPolicyStatus.ENABLED),
                true, "now"));

        assertEquals("audit-1", row.get("audit_id"));
        assertEquals("project-1", row.get("project_id"));
        assertEquals("******", String.valueOf(row.get("after_json")).replace("{\"token\":\"", "").replace("\"}", ""));
        assertEquals(90, policy.get("retention_days"));
        assertEquals(1, policy.get("masking_enabled"));
        assertEquals(0, policy.get("export_approval_required"));
        assertEquals(true, policy.get("persistence"));
    }

    @Test
    void policyMapperClampsRetentionAndNormalizesUnknownStatus() {
        AuditPolicy policy = mapper.policy(Map.of(
                "projectId", "project-1",
                "retentionDays", 99999,
                "status", "unexpected"));

        assertEquals(3650, policy.retentionDays());
        assertEquals(AuditPolicyStatus.ENABLED, policy.status());
    }
}
