package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcCodeDeliverySchemaInitializer {

    private final JdbcTemplate jdbc;
    private final boolean jdbcEnabled;
    private final boolean autoInit;

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcCodeDeliverySchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${orbisops.repair.jdbc-enabled:true}") boolean jdbcEnabled,
            @Value("${orbisops.repair.auto-init:true}") boolean autoInit) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.jdbcEnabled = jdbcEnabled;
        this.autoInit = autoInit;
    }

    JdbcCodeDeliverySchemaInitializer(JdbcTemplate jdbc, boolean jdbcEnabled, boolean autoInit) {
        this.jdbc = jdbc;
        this.jdbcEnabled = jdbcEnabled;
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (!jdbcEnabled || !autoInit || jdbc == null) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_code_delivery (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  delivery_id VARCHAR(80) NOT NULL,
                  workspace_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  service_id VARCHAR(100) NOT NULL,
                  delivery_mode VARCHAR(32) NOT NULL,
                  branch_name VARCHAR(160) NOT NULL,
                  commit_sha VARCHAR(40) NOT NULL,
                  pull_request_url VARCHAR(1000) NULL,
                  ci_status VARCHAR(40) NOT NULL,
                  ci_url VARCHAR(1000) NULL,
                  created_by VARCHAR(120) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_code_delivery (delivery_id),
                  KEY idx_delivery_workspace (workspace_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码修复分支、PR与CI状态'
                """);
    }
}
