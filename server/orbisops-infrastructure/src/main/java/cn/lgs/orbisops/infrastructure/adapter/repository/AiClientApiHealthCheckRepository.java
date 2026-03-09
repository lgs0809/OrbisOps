package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiHealthCheckRepository;
import cn.lgs.orbisops.domain.agent.model.valobj.AiClientApiHealthCheckRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiClientApiHealthCheckRepository implements IAiClientApiHealthCheckRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiClientApiHealthCheckRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public boolean save(AiClientApiHealthCheckRecord record) {
        if (jdbcTemplate == null || record == null) {
            return false;
        }
        try {
            return jdbcTemplate.update("""
                    INSERT INTO ai_client_api_health_check
                              (check_id, api_id, test_type, endpoint, status, http_status, latency_ms,
                               error_message, tested_by, test_time, create_time)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.checkId(),
                    record.apiId(),
                    record.testType(),
                    record.endpoint(),
                    record.status(),
                    record.httpStatus(),
                    record.latencyMs(),
                    record.errorMessage(),
                    record.testedBy(),
                    record.testTime(),
                    record.createTime()) == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
