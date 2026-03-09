package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertAggregationRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSeed;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateState;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationAction;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAlertAggregationRepository implements IAlertAggregationRepository {

    private static final String SELECT_COLUMNS = """
            SELECT aggregate_key, project_id, rule_id, fingerprint, current_state, severity, severity_rank,
                   occurrence_count, pending_summary_count, affected_resources_json, payload_json, version
            FROM ai_ops_alert_aggregate
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcAlertAggregationRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean insertIfAbsent(AlertAggregateSeed seed) {
        return jdbc().update("""
                INSERT IGNORE INTO ai_ops_alert_aggregate
                (aggregate_key, project_id, rule_id, fingerprint, current_state, severity, severity_rank,
                 occurrence_count, pending_summary_count, affected_resources_json, payload_json, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, 1)
                """,
                seed.aggregateKey(),
                seed.projectId(),
                seed.ruleId(),
                seed.fingerprint(),
                seed.state().name(),
                seed.severity(),
                seed.severityRank(),
                JSON.toJSONString(seed.affectedResources()),
                JSON.toJSONString(seed.payload())) == 1;
    }

    @Override
    public Optional<AlertAggregateSnapshot> lock(String aggregateKey) {
        List<AlertAggregateSnapshot> rows = jdbc().query(
                SELECT_COLUMNS + " WHERE aggregate_key=? FOR UPDATE",
                rowMapper(), aggregateKey);
        return rows.stream().findFirst();
    }

    @Override
    public boolean apply(AlertAggregationPlan plan) {
        return switch (plan.action()) {
            case MARK_FIRST -> markFirst(plan);
            case TOUCH_RESOLVED -> touchResolved(plan);
            case MARK_IMMEDIATE -> markImmediate(plan);
            case SCHEDULE_SUMMARY -> scheduleSummary(plan);
            case NONE -> true;
        };
    }

    @Override
    public List<AlertAggregateSnapshot> lockDueSummaries(int limit, int staleClaimSeconds) {
        return jdbc().query(SELECT_COLUMNS + """
                WHERE current_state='FIRING'
                  AND pending_summary_count > 0
                  AND next_summary_at IS NOT NULL
                  AND next_summary_at <= CURRENT_TIMESTAMP
                  AND (summary_claim_token IS NULL
                       OR summary_claimed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND))
                ORDER BY severity_rank DESC, next_summary_at ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, rowMapper(), staleClaimSeconds, limit);
    }

    @Override
    public boolean claimSummary(
            AlertAggregateSnapshot aggregate,
            String claimToken,
            int staleClaimSeconds) {
        return jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET summary_claim_token=?, summary_claimed_at=CURRENT_TIMESTAMP, version=?
                        WHERE aggregate_key=? AND version=? AND pending_summary_count=?
                          AND (summary_claim_token IS NULL
                               OR summary_claimed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND))
                        """,
                claimToken,
                aggregate.version() + 1,
                aggregate.aggregateKey(),
                aggregate.version(),
                aggregate.pendingSummaryCount(),
                staleClaimSeconds) == 1;
    }

    @Override
    public boolean acknowledgeSummary(AlertSummaryClaim claim, int debounceSeconds) {
        return jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET pending_summary_count=GREATEST(0, pending_summary_count-?),
                            next_summary_at=CASE WHEN pending_summary_count > 0
                              THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND) ELSE NULL END,
                            max_summary_at=CASE WHEN pending_summary_count > 0 THEN max_summary_at ELSE NULL END,
                            summary_claim_token=NULL, summary_claimed_at=NULL,
                            last_dispatched_at=CURRENT_TIMESTAMP,
                            last_dispatch_type='SUMMARY', version=version+1
                        WHERE aggregate_key=? AND summary_claim_token=?
                        """,
                claim.summarizedOccurrences(),
                debounceSeconds,
                claim.aggregateKey(),
                claim.claimToken()) == 1;
    }

    @Override
    public void releaseSummaryClaim(AlertSummaryClaim claim) {
        jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET summary_claim_token=NULL, summary_claimed_at=NULL,
                            next_summary_at=LEAST(COALESCE(next_summary_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP),
                            version=version+1
                        WHERE aggregate_key=? AND summary_claim_token=?
                        """,
                claim.aggregateKey(), claim.claimToken());
    }

    private boolean markFirst(AlertAggregationPlan plan) {
        return jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET last_dispatched_at=CURRENT_TIMESTAMP,
                            last_dispatch_type='FIRST', version=version+1
                        WHERE aggregate_key=? AND version=?
                        """,
                plan.aggregateKey(), plan.expectedVersion()) == 1;
    }

    private boolean touchResolved(AlertAggregationPlan plan) {
        return jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET occurrence_count=occurrence_count+1,
                            affected_resources_json=?, payload_json=?,
                            last_seen_at=CURRENT_TIMESTAMP, version=version+1
                        WHERE aggregate_key=? AND version=?
                        """,
                JSON.toJSONString(plan.affectedResources()),
                JSON.toJSONString(plan.payload()),
                plan.aggregateKey(),
                plan.expectedVersion()) == 1;
    }

    private boolean markImmediate(AlertAggregationPlan plan) {
        return jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET current_state=?, severity=?, severity_rank=?,
                            occurrence_count=occurrence_count+1,
                            pending_summary_count=0, next_summary_at=NULL, max_summary_at=NULL,
                            affected_resources_json=?, payload_json=?,
                            last_dispatched_at=CURRENT_TIMESTAMP,
                            last_dispatch_type=?, last_seen_at=CURRENT_TIMESTAMP, version=version+1
                        WHERE aggregate_key=? AND version=?
                        """,
                plan.targetState().name(),
                plan.severity(),
                plan.severityRank(),
                JSON.toJSONString(plan.affectedResources()),
                JSON.toJSONString(plan.payload()),
                plan.eventType().name(),
                plan.aggregateKey(),
                plan.expectedVersion()) == 1;
    }

    private boolean scheduleSummary(AlertAggregationPlan plan) {
        return jdbc().update("""
                        UPDATE ai_ops_alert_aggregate
                        SET severity=?, severity_rank=GREATEST(severity_rank, ?),
                            occurrence_count=occurrence_count+1,
                            pending_summary_count=pending_summary_count+1,
                            affected_resources_json=?, payload_json=?, last_seen_at=CURRENT_TIMESTAMP,
                            max_summary_at=COALESCE(
                              max_summary_at,
                              DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND)),
                            next_summary_at=LEAST(
                              COALESCE(max_summary_at,
                                DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND)),
                              DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND)),
                            version=version+1
                        WHERE aggregate_key=? AND version=?
                        """,
                plan.severity(),
                plan.severityRank(),
                JSON.toJSONString(plan.affectedResources()),
                JSON.toJSONString(plan.payload()),
                plan.maxWaitSeconds(),
                plan.maxWaitSeconds(),
                plan.debounceSeconds(),
                plan.aggregateKey(),
                plan.expectedVersion()) == 1;
    }

    private RowMapper<AlertAggregateSnapshot> rowMapper() {
        return (rs, rowNum) -> new AlertAggregateSnapshot(
                rs.getString("aggregate_key"),
                rs.getString("project_id"),
                rs.getLong("rule_id"),
                rs.getString("fingerprint"),
                AlertAggregateState.valueOf(rs.getString("current_state")),
                rs.getString("severity"),
                rs.getInt("severity_rank"),
                rs.getLong("occurrence_count"),
                rs.getInt("pending_summary_count"),
                parseList(rs.getString("affected_resources_json")),
                parseMap(rs.getString("payload_json")),
                rs.getLong("version"));
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> result = JSON.parseObject(json, new TypeReference<List<String>>() { });
            return result == null ? List.of() : List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> result = JSON.parseObject(
                    json, new TypeReference<Map<String, Object>>() { });
            return result == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(result));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("ALERT_AGGREGATE_STORE_UNAVAILABLE");
        return jdbc;
    }
}
