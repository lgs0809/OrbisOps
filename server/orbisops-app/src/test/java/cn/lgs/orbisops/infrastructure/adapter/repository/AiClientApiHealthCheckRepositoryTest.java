package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientApiHealthCheckRepositoryTest {

    @Test
    void persistsTypedResultAndBoundsErrorHistory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiClientApiHealthCheckRepository repository = repository(jdbcTemplate);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        LocalDateTime checkedAt = LocalDateTime.of(2026, 7, 30, 3, 0);
        AiClientApiHealthCheckResult result = new AiClientApiHealthCheckResult(
                "local-provider",
                "MODELS_ENDPOINT",
                "http://127.0.0.1:8080/v1/models",
                "FAILED",
                500,
                18L,
                "x".repeat(1200),
                "admin-1",
                checkedAt,
                checkedAt);

        repository.save(result);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("ai_client_api_health_check"));
        assertEquals("local-provider", arguments.getValue()[1]);
        assertEquals("MODELS_ENDPOINT", arguments.getValue()[2]);
        assertEquals(1000, ((String) arguments.getValue()[7]).length());
        assertEquals("admin-1", arguments.getValue()[8]);
        assertEquals(checkedAt, arguments.getValue()[10]);
    }

    @Test
    void unavailableJdbcSilentlySkipsAuxiliaryHistory() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiClientApiHealthCheckRepository repository = new AiClientApiHealthCheckRepository(provider);

        repository.save(new AiClientApiHealthCheckResult(
                "provider", "MODELS_ENDPOINT", "endpoint", "SUCCESS",
                200, 10L, "", "operator", null, null));

        verify(provider).getIfAvailable();
    }

    @SuppressWarnings("unchecked")
    private AiClientApiHealthCheckRepository repository(JdbcTemplate jdbcTemplate) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        return new AiClientApiHealthCheckRepository(provider);
    }
}
