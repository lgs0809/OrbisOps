package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcRuntimeCompatibilityMigrationContributorTest {

    @Test
    void mcpScanMustCountInvalidProjectDescriptorsAndUnmappedLegacyRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = provider(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("information_schema.columns")) {
                        Object table = invocation.getArguments()[2];
                        return "ai_ops_project_mcp".equals(table) ? 10L : 5L;
                    }
                    return 1L;
                });
        AtomicInteger counts = new AtomicInteger();
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> switch (counts.getAndIncrement()) {
                    case 0 -> 2L;
                    case 1 -> 1L;
                    case 2 -> 3L;
                    default -> 2L;
                });
        JdbcMcpRuntimeSourceMigrationContributor contributor =
                new JdbcMcpRuntimeSourceMigrationContributor(provider);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(5, inspection.totalRows());
        assertEquals(3, inspection.currentRows());
        assertEquals(2, inspection.manualReviewRows());
        assertTrue(inspection.reasonCodes().contains(
                "PROJECT_MCP_RUNTIME_DESCRIPTOR_INVALID"));
        assertTrue(inspection.reasonCodes().contains(
                "LEGACY_MCP_RUNTIME_MAPPING_REQUIRED"));
    }

    @Test
    void boundWorkflowScanMustRejectRunsWithoutFrozenTypedCheckpoint() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = provider(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    if (!invocation.getArgument(0, String.class).contains("information_schema.columns")) {
                        return 1L;
                    }
                    String table = invocation.getArgument(2, String.class);
                    return "ai_ops_agent_run_checkpoint".equals(table) ? 8L : 7L;
                });
        AtomicInteger counts = new AtomicInteger();
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> counts.getAndIncrement() == 0 ? 4L : 3L);
        JdbcBoundWorkflowSnapshotMigrationContributor contributor =
                new JdbcBoundWorkflowSnapshotMigrationContributor(provider);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(4, inspection.totalRows());
        assertEquals(3, inspection.currentRows());
        assertEquals(1, inspection.manualReviewRows());
        assertTrue(inspection.reasonCodes().contains(
                "BOUND_WORKFLOW_SNAPSHOT_RECONSTRUCTION_UNSAFE"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }

    private PlatformCapabilityMigrationCommand command() {
        return new PlatformCapabilityMigrationCommand(
                "migration-1", "operator", true, 100, List.of());
    }
}
