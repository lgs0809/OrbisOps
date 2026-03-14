package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageCurrentReadPort;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentQuery;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcChangePackageCurrentRepository implements IChangePackageCurrentRepository, ChangePackageCurrentReadPort {

    private static final String INSERT_CURRENT = """
            INSERT INTO ai_ops_change_package
            (package_id, session_id, incident_id, project_id, preparation_agent_id, preparation_agent_version,
             package_type, status, version, package_hash, %s, create_by)
            VALUES (%s)
            """.formatted(
            ChangePackageCurrentStateJdbcMapper.columnList(),
            ChangePackageCurrentStateJdbcMapper.placeholders(
                    10 + ChangePackageCurrentStateJdbcMapper.fieldCount() + 1));

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcChangePackageCurrentRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcChangePackageCurrentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void verifyReadable() {
        requireAvailable();
        jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_ops_change_package", Integer.class);
    }

    @Override
    public void insert(ChangePackageCurrent current) {
        requireAvailable();
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        List<Object> values = new ArrayList<>();
        values.add(current.packageId());
        values.add(current.sessionId());
        values.add(nullable(current.incidentId()));
        values.add(current.projectId());
        values.add(current.preparationAgentId());
        values.add(current.preparationAgentVersion());
        values.add(current.packageType().name());
        values.add(current.status().name());
        values.add(current.version());
        values.add(current.packageHash());
        values.addAll(ChangePackageCurrentStateJdbcMapper.orderedValues(current.state()));
        values.add(current.createBy());
        jdbcTemplate.update(INSERT_CURRENT, values.toArray());
    }

    @Override
    public Optional<ChangePackageCurrent> find(String packageId) {
        requireAvailable();
        return jdbcTemplate.queryForList("""
                SELECT * FROM ai_ops_change_package
                WHERE package_id=?
                LIMIT 1
                """, required(packageId)).stream().findFirst().map(this::map);
    }

    @Override
    public List<ChangePackageCurrent> findAll(ChangePackageCurrentQuery query) {
        requireAvailable();
        if (query == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_QUERY_REQUIRED");
        StringBuilder sql = new StringBuilder("SELECT * FROM ai_ops_change_package WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendEquals(sql, args, "project_id", query.projectId());
        appendEquals(sql, args, "session_id", query.sessionId());
        appendEquals(sql, args, "incident_id", query.incidentId());
        if (query.status() != null) appendEquals(sql, args, "status", query.status().name());
        sql.append(" ORDER BY update_time DESC, id DESC LIMIT ?");
        args.add(query.limit());
        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream().map(this::map).toList();
    }

    private ChangePackageCurrent map(Map<String, Object> row) {
        ChangePackagePointer pointer = new ChangePackagePointer(
                text(row.get("package_id")), ChangePackageStatus.require(text(row.get("status"))),
                intValue(row.get("version")), text(row.get("package_hash")),
                intValue(row.get("approved_version")), text(row.get("approved_package_hash")));
        ChangePackageCurrentState state = ChangePackageCurrentStateJdbcMapper.fromRow(row);
        String approvedSnapshotJson = text(row.get("approved_snapshot_json"));
        if (!pointer.approved() && !approvedSnapshotJson.isBlank()) {
            throw new IllegalStateException("CHANGE_PACKAGE_UNAPPROVED_SNAPSHOT_PERSISTED");
        }
        ChangePackageSnapshot approvedSnapshot = pointer.approved()
                ? ChangePackageSnapshotJsonCodec.decode(approvedSnapshotJson, pointer.approvedPackageHash())
                : null;
        return new ChangePackageCurrent(
                longValue(row.get("id")), pointer, text(row.get("session_id")), text(row.get("incident_id")),
                text(row.get("project_id")), text(row.get("preparation_agent_id")),
                intValue(row.get("preparation_agent_version")), ChangePackageType.require(text(row.get("package_type"))),
                state, approvedSnapshot,
                text(row.get("landing_run_id")), text(row.get("create_by")), text(row.get("approve_by")),
                instant(row.get("create_time")), instant(row.get("update_time")), instant(row.get("approved_at")));
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND ").append(column).append("=?");
        args.add(value);
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
    }

    private String required(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_PACKAGE_ID_REQUIRED");
        return normalized;
    }

    private String nullable(String value) {
        String normalized = text(value);
        return normalized.isBlank() ? null : normalized;
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime dateTime) return dateTime.atZone(ZoneId.systemDefault()).toInstant();
        return null;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
