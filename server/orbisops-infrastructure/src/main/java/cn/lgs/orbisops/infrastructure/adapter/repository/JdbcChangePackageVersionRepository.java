package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcChangePackageVersionRepository implements IChangePackageVersionRepository {

    private static final String SELECT_COLUMNS = """
            id, package_id, version, package_hash, status, snapshot_json,
            change_summary, created_by, create_time
            """;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcChangePackageVersionRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcChangePackageVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void append(ChangePackageVersion version) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_change_package_version
                (package_id, version, package_hash, status, snapshot_json, change_summary, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, version.packageId(), version.version(), version.packageHash(), version.status(),
                ChangePackageSnapshotJsonCodec.encode(version.snapshot()), version.changeSummary(), version.createdBy());
    }

    @Override
    public Optional<ChangePackageVersion> find(String packageId, int version) {
        requireAvailable();
        return jdbcTemplate.queryForList("SELECT " + SELECT_COLUMNS + " FROM ai_ops_change_package_version "
                        + "WHERE package_id=? AND version=? LIMIT 1", packageId, version).stream()
                .findFirst().map(this::map);
    }

    @Override
    public List<ChangePackageVersion> findAll(String packageId) {
        requireAvailable();
        return jdbcTemplate.queryForList("SELECT " + SELECT_COLUMNS + " FROM ai_ops_change_package_version "
                        + "WHERE package_id=? ORDER BY version DESC", packageId).stream()
                .map(this::map).toList();
    }

    private ChangePackageVersion map(Map<String, Object> row) {
        String packageHash = text(row.get("package_hash"));
        return new ChangePackageVersion(
                longValue(row.get("id")), text(row.get("package_id")), intValue(row.get("version")),
                packageHash, text(row.get("status")),
                ChangePackageSnapshotJsonCodec.decode(text(row.get("snapshot_json")), packageHash),
                text(row.get("change_summary")), text(row.get("created_by")),
                localDateTime(row.get("create_time")));
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_VERSION_STORE_UNAVAILABLE");
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return null;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
