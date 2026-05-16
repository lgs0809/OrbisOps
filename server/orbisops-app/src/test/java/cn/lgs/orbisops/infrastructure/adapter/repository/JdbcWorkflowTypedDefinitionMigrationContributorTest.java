package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import cn.lgs.orbisops.application.migration.WorkflowDefinitionDocumentMigrationPort;
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

class JdbcWorkflowTypedDefinitionMigrationContributorTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void inspectAndBackfillMustUseDocumentMigratorAndOriginalValueCas() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("information_schema.columns") ? 4L : 1L);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("source_table")).thenReturn("CURRENT");
        when(row.getString("agent_id")).thenReturn("agent-1");
        when(row.getInt("version")).thenReturn(3);
        when(row.getString("definition_json")).thenReturn("{\"schemaVersion\":0}");
        when(row.getString("definition_hash")).thenReturn("");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        WorkflowDefinitionDocumentMigrationPort documents = json ->
                WorkflowDefinitionDocumentMigrationPort.MigrationDocument.migrated(
                        "{\"schemaVersion\":1}", "a".repeat(64));
        JdbcWorkflowTypedDefinitionMigrationContributor contributor =
                new JdbcWorkflowTypedDefinitionMigrationContributor(provider, documents);
        PlatformCapabilityMigrationCommand command = command();

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command);
        var outcome = contributor.backfill(command, inspection);

        assertEquals(1, inspection.backfillableRows());
        assertEquals(1, outcome.backfilledRows());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        assertEquals("{\"schemaVersion\":1}", args.getValue()[0]);
        assertEquals("a".repeat(64), args.getValue()[1]);
        assertEquals("agent-1", args.getValue()[2]);
        assertEquals(3, args.getValue()[3]);
        assertEquals("{\"schemaVersion\":0}", args.getValue()[4]);
        assertEquals("", args.getValue()[5]);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unsupportedDefinitionMustBeCountedAsManualReview() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("information_schema.columns") ? 4L : 1L);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("source_table")).thenReturn("VERSION");
        when(row.getString("agent_id")).thenReturn("agent-1");
        when(row.getInt("version")).thenReturn(4);
        when(row.getString("definition_json")).thenReturn("future");
        when(row.getString("definition_hash")).thenReturn("a".repeat(64));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        WorkflowDefinitionDocumentMigrationPort documents = json ->
                WorkflowDefinitionDocumentMigrationPort.MigrationDocument.review(
                        "WORKFLOW_DEFINITION_JSON_UNSUPPORTED");
        JdbcWorkflowTypedDefinitionMigrationContributor contributor =
                new JdbcWorkflowTypedDefinitionMigrationContributor(provider, documents);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(1, inspection.manualReviewRows());
        assertTrue(inspection.reasonCodes().contains(
                "WORKFLOW_DEFINITION_JSON_UNSUPPORTED"));
    }

    private PlatformCapabilityMigrationCommand command() {
        return new PlatformCapabilityMigrationCommand(
                "migration-1", "operator", false, 100, List.of());
    }
}
