package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.evidence.adapter.repository.IToolResultRepository;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultBudget;
import cn.lgs.orbisops.domain.evidence.service.ToolResultPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@DependsOn("jdbcEvidenceSchemaInitializer")
public class JdbcToolResultRepository implements IToolResultRepository {

    private final JdbcTemplate jdbc;
    private final boolean memoryFallbackAllowed;
    private final ToolResultPolicy policy = new ToolResultPolicy();
    private final Map<String, ToolResult> memory = new ConcurrentHashMap<>();

    public JdbcToolResultRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            @Value("${orbisops.safety.allow-in-memory-evidence-store:false}") boolean allowMemory,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.jdbc = provider.getIfAvailable();
        this.memoryFallbackAllowed = allowMemory && developmentProfile(activeProfiles);
    }

    @Override
    public ToolResult save(ToolResult result) {
        if (result == null) throw new IllegalArgumentException("TOOL_RESULT_REQUIRED");
        if (jdbc != null) {
            try {
                jdbc.update("""
                        INSERT INTO ai_ops_tool_result
                        (result_id, project_id, session_id, run_id, user_id, toolset_id, tool_name, source, status,
                         query_text, input_hash, preview_text, full_output, full_output_ref, output_hash, truncated,
                         duration_ms, max_rows, max_bytes, max_lines, max_points, max_time_range, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        result.resultId(), result.projectId(), result.sessionId(), result.runId(), result.userId(),
                        result.toolsetId(), result.toolName(), result.source(), result.status(), result.query(),
                        result.inputHash(), result.preview(), result.fullOutput(), result.fullOutputRef(),
                        result.outputHash(), result.truncated() ? 1 : 0, result.durationMs(),
                        result.budget().maxRows(), result.budget().maxBytes(), result.budget().maxLines(),
                        result.budget().maxPoints(), result.budget().maxTimeRangeMinutes(), result.createdBy());
                memory.put(result.resultId(), result);
                return result;
            } catch (DataAccessException e) {
                throw new IllegalStateException(
                        "ToolResult 持久化失败，工具执行必须 fail closed：" + e.getMessage(), e);
            }
        }
        if (!memoryFallbackAllowed) {
            throw new IllegalStateException(
                    "ToolResultStore 未配置持久化存储，生产/default 模式禁止内存 fallback");
        }
        memory.put(result.resultId(), result);
        return result;
    }

    @Override
    public Optional<ToolResult> find(String resultId) {
        String id = value(resultId);
        ToolResult cached = memory.get(id);
        if (cached != null) return Optional.of(cached);
        if (jdbc == null) {
            if (!memoryFallbackAllowed) {
                throw new IllegalStateException(
                        "ToolResultStore 未配置持久化存储，生产/default 模式禁止读取内存 fallback");
            }
            return Optional.empty();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ai_ops_tool_result WHERE result_id = ? LIMIT 1", id);
        if (rows.isEmpty()) return Optional.empty();
        ToolResult result = map(rows.get(0));
        memory.put(id, result);
        return Optional.of(result);
    }

    @Override
    public List<ToolResult> listForRun(String projectId, String runId, int limit) {
        if (jdbc == null) {
            if (!memoryFallbackAllowed) throw new IllegalStateException("ToolResultStore 未配置持久化存储");
            return memory.values().stream()
                    .filter(item -> value(projectId).equals(item.projectId()))
                    .filter(item -> value(runId).equals(item.runId()))
                    .limit(limit)
                    .toList();
        }
        return jdbc.queryForList("""
                SELECT * FROM ai_ops_tool_result
                WHERE project_id=? AND run_id=?
                ORDER BY id ASC LIMIT ?
                """, value(projectId), value(runId), limit).stream().map(this::map).toList();
    }

    @Override
    public long count() {
        if (jdbc != null) {
            Long count = jdbc.queryForObject("SELECT COUNT(1) FROM ai_ops_tool_result", Long.class);
            return count == null ? 0L : count;
        }
        if (!memoryFallbackAllowed) throw new IllegalStateException("ToolResultStore 未配置持久化存储");
        return memory.size();
    }

    @Override
    public boolean persistent() {
        return jdbc != null;
    }

    @Override
    public boolean memoryFallbackAllowed() {
        return memoryFallbackAllowed;
    }

    private ToolResult map(Map<String, Object> row) {
        String query = raw(row.get("query_text"));
        String fullOutput = raw(row.get("full_output"));
        return new ToolResult(
                text(row, "result_id"), text(row, "project_id"), text(row, "session_id"),
                text(row, "run_id"), text(row, "user_id"), text(row, "toolset_id"),
                text(row, "tool_name"), text(row, "source"), text(row, "status"),
                query, legacyHash(text(row, "input_hash"), query), raw(row.get("preview_text")),
                fullOutput, text(row, "full_output_ref"), legacyHash(text(row, "output_hash"), fullOutput),
                bool(row.get("truncated")), number(row.get("duration_ms")).longValue(),
                new ToolResultBudget(
                        number(row.get("max_rows")).intValue(),
                        number(row.get("max_bytes")).intValue(),
                        number(row.get("max_lines")).intValue(),
                        number(row.get("max_points")).intValue(),
                        number(row.get("max_time_range")).intValue()),
                text(row, "created_by"), time(row.get("create_time")));
    }

    private String legacyHash(String stored, String source) {
        String normalized = value(stored).toLowerCase(Locale.ROOT);
        return normalized.matches("[a-f0-9]{64}") ? normalized : policy.sha256(source);
    }

    private boolean developmentProfile(String profiles) {
        for (String profile : value(profiles).toLowerCase(Locale.ROOT).split(",")) {
            if ("dev".equals(profile.trim()) || "test".equals(profile.trim())) return true;
        }
        return false;
    }

    private String text(Map<String, Object> row, String key) {
        return value(row.get(key));
    }

    private String raw(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Number number(Object value) {
        if (value instanceof Number number) return number;
        try { return Long.parseLong(this.value(value)); }
        catch (Exception ignored) { return 0L; }
    }

    private boolean bool(Object value) {
        return number(value).intValue() != 0 || Boolean.TRUE.equals(value);
    }

    private String time(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        String normalized = this.value(value);
        return normalized.isBlank() ? "1970-01-01T00:00:00Z" : normalized;
    }
}
