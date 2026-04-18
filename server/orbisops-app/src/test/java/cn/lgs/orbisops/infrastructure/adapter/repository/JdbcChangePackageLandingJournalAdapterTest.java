package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChangePackageLandingJournalAdapterTest {

    @Test
    void rebindsOnlyProvenUnexecutedOperationToTheFreshLandingAttempt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "landing_run_id", "lr-old",
                "package_id", "cp-1",
                "project_id", "project-1",
                "approved_version", 1,
                "approved_package_hash", "package-hash",
                "operation_id", "restart-service",
                "operation_hash", "operation-hash",
                "fact_status", "NONE",
                "status", "PENDING")));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        ChangePackageLandingOperation operation = new ChangePackageLandingOperation(
                "restart-service", "operation-hash", "SERVICE_CONTROL", "mcp.service-control",
                "restart_service", "service://orbisops-acceptance", "MUTATE_TEMP_RESOURCE",
                Map.of("operationId", "restart-service"));
        ChangePackageLandingPlan plan = new ChangePackageLandingPlan(
                "cp-1", "project-1", 1, "package-hash", Map.of("target", "acceptance"),
                List.of(operation));

        new JdbcChangePackageLandingJournalAdapter(provider).initialize("lr-new", plan);

        verify(jdbc).update(argThat(sql -> sql.contains("SET operation_run_id=?")
                        && sql.contains("landing_run_id=?")
                        && sql.contains("fact_status='NONE'")
                        && sql.contains("status='PENDING'")
                        && sql.contains("operation_hash=?")),
                any(Object[].class));
    }
}
