package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort;
import cn.lgs.orbisops.domain.skill.service.SkillRuntimeBodyPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Repository
public class JdbcSkillRuntimeBudgetRepository implements SkillRuntimeBudgetPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    @Autowired
    public JdbcSkillRuntimeBudgetRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc) { this(jdbc.getIfAvailable()); }
    public JdbcSkillRuntimeBudgetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        tx = jdbc == null ? null : new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    @Override public void reserve(String project, String run, List<Load> loads) {
        if (loads == null || loads.isEmpty()) return;
        if (project == null || project.isBlank() || run == null || run.isBlank()) throw new IllegalArgumentException("SKILL_RUNTIME_BUDGET_RUN_REQUIRED");
        if (jdbc == null) throw new IllegalStateException("SKILL_RUNTIME_BUDGET_STORE_UNAVAILABLE");
        if (loads.size() > 100) throw new IllegalArgumentException("SKILL_RUNTIME_BUDGET_BATCH_TOO_LARGE");
        tx.executeWithoutResult(ignored -> {
            jdbc.update("INSERT INTO ai_ops_skill_runtime_budget(project_id,run_id) VALUES (?,?) ON DUPLICATE KEY UPDATE run_id=VALUES(run_id)", project, run);
            jdbc.queryForObject("SELECT run_id FROM ai_ops_skill_runtime_budget WHERE project_id=? AND run_id=? FOR UPDATE", String.class, project, run);
            var rows = jdbc.queryForList("SELECT skill_key,item_hash,units FROM ai_ops_skill_runtime_budget_item WHERE project_id=? AND run_id=? FOR UPDATE", project, run);
            Map<String,Load> combined = new LinkedHashMap<>();
            for (var row : rows) combined.put((String) row.get("item_hash"), new Load((String) row.get("skill_key"), (String) row.get("item_hash"), ((Number) row.get("units")).intValue()));
            for (Load load : loads) {
                Load old = combined.putIfAbsent(load.itemHash(), load);
                if (old != null && !old.equals(load)) throw new IllegalStateException("SKILL_RUNTIME_BUDGET_IDENTITY_CONFLICT");
            }
            if (combined.values().stream().map(Load::skillKey).distinct().count() > SkillRuntimeBodyPolicy.MAX_SKILLS)
                throw new IllegalStateException("SKILL_RUNTIME_SKILL_COUNT_EXCEEDED");
            if (combined.values().stream().mapToLong(Load::units).sum() > SkillRuntimeBodyPolicy.TOTAL_BUDGET)
                throw new IllegalStateException("SKILL_RUNTIME_TOTAL_BODY_BUDGET_EXCEEDED");
            for (Load load : loads) jdbc.update("INSERT IGNORE INTO ai_ops_skill_runtime_budget_item(project_id,run_id,skill_key,item_hash,units) VALUES (?,?,?,?,?)",
                    project, run, load.skillKey(), load.itemHash(), load.units());
        });
    }
}
