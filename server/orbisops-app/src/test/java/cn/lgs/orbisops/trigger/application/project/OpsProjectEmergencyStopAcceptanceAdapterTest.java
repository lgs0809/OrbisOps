package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectEmergencyStopAcceptancePort;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectEmergencyStopAcceptanceAdapterTest {

    @Test
    void acceptsOnlyOrderedDurableProductionLikeActivateAndReleaseFacts() {
        IConfigAuditRepository audits = mock(IConfigAuditRepository.class);
        when(audits.search(any(ConfigAuditCriteria.class))).thenAnswer(invocation -> {
            ConfigAuditCriteria criteria = invocation.getArgument(0);
            return criteria.actionName().contains("activated")
                    ? List.of(entry(46759L, "activate-46759", "tool_execution_emergency_stop_activated",
                    "{\"active\":true,\"reason\":\"production-like process restart drill\"}"))
                    : List.of(entry(46765L, "release-46765", "tool_execution_emergency_stop_released",
                    "{\"active\":false,\"reason\":\"production-like process restart drill complete\"}"));
        });

        ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact fact =
                new OpsProjectEmergencyStopAcceptanceAdapter(audits).acceptance("demo-project");

        assertTrue(fact.accepted());
        assertTrue(fact.detail().contains("activate-46759"));
        assertTrue(fact.detail().contains("release-46765"));
    }

    @Test
    void missingReleaseFailsClosed() {
        IConfigAuditRepository audits = mock(IConfigAuditRepository.class);
        when(audits.search(any(ConfigAuditCriteria.class))).thenAnswer(invocation -> {
            ConfigAuditCriteria criteria = invocation.getArgument(0);
            return criteria.actionName().contains("activated")
                    ? List.of(entry(46759L, "activate-46759", "tool_execution_emergency_stop_activated",
                    "{\"active\":true,\"reason\":\"production-like process restart drill\"}"))
                    : List.of();
        });

        ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact fact =
                new OpsProjectEmergencyStopAcceptanceAdapter(audits).acceptance("demo-project");

        assertFalse(fact.accepted());
        assertTrue(fact.detail().contains("尚无完整"));
    }

    private ConfigAuditEntry entry(Long id, String auditId, String action, String afterJson) {
        return new ConfigAuditEntry(
                id, auditId, "demo-project", "", "tool-execution", action, "", "", "HIGH", "SUCCESS",
                "operator", "operator", "ADMIN", "127.0.0.1", "trace", "{}", afterJson,
                LocalDateTime.of(2026, 8, 14, 10, 0).plusSeconds(id));
    }
}
