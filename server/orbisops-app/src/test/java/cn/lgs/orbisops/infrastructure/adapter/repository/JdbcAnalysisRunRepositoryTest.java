package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAnalysisRunRepositoryTest {

    @Test
    void unavailableRepositoryReflectsJdbcEnablementAndMissingTemplate() {
        JdbcAnalysisRunRepository missing = new JdbcAnalysisRunRepository((JdbcTemplate) null);
        assertFalse(missing.available());

        JdbcAnalysisRunRepository disabled = repository(mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(disabled, "jdbcEnabled", false);
        assertFalse(disabled.available());
    }

    @Test
    void saveKeepsUpsertIdentityAndSnapshotColumns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcAnalysisRunRepository repository = repository(jdbcTemplate);
        AnalysisRunSnapshot snapshot = snapshot();

        repository.save(snapshot);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_analysis_task"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals("run-1", arguments.getValue()[0]);
        assertEquals("project-1", arguments.getValue()[1]);
        assertEquals("ADMIN", arguments.getValue()[2]);
        assertEquals("RUNNING", arguments.getValue()[3]);
        assertEquals(1000L, arguments.getValue()[9]);
    }

    @Test
    void findRecentAndActiveCountUseTypedSnapshotAndOriginalOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> row = Map.ofEntries(
                Map.entry("run_id", "run-1"),
                Map.entry("project_id", "project-1"),
                Map.entry("trigger_source", "ADMIN"),
                Map.entry("status", "RUNNING"),
                Map.entry("request_json", "{\"question\":\"q\"}"),
                Map.entry("response_json", "{\"analysisId\":\"a1\"}"),
                Map.entry("created_at", "2026-07-22 10:00:00"),
                Map.entry("updated_at", "2026-07-22 10:01:00"),
                Map.entry("duration_ms", 1000L));
        doReturn(List.of(row)).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(2);
        JdbcAnalysisRunRepository repository = repository(jdbcTemplate);

        AnalysisRunSnapshot found = repository.find("run-1").orElseThrow();
        List<AnalysisRunSnapshot> recent = repository.findRecent(10);
        int active = repository.countActiveByProject("project-1");

        assertEquals("run-1", found.runId());
        assertEquals("project-1", found.projectId());
        assertNull(found.errorMessage());
        assertEquals(List.of("run-1"), recent.stream().map(AnalysisRunSnapshot::runId).toList());
        assertEquals(2, active);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().stream().anyMatch(value -> value.contains("ORDER BY id DESC")));
    }

    @Test
    void autoInitCompatibilityIsOwnedByInfrastructureAndRunsOnce() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
        JdbcAnalysisRunRepository repository = new JdbcAnalysisRunRepository(jdbcTemplate);

        repository.find("run-1");
        repository.find("run-2");

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertTrue(ddl.getValue().contains("CREATE TABLE IF NOT EXISTS ai_ops_analysis_task"));
    }

    private JdbcAnalysisRunRepository repository(JdbcTemplate jdbcTemplate) {
        JdbcAnalysisRunRepository repository = new JdbcAnalysisRunRepository(jdbcTemplate);
        ReflectionTestUtils.setField(repository, "autoInit", false);
        return repository;
    }

    private AnalysisRunSnapshot snapshot() {
        return new AnalysisRunSnapshot(
                "run-1",
                "project-1",
                "ADMIN",
                "RUNNING",
                "{\"question\":\"q\"}",
                "{\"analysisId\":\"a1\"}",
                null,
                "2026-07-22 10:00:00",
                "2026-07-22 10:01:00",
                1000L);
    }
}
