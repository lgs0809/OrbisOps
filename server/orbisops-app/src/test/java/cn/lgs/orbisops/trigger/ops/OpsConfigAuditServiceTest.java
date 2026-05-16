package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.audit.AuditQuery;
import cn.lgs.orbisops.application.audit.ConfigAuditApplicationService;
import cn.lgs.orbisops.application.audit.ConfigAuditCommand;
import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import cn.lgs.orbisops.trigger.application.audit.OpsConfigAuditMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsConfigAuditServiceTest {

    @Test
    void recordDelegatesTypedCommandAndExtractsProjectAgent() {
        ConfigAuditApplicationService application = mock(ConfigAuditApplicationService.class);
        when(application.record(any(ConfigAuditCommand.class))).thenReturn(entry("audit-1"));
        OpsConfigAuditService service = new OpsConfigAuditService(application, new OpsConfigAuditMapper());

        service.record("skill", "update", "skill-1", null,
                Map.of("scope", Map.of("projectId", "project-1", "agentId", "agent-1")));

        ArgumentCaptor<ConfigAuditCommand> command = ArgumentCaptor.forClass(ConfigAuditCommand.class);
        verify(application).record(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("agent-1", command.getValue().agentId());
        assertEquals("skill", command.getValue().moduleName());
        assertEquals("skill-1", command.getValue().targetId());
    }

    @Test
    void recordFailureRemainsFailClosedWithCompatibilityContext() {
        ConfigAuditApplicationService application = mock(ConfigAuditApplicationService.class);
        when(application.record(any(ConfigAuditCommand.class))).thenThrow(new IllegalStateException("store down"));
        OpsConfigAuditService service = new OpsConfigAuditService(application, new OpsConfigAuditMapper());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.record("project-1", "tool-execution", "blocked", "code/code_bash",
                        null, Map.of("status", "BLOCKED")));

        assertTrue(error.getMessage().contains("安全主链路必须 fail closed"));
        assertTrue(error.getMessage().contains("tool-execution"));
        assertTrue(error.getMessage().contains("store down"));
    }

    @Test
    void searchPreservesCompatibilityLimitClampAndSnakeCaseRows() {
        ConfigAuditApplicationService application = mock(ConfigAuditApplicationService.class);
        when(application.search(any(AuditQuery.class))).thenReturn(List.of(entry("audit-1")));
        OpsConfigAuditService service = new OpsConfigAuditService(application, new OpsConfigAuditMapper());

        List<Map<String, Object>> rows = service.search(new OpsConfigAuditService.AuditQuery(
                "project-1", null, null, "skill", null, null, null, null, 5000));

        ArgumentCaptor<AuditQuery> query = ArgumentCaptor.forClass(AuditQuery.class);
        verify(application).search(query.capture());
        assertEquals(500, query.getValue().limit());
        assertEquals("audit-1", rows.get(0).get("audit_id"));
        assertEquals("project-1", rows.get(0).get("project_id"));
    }

    @Test
    void exportOneReturnsRecordAndEmitsExportAudit() {
        ConfigAuditApplicationService application = mock(ConfigAuditApplicationService.class);
        when(application.detail("audit-1")).thenReturn(Optional.of(entry("audit-1")));
        when(application.record(any(ConfigAuditCommand.class))).thenReturn(entry("audit-export"));
        OpsConfigAuditService service = new OpsConfigAuditService(application, new OpsConfigAuditMapper());

        Map<String, Object> export = service.exportOne("audit-1");

        assertTrue(export.containsKey("exportedAt"));
        assertEquals("audit-1", ((Map<?, ?>) export.get("record")).get("audit_id"));
        ArgumentCaptor<ConfigAuditCommand> command = ArgumentCaptor.forClass(ConfigAuditCommand.class);
        verify(application).record(command.capture());
        assertEquals("audit-export", command.getValue().moduleName());
        assertEquals("export", command.getValue().actionName());
    }

    @Test
    void updatePolicyWritesTypedPolicyAndAuditsBeforeAfter() {
        ConfigAuditApplicationService application = mock(ConfigAuditApplicationService.class);
        ConfigAuditPolicySnapshot before = policy("project-1", 180);
        ConfigAuditPolicySnapshot after = policy("project-1", 90);
        when(application.policy("project-1")).thenReturn(before);
        when(application.updatePolicy(any(AuditPolicy.class))).thenReturn(after);
        when(application.record(any(ConfigAuditCommand.class))).thenReturn(entry("audit-policy"));
        OpsConfigAuditService service = new OpsConfigAuditService(application, new OpsConfigAuditMapper());

        Map<String, Object> result = service.updatePolicy(Map.of(
                "projectId", "project-1",
                "retentionDays", 90,
                "status", "ENABLED"));

        assertEquals(90, result.get("retention_days"));
        verify(application).policy("project-1");
        ArgumentCaptor<AuditPolicy> policy = ArgumentCaptor.forClass(AuditPolicy.class);
        verify(application).updatePolicy(policy.capture());
        assertEquals(90, policy.getValue().retentionDays());
        verify(application, times(1)).record(any(ConfigAuditCommand.class));
    }

    private ConfigAuditEntry entry(String auditId) {
        return new ConfigAuditEntry(
                1L, auditId, "project-1", "agent-1", "skill", "update", "skill", "skill-1",
                "MEDIUM", "SUCCESS", "u1", "alice", "admin", "127.0.0.1", "trace-1",
                null, "{\"status\":\"ok\"}", LocalDateTime.of(2026, 7, 23, 18, 0));
    }

    private ConfigAuditPolicySnapshot policy(String projectId, int retentionDays) {
        return new ConfigAuditPolicySnapshot(
                new AuditPolicy(projectId, retentionDays, true, true, true, true, AuditPolicyStatus.ENABLED),
                true, "now");
    }
}
