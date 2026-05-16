package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcChangePackageEventRepository implements IChangePackageEventRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcChangePackageEventRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcChangePackageEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void append(ChangePackageEvent event) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_change_package_event
                (event_id, package_id, event_type, actor, summary, payload_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """, event.eventId(), event.packageId(), event.eventType(), event.actor(),
                event.summary(), ChangePackageJsonMapCodec.encode(event.payload()));
    }

    @Override
    public List<ChangePackageEvent> findRecent(String packageId, int limit) {
        requireAvailable();
        return jdbcTemplate.queryForList("""
                SELECT id, event_id, package_id, event_type, actor, summary, payload_json, create_time
                FROM ai_ops_change_package_event
                WHERE package_id=?
                ORDER BY id DESC
                LIMIT ?
                """, packageId, bounded(limit)).stream().map(this::map).toList();
    }

    private ChangePackageEvent map(Map<String, Object> row) {
        return new ChangePackageEvent(longValue(row.get("id")), text(row.get("event_id")),
                text(row.get("package_id")), text(row.get("event_type")), text(row.get("actor")),
                text(row.get("summary")), ChangePackageJsonMapCodec.decode(text(row.get("payload_json"))),
                localDateTime(row.get("create_time")));
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_EVENT_STORE_UNAVAILABLE");
    }

    private int bounded(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
