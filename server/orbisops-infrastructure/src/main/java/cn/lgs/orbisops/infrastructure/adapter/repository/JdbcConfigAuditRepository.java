package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditDraft;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditReadiness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class JdbcConfigAuditRepository implements IConfigAuditRepository {

    private static final DateTimeFormatter MYSQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SELECT_COLUMNS = """
            SELECT id, audit_id, project_id, agent_id, module_name, action_name, target_type, target_id,
                   risk_level, result_status, operator_id, operator_name, operator_role, client_ip,
                   trace_id, before_json, after_json, create_time
            FROM ai_ops_config_audit
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final boolean autoInit;
    private final boolean allowInMemoryEvidenceStore;
    private final String activeProfiles;
    private final List<ConfigAuditEntry> memoryAudit = new CopyOnWriteArrayList<>();

    public JdbcConfigAuditRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${orbisops.config-audit.auto-init:true}") boolean autoInit,
            @Value("${orbisops.safety.allow-in-memory-evidence-store:false}") boolean allowInMemoryEvidenceStore,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.autoInit = autoInit;
        this.allowInMemoryEvidenceStore = allowInMemoryEvidenceStore;
        this.activeProfiles = activeProfiles == null ? "" : activeProfiles;
    }

    @Override
    public ConfigAuditEntry append(ConfigAuditDraft draft) {
        if (draft == null) throw new IllegalArgumentException("CONFIG_AUDIT_DRAFT_REQUIRED");
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            requireMemoryFallback("OpsConfigAuditService 未配置持久化存储，生产/default 模式禁止内存审计 fallback");
            Optional<ConfigAuditEntry> existing = memoryAudit.stream()
                    .filter(row -> row.auditId().equals(draft.auditId()))
                    .findFirst();
            if (existing.isPresent()) return existing.get();
            ConfigAuditEntry entry = ConfigAuditEntry.from(draft);
            memoryAudit.add(entry);
            return entry;
        }
        try {
            jdbc.update("""
                    INSERT INTO ai_ops_config_audit
                      (audit_id, project_id, agent_id, module_name, action_name, target_type, target_id,
                       risk_level, result_status, operator_id, operator_name, operator_role, client_ip,
                       trace_id, before_json, after_json, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    draft.auditId(), draft.projectId(), draft.agentId(), draft.moduleName(), draft.actionName(),
                    draft.targetType(), draft.targetId(), draft.riskLevel(), draft.resultStatus(),
                    draft.operatorId(), draft.operatorName(), draft.operatorRole(), draft.clientIp(),
                    draft.traceId(), draft.beforeJson(), draft.afterJson(), draft.createTime());
            return find(draft.auditId()).orElseGet(() -> ConfigAuditEntry.from(draft));
        } catch (DuplicateKeyException duplicate) {
            return find(draft.auditId()).orElseThrow(() -> new IllegalStateException(
                    "审计幂等键冲突后无法读取既有记录 auditId=" + draft.auditId(), duplicate));
        } catch (DataAccessException e) {
            throw new IllegalStateException("记录配置审计失败，安全主链路必须 fail closed auditId="
                    + draft.auditId() + " reason=" + e.getMessage(), e);
        }
    }

    @Override
    public List<ConfigAuditEntry> search(ConfigAuditCriteria criteria) {
        ConfigAuditCriteria safe = criteria == null ? ConfigAuditCriteria.empty() : criteria;
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            requireMemoryFallback("AuditStore 未配置持久化存储，生产/default 模式禁止返回空审计列表");
            return memoryAudit.stream()
                    .filter(row -> safe.projectId().isBlank() || row.projectId().equals(safe.projectId()))
                    .filter(row -> safe.userId().isBlank()
                            || row.operatorId().equals(safe.userId())
                            || row.operatorName().equals(safe.userId()))
                    .filter(row -> safe.agentId().isBlank() || row.agentId().equals(safe.agentId()))
                    .filter(row -> safe.moduleName().isBlank() || row.moduleName().equals(safe.moduleName()))
                    .filter(row -> safe.actionName().isBlank() || row.actionName().equals(safe.actionName()))
                    .filter(row -> safe.riskLevel().isBlank() || row.riskLevel().equalsIgnoreCase(safe.riskLevel()))
                    .filter(row -> afterOrEqual(row.createTime(), safe.startTime()))
                    .filter(row -> beforeOrEqual(row.createTime(), safe.endTime()))
                    .limit(safe.limit())
                    .toList();
        }
        try {
            StringBuilder sql = new StringBuilder(SELECT_COLUMNS + " WHERE 1=1");
            List<Object> args = new ArrayList<>();
            appendEquals(sql, args, "project_id", safe.projectId());
            if (!safe.userId().isBlank()) {
                sql.append(" AND (operator_id=? OR operator_name=?)");
                args.add(safe.userId());
                args.add(safe.userId());
            }
            appendEquals(sql, args, "agent_id", safe.agentId());
            appendEquals(sql, args, "module_name", safe.moduleName());
            appendEquals(sql, args, "action_name", safe.actionName());
            appendEquals(sql, args, "risk_level", safe.riskLevel());
            if (!safe.startTime().isBlank()) {
                sql.append(" AND create_time>=?");
                args.add(safe.startTime());
            }
            if (!safe.endTime().isBlank()) {
                sql.append(" AND create_time<=?");
                args.add(safe.endTime());
            }
            sql.append(" ORDER BY id DESC LIMIT ?");
            args.add(safe.limit());
            return jdbc.query(sql.toString(), rowMapper(), args.toArray());
        } catch (DataAccessException e) {
            throw new IllegalStateException("查询配置审计失败，禁止伪装为空列表：" + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ConfigAuditEntry> find(String auditId) {
        String normalized = auditId == null ? "" : auditId.trim();
        if (normalized.isBlank()) return Optional.empty();
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            requireMemoryFallback("AuditStore 未配置持久化存储，生产/default 模式禁止返回空审计详情");
            return memoryAudit.stream().filter(row -> row.auditId().equals(normalized)).findFirst();
        }
        try {
            return jdbc.query(
                            SELECT_COLUMNS + " WHERE audit_id=? OR CAST(id AS CHAR)=? ORDER BY id DESC LIMIT 1",
                            rowMapper(), normalized, normalized)
                    .stream().findFirst();
        } catch (DataAccessException e) {
            throw new IllegalStateException("查询配置审计详情失败，禁止伪装为空详情 auditId="
                    + normalized + "：" + e.getMessage(), e);
        }
    }

    @Override
    public List<ConfigAuditEntry> listForOperator(String operator, int limit) {
        String normalized = operator == null ? "" : operator.trim();
        if (normalized.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            requireMemoryFallback("AuditStore 未配置持久化存储，生产/default 模式禁止返回空个人审计");
            return memoryAudit.stream()
                    .filter(row -> row.operatorId().equals(normalized) || row.operatorName().equals(normalized))
                    .limit(safeLimit)
                    .toList();
        }
        try {
            return jdbc.query(
                    SELECT_COLUMNS + " WHERE operator_id=? OR operator_name=? ORDER BY id DESC LIMIT ?",
                    rowMapper(), normalized, normalized, safeLimit);
        } catch (DataAccessException e) {
            throw new IllegalStateException("查询个人配置审计失败，禁止伪装为空列表：" + e.getMessage(), e);
        }
    }

    @Override
    public ConfigAuditReadiness readiness() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            requireMemoryFallback("AuditStore 未配置持久化存储");
            return new ConfigAuditReadiness(
                    "AuditStore", "DEGRADED_MEMORY", autoInit, true, "dev/test explicit memory fallback");
        }
        try {
            jdbc.queryForObject("SELECT COUNT(1) FROM ai_ops_config_audit", Integer.class);
            return new ConfigAuditReadiness("AuditStore", "UP", autoInit, canUseMemoryFallback(), "");
        } catch (DataAccessException e) {
            throw new IllegalStateException("AuditStore readiness 检查失败：" + e.getMessage(), e);
        }
    }

    private RowMapper<ConfigAuditEntry> rowMapper() {
        return (rs, rowNum) -> {
            Timestamp timestamp = rs.getTimestamp("create_time");
            return new ConfigAuditEntry(
                    rs.getLong("id"),
                    rs.getString("audit_id"),
                    rs.getString("project_id"),
                    rs.getString("agent_id"),
                    rs.getString("module_name"),
                    rs.getString("action_name"),
                    rs.getString("target_type"),
                    rs.getString("target_id"),
                    rs.getString("risk_level"),
                    rs.getString("result_status"),
                    rs.getString("operator_id"),
                    rs.getString("operator_name"),
                    rs.getString("operator_role"),
                    rs.getString("client_ip"),
                    rs.getString("trace_id"),
                    rs.getString("before_json"),
                    rs.getString("after_json"),
                    timestamp == null ? null : timestamp.toLocalDateTime());
        };
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append("=?");
            args.add(value.trim());
        }
    }

    private boolean afterOrEqual(LocalDateTime value, String startTime) {
        if (startTime == null || startTime.isBlank()) return true;
        LocalDateTime threshold = parseDateTime(startTime);
        return threshold == null || (value != null && !value.isBefore(threshold));
    }

    private boolean beforeOrEqual(LocalDateTime value, String endTime) {
        if (endTime == null || endTime.isBlank()) return true;
        LocalDateTime threshold = parseDateTime(endTime);
        return threshold == null || (value != null && !value.isAfter(threshold));
    }

    private LocalDateTime parseDateTime(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return null;
        try {
            return LocalDateTime.parse(normalized, MYSQL_DATE_TIME);
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(normalized);
            } catch (RuntimeException invalid) {
                return null;
            }
        }
    }

    private void requireMemoryFallback(String error) {
        if (!canUseMemoryFallback()) {
            throw new IllegalStateException(error);
        }
    }

    private boolean canUseMemoryFallback() {
        if (!allowInMemoryEvidenceStore) return false;
        for (String profile : activeProfiles.toLowerCase(Locale.ROOT).split(",")) {
            String normalized = profile.trim();
            if ("dev".equals(normalized) || "test".equals(normalized)) return true;
        }
        return false;
    }
}
