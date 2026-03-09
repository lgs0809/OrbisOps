package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingLockPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcChangePackageLandingLockAdapter implements ChangePackageLandingLockPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChangePackageLandingLockAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public List<String> acquire(String runId,
                                String leaseToken,
                                ChangePackageCurrent current,
                                ChangePackageLandingPlan plan,
                                String actor) {
        if (current == null || plan == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_LOCK_CONTEXT_REQUIRED");
        }
        List<String> acquired = new ArrayList<>();
        try {
            for (String resourceKey : resourceKeys(current, plan)) {
                acquireOne(resourceKey, runId, leaseToken, current);
                acquired.add(resourceKey);
            }
            return List.copyOf(acquired);
        } catch (RuntimeException error) {
            release(acquired, leaseToken);
            throw error;
        }
    }

    @Override
    public void release(List<String> resourceKeys, String leaseToken) {
        if (jdbcTemplate == null || resourceKeys == null || resourceKeys.isEmpty()) return;
        String keyColumn = resourceLockKeyColumn();
        for (String resourceKey : resourceKeys) {
            jdbcTemplate.update("""
                    UPDATE ai_ops_change_resource_lock
                    SET status='RELEASED', update_time=CURRENT_TIMESTAMP
                    WHERE %s=? AND lease_token=?
                    """.formatted(keyColumn), resourceKey, leaseToken);
        }
    }

    private void acquireOne(String resourceKey,
                            String runId,
                            String leaseToken,
                            ChangePackageCurrent current) {
        String keyColumn = resourceLockKeyColumn();
        List<Map<String, Object>> rows = template().queryForList("""
                SELECT * FROM ai_ops_change_resource_lock
                WHERE %s=?
                LIMIT 1
                """.formatted(keyColumn), resourceKey);
        if (rows.isEmpty()) {
            insert(resourceKey, keyColumn, runId, leaseToken, current);
            return;
        }
        Map<String, Object> existing = rows.get(0);
        if ("ACTIVE".equals(text(existing.get("status"))) && !expired(existing.get("lease_expires_at"))) {
            throw new IllegalStateException("LANDING_RESOURCE_LOCKED：" + resourceKey
                    + " existingRunId=" + text(existing.get("run_id")));
        }
        int updated = template().update("""
                        UPDATE ai_ops_change_resource_lock
                        SET package_id=?, project_id=?, run_id=?, lease_token=?, status='ACTIVE',
                            lease_expires_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE), update_time=CURRENT_TIMESTAMP
                        WHERE %s=?
                        """.formatted(keyColumn),
                current.packageId(), current.projectId(), runId, leaseToken, resourceKey);
        if (updated != 1) {
            throw new IllegalStateException("LANDING_RESOURCE_LOCK_CAS_CONFLICT：" + resourceKey);
        }
    }

    private void insert(String resourceKey,
                        String keyColumn,
                        String runId,
                        String leaseToken,
                        ChangePackageCurrent current) {
        if ("resource_lock_key".equals(keyColumn)
                && columnExists("ai_ops_change_resource_lock", "resource_key")
                && columnExists("ai_ops_change_resource_lock", "task_id")) {
            template().update("""
                            INSERT INTO ai_ops_change_resource_lock
                            (resource_lock_key, resource_key, task_id, package_id, project_id, run_id, lease_token, status, lease_expires_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE))
                            """,
                    resourceKey, resourceKey, runId, current.packageId(), current.projectId(), runId, leaseToken);
            return;
        }
        if ("resource_lock_key".equals(keyColumn)
                && columnExists("ai_ops_change_resource_lock", "resource_key")) {
            template().update("""
                            INSERT INTO ai_ops_change_resource_lock
                            (resource_lock_key, resource_key, package_id, project_id, run_id, lease_token, status, lease_expires_at)
                            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE))
                            """,
                    resourceKey, resourceKey, current.packageId(), current.projectId(), runId, leaseToken);
            return;
        }
        template().update("""
                        INSERT INTO ai_ops_change_resource_lock
                        (%s, package_id, project_id, run_id, lease_token, status, lease_expires_at)
                        VALUES (?, ?, ?, ?, ?, 'ACTIVE', DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE))
                        """.formatted(keyColumn),
                resourceKey, current.packageId(), current.projectId(), runId, leaseToken);
    }

    private List<String> resourceKeys(ChangePackageCurrent current, ChangePackageLandingPlan plan) {
        List<String> keys = new ArrayList<>();
        for (ChangePackageLandingOperation operation : plan.operations()) {
            if (!operation.resourceKey().isBlank()) {
                keys.add(current.projectId() + ":" + operation.resourceKey());
            } else if (!operation.operationId().isBlank()) {
                keys.add(current.projectId() + ":operation:" + operation.operationId());
            }
        }
        if (keys.isEmpty()) {
            keys.add(current.projectId() + ":package:" + current.packageId());
        }
        return keys.stream().distinct().toList();
    }

    private String resourceLockKeyColumn() {
        if (columnExists("ai_ops_change_resource_lock", "resource_lock_key")) return "resource_lock_key";
        if (columnExists("ai_ops_change_resource_lock", "resource_key")) return "resource_key";
        throw new IllegalStateException("ai_ops_change_resource_lock 缺少资源锁键字段");
    }

    private boolean columnExists(String table, String column) {
        Integer count = template().queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean expired(Object value) {
        if (value == null) return true;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().isBefore(Instant.now());
        if (value instanceof Instant instant) return instant.isBefore(Instant.now());
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().isBefore(Instant.now());
        }
        try {
            return Timestamp.valueOf(String.valueOf(value)).toInstant().isBefore(Instant.now());
        } catch (RuntimeException ignored) {
            try {
                return Instant.parse(String.valueOf(value)).isBefore(Instant.now());
            } catch (RuntimeException ignoredAgain) {
                return false;
            }
        }
    }

    private JdbcTemplate template() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_LANDING_LOCK_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
