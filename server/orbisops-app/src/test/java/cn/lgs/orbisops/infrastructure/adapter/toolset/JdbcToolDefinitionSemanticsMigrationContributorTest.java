package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcToolDefinitionSemanticsMigrationContributorTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void missingSafetySemanticsMustBeQuarantinedAndToolsetDisabled() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("information_schema.columns") ? 4L : 1L);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("project_id")).thenReturn("project-1");
        when(row.getString("toolset_id")).thenReturn("legacy-tools");
        when(row.getString("tools_json")).thenReturn("""
                [{"toolName":"apply","adapterType":"LOCAL","enabled":true}]
                """);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcToolDefinitionSemanticsMigrationContributor contributor =
                new JdbcToolDefinitionSemanticsMigrationContributor(provider);
        PlatformCapabilityMigrationCommand command = command();

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command);
        var outcome = contributor.backfill(command, inspection);

        assertEquals(1, inspection.backfillableRows());
        assertEquals(0, inspection.manualReviewRows());
        assertEquals(1, outcome.backfilledRows());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        String migrated = String.valueOf(args.getValue()[0]);
        assertTrue(migrated.contains("\"migrationReviewRequired\":true"));
        assertTrue(migrated.contains("\"requiresApproval\":true"));
        assertTrue(migrated.contains("\"enabled\":false"));
        assertTrue(migrated.contains("\"riskLevel\":\"HIGH\""));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void malformedOrIdentityMissingToolMustRequireManualReview() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("information_schema.columns") ? 4L : 1L);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("project_id")).thenReturn("project-1");
        when(row.getString("toolset_id")).thenReturn("broken");
        when(row.getString("tools_json")).thenReturn("[{\"adapterType\":\"LOCAL\"}]");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        JdbcToolDefinitionSemanticsMigrationContributor contributor =
                new JdbcToolDefinitionSemanticsMigrationContributor(provider);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(1, inspection.manualReviewRows());
        assertTrue(inspection.reasonCodes().contains("TOOL_DEFINITION_TOOL_NAME_MISSING"));
    }

    private PlatformCapabilityMigrationCommand command() {
        return new PlatformCapabilityMigrationCommand(
                "migration-1", "operator", false, 100, List.of());
    }
}
