package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.application.config.AiClientApiHealthRecordPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** JDBC adapter for provider API health-check history. */
@Repository
public class AiClientApiHealthCheckRepository implements AiClientApiHealthRecordPort {

    private final JdbcTemplate jdbcTemplate;

    public AiClientApiHealthCheckRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public void save(AiClientApiHealthCheckResult result) {
        if (jdbcTemplate == null || result == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_client_api_health_check
                              (check_id, api_id, test_type, endpoint, status, http_status, latency_ms,
                               error_message, tested_by, test_time, create_time)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID().toString(),
                    result.apiId(),
                    result.testType(),
                    result.endpoint(),
                    result.status(),
                    result.httpStatus(),
                    result.latencyMs(),
                    truncate(result.errorMessage(), 1000),
                    result.testedBy(),
                    result.testTime(),
                    result.checkedAt());
        } catch (RuntimeException ignored) {
            // Health history is auxiliary evidence; probe outcome must not be rewritten by storage failure.
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
