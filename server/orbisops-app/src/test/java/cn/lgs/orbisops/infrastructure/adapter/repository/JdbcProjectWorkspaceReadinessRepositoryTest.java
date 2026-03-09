package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcProjectWorkspaceReadinessRepositoryTest {

    @Test
    void availabilityReflectsJdbcEnablementAndMissingTemplate() {
        JdbcProjectWorkspaceReadinessRepository missing =
                new JdbcProjectWorkspaceReadinessRepository((JdbcTemplate) null);
        assertFalse(missing.available());

        JdbcProjectWorkspaceReadinessRepository disabled =
                new JdbcProjectWorkspaceReadinessRepository(mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(disabled, "jdbcEnabled", false);
        assertFalse(disabled.available());

        JdbcProjectWorkspaceReadinessRepository available =
                new JdbcProjectWorkspaceReadinessRepository(mock(JdbcTemplate.class));
        assertTrue(available.available());
    }

    @Test
    void sourceRepositoryCountKeepsReadyEnabledStatusesAndNormalizedProjectId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo-project")))
                .thenReturn(2);
        JdbcProjectWorkspaceReadinessRepository repository =
                new JdbcProjectWorkspaceReadinessRepository(jdbcTemplate);

        int count = repository.countReadySourceRepositories(" demo-project ");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), eq("demo-project"));
        assertEquals(2, count);
        assertTrue(sql.getValue().contains("FROM ai_ops_source_repository"));
        assertTrue(sql.getValue().contains("status IN ('READY', 'ENABLED')"));
    }

    @Test
    void executionResourceCountKeepsEnabledStatusAndProjectId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo-project")))
                .thenReturn(3);
        JdbcProjectWorkspaceReadinessRepository repository =
                new JdbcProjectWorkspaceReadinessRepository(jdbcTemplate);

        int count = repository.countEnabledExecutionResources("demo-project");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), eq("demo-project"));
        assertEquals(3, count);
        assertTrue(sql.getValue().contains("FROM ai_ops_execution_resource"));
        assertTrue(sql.getValue().contains("status = 'ENABLED'"));
    }

    @Test
    void nullAndNegativeCountsAreDefensivelyNormalizedToZero() {
        JdbcTemplate nullTemplate = mock(JdbcTemplate.class);
        when(nullTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo-project")))
                .thenReturn(null);
        JdbcProjectWorkspaceReadinessRepository nullRepository =
                new JdbcProjectWorkspaceReadinessRepository(nullTemplate);

        JdbcTemplate negativeTemplate = mock(JdbcTemplate.class);
        when(negativeTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo-project")))
                .thenReturn(-1);
        JdbcProjectWorkspaceReadinessRepository negativeRepository =
                new JdbcProjectWorkspaceReadinessRepository(negativeTemplate);

        assertEquals(0, nullRepository.countReadySourceRepositories("demo-project"));
        assertEquals(0, negativeRepository.countEnabledExecutionResources("demo-project"));
    }

    @Test
    void repositoryDoesNotSwallowJdbcFailures() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("catalog unavailable");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo-project")))
                .thenThrow(failure);
        JdbcProjectWorkspaceReadinessRepository repository =
                new JdbcProjectWorkspaceReadinessRepository(jdbcTemplate);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> repository.countReadySourceRepositories("demo-project"));

        assertEquals(failure, thrown);
    }
}
