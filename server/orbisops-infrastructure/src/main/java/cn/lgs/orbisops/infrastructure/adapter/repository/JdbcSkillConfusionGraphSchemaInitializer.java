package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class JdbcSkillConfusionGraphSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.skill-confusion.auto-init:true}")
    private boolean autoInit;

    public JdbcSkillConfusionGraphSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbcTemplate = provider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        if (!autoInit || jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_skill_confusion_edge (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      expected_skill_id VARCHAR(128) NOT NULL,
                      selected_skill_id VARCHAR(128) NOT NULL,
                      false_positive_count INT NOT NULL DEFAULT 0,
                      false_negative_count INT NOT NULL DEFAULT 0,
                      shadowing_count INT NOT NULL DEFAULT 0,
                      average_margin DECIMAL(8,6) NOT NULL DEFAULT 0,
                      evaluated_at TIMESTAMP(6) NOT NULL,
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_skill_confusion_edge (expected_skill_id, selected_skill_id),
                      KEY idx_skill_confusion_expected (expected_skill_id, evaluated_at),
                      KEY idx_skill_confusion_selected (selected_skill_id, evaluated_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill路由混淆图边'
                    """);
        } catch (DataAccessException error) {
            log.warn("Skill Confusion Graph 表初始化失败，路由评估降级：{}", error.getMessage());
        }
    }
}
