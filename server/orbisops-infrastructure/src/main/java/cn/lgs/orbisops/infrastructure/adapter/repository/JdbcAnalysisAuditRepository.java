package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.audit.adapter.repository.IAnalysisAuditRepository;
import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class JdbcAnalysisAuditRepository implements IAnalysisAuditRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final boolean jdbcEnabled;
    private volatile boolean unavailableLogged;

    public JdbcAnalysisAuditRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${orbisops.audit.jdbc-enabled:true}") boolean jdbcEnabled) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.jdbcEnabled = jdbcEnabled;
    }

    @Override
    public void upsert(AnalysisAuditRecord record) {
        JdbcTemplate jdbc = jdbcTemplate();
        if (jdbc == null || record == null) return;
        try {
            jdbc.update("""
                    INSERT INTO ai_ops_agent_audit
                    (analysis_id, success, question, intent, range_minutes, prom_window, generated_at, duration_ms,
                     selected_sources_json, executed_sources_json, skipped_sources_json, result_statuses_json,
                     insight_levels_json, conclusion, error_message)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                     success=VALUES(success), question=VALUES(question), intent=VALUES(intent),
                     range_minutes=VALUES(range_minutes), prom_window=VALUES(prom_window), generated_at=VALUES(generated_at),
                     duration_ms=VALUES(duration_ms), selected_sources_json=VALUES(selected_sources_json),
                     executed_sources_json=VALUES(executed_sources_json), skipped_sources_json=VALUES(skipped_sources_json),
                     result_statuses_json=VALUES(result_statuses_json), insight_levels_json=VALUES(insight_levels_json),
                     conclusion=VALUES(conclusion), error_message=VALUES(error_message)
                    """,
                    record.analysisId(),
                    record.success() ? 1 : 0,
                    record.question(),
                    record.intent(),
                    record.rangeMinutes(),
                    record.promWindow(),
                    record.generatedAt(),
                    record.durationMs(),
                    JSON.toJSONString(record.selectedSources()),
                    JSON.toJSONString(record.executedSources()),
                    JSON.toJSONString(record.skippedSources()),
                    JSON.toJSONString(record.resultStatuses()),
                    JSON.toJSONString(record.insightLevels()),
                    record.conclusion(),
                    record.errorMessage());
        } catch (DataAccessException e) {
            logFallback(e);
        }
    }

    @Override
    public List<AnalysisAuditRecord> list(int limit) {
        JdbcTemplate jdbc = jdbcTemplate();
        if (jdbc == null) return List.of();
        try {
            return jdbc.query("""
                    SELECT analysis_id, success, question, intent, range_minutes, prom_window, generated_at, duration_ms,
                           selected_sources_json, executed_sources_json, skipped_sources_json, result_statuses_json,
                           insight_levels_json, conclusion, error_message
                    FROM ai_ops_agent_audit
                    ORDER BY id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> new AnalysisAuditRecord(
                    rs.getString("analysis_id"),
                    rs.getInt("success") == 1,
                    rs.getString("question"),
                    rs.getString("intent"),
                    nullableInteger(rs.getObject("range_minutes")),
                    rs.getString("prom_window"),
                    rs.getString("generated_at"),
                    nullableLong(rs.getObject("duration_ms")),
                    parseStringList(rs.getString("selected_sources_json")),
                    parseStringList(rs.getString("executed_sources_json")),
                    parseStringList(rs.getString("skipped_sources_json")),
                    parseStringMap(rs.getString("result_statuses_json")),
                    parseStringList(rs.getString("insight_levels_json")),
                    rs.getString("conclusion"),
                    rs.getString("error_message")), limit);
        } catch (RuntimeException e) {
            logFallback(e);
            return List.of();
        }
    }

    private JdbcTemplate jdbcTemplate() {
        return jdbcEnabled ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    private Integer nullableInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        JSONArray array = JSON.parseArray(json);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) values.add(array.getString(i));
        return values;
    }

    private Map<String, String> parseStringMap(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        JSONObject object = JSON.parseObject(json);
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : object.keySet()) values.put(key, String.valueOf(object.get(key)));
        return values;
    }

    private void logFallback(Exception error) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("运维分析审计落库不可用，已降级为内存最近记录：{}", error.getMessage());
        }
    }
}
