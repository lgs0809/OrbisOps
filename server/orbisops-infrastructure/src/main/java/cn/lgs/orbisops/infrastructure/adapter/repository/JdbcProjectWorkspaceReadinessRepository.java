package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectWorkspaceReadinessRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectWorkspaceReadinessRepository implements IProjectWorkspaceReadinessRepository {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.project-workspace.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Autowired
    public JdbcProjectWorkspaceReadinessRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcProjectWorkspaceReadinessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcEnabled && jdbcTemplate != null;
    }

    @Override
    public int countReadySourceRepositories(String projectId) {
        Integer count = requiredTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM ai_ops_source_repository
                WHERE project_id = ?
                  AND status IN ('READY', 'ENABLED')
                """, Integer.class, projectId.trim());
        return normalizeCount(count);
    }

    @Override
    public int countEnabledExecutionResources(String projectId) {
        Integer count = requiredTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM ai_ops_execution_resource
                WHERE project_id = ?
                  AND status = 'ENABLED'
                """, Integer.class, projectId.trim());
        return normalizeCount(count);
    }

    private JdbcTemplate requiredTemplate() {
        if (!available()) {
            throw new IllegalStateException("PROJECT_WORKSPACE_READINESS_STORE_UNAVAILABLE");
        }
        return jdbcTemplate;
    }

    private int normalizeCount(Integer count) {
        return count == null ? 0 : Math.max(count, 0);
    }
}
