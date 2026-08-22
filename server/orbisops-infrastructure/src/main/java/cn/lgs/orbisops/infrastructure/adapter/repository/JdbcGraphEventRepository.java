package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.runtime.graph.adapter.repository.IGraphEventRepository;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEventDraft;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcGraphEventRepository implements IGraphEventRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.graph-events.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.graph-events.auto-init:true}")
    private boolean autoInit = true;

    @Value("${orbisops.graph-events.fail-closed:true}")
    private boolean failClosed = true;

    private volatile boolean initialized;
    private volatile boolean fallbackLogged;

    public JdbcGraphEventRepository(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Optional<GraphEvent> append(GraphEventDraft draft) {
        if (draft == null) throw new IllegalArgumentException("GRAPH_EVENT_DRAFT_REQUIRED");
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            if (failClosed) {
                throw new IllegalStateException("Graph Event Store 不可用，禁止继续 Work Session");
            }
            return Optional.empty();
        }
        try {
            ensureTable(template);
            long sequence = nextSequence(template, draft.sequenceKey());
            GraphEvent event = draft.materialize(sequence);
            template.update("""
                            INSERT INTO ai_ops_agent_node_trace
                            (run_id, analysis_id, sequence_no, event_type, node_id, node_type, agent, source,
                             status, summary, started_at, finished_at, duration_ms, payload_json)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    emptyToNull(event.runId()),
                    emptyToNull(event.analysisId()),
                    event.sequence(),
                    event.eventType(),
                    emptyToNull(event.nodeId()),
                    emptyToNull(event.nodeType()),
                    emptyToNull(event.agent()),
                    emptyToNull(event.source()),
                    emptyToNull(event.status()),
                    emptyToNull(event.summary()),
                    emptyToNull(event.startedAt()),
                    emptyToNull(event.finishedAt()),
                    event.durationMs(),
                    JSON.toJSONString(event.payload()));
            return Optional.of(event);
        } catch (DataAccessException error) {
            // Never convert an in-transaction write failure into a normal return: sequence
            // allocation and event insert must roll back atomically.
            throw new IllegalStateException("Graph Event Store 写入失败", error);
        }
    }

    @Override
    public List<GraphEvent> list(String scopeId, long afterSequence, int limit) {
        String scope = text(scopeId);
        if (scope.isBlank()) return List.of();
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            if (failClosed) {
                throw new IllegalStateException("Graph Event Store 不可用，无法恢复 Work Session 事件");
            }
            return List.of();
        }
        try {
            ensureTable(template);
            return template.query("""
                            SELECT run_id, analysis_id, sequence_no, event_type, node_id, node_type, agent, source,
                                   status, summary, started_at, finished_at, duration_ms, payload_json
                            FROM ai_ops_agent_node_trace
                            WHERE (run_id = ? OR analysis_id = ?) AND sequence_no > ?
                            ORDER BY sequence_no ASC, id ASC
                            LIMIT ?
                            """,
                    (resultSet, rowNum) -> new GraphEvent(
                            resultSet.getString("run_id"),
                            resultSet.getString("analysis_id"),
                            resultSet.getLong("sequence_no"),
                            resultSet.getString("event_type"),
                            resultSet.getString("node_id"),
                            resultSet.getString("node_type"),
                            resultSet.getString("agent"),
                            resultSet.getString("source"),
                            resultSet.getString("status"),
                            resultSet.getString("summary"),
                            resultSet.getString("started_at"),
                            resultSet.getString("finished_at"),
                            longNumber(resultSet.getObject("duration_ms")),
                            payload(resultSet.getString("payload_json"))),
                    scope,
                    scope,
                    Math.max(0L, afterSequence),
                    Math.max(1, Math.min(limit, 2000)))
                    .stream()
                    .sorted(Comparator.comparingLong(GraphEvent::sequence))
                    .toList();
        } catch (DataAccessException error) {
            if (failClosed) throw new IllegalStateException("Graph Event Store 查询失败", error);
            logFallback(error);
            return List.of();
        }
    }

    private long nextSequence(JdbcTemplate template, String sequenceKey) {
        int updated = template.update("""
                INSERT INTO ai_ops_graph_event_sequence (sequence_key, next_sequence)
                VALUES (?, LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE
                  next_sequence = LAST_INSERT_ID(next_sequence + 1),
                  updated_at = CURRENT_TIMESTAMP(3)
                """, sequenceKey);
        if (updated < 1) {
            throw new IllegalStateException(
                    "Graph Event Store 无法分配事件序号：sequenceKey=" + sequenceKey);
        }
        Long sequence = template.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (sequence == null || sequence <= 0L) {
            throw new IllegalStateException(
                    "Graph Event Store 返回无效事件序号：sequenceKey=" + sequenceKey);
        }
        return sequence;
    }

    private JdbcTemplate availableTemplate() {
        return jdbcEnabled ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            if (autoInit) {
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_graph_event_sequence (
                          sequence_key VARCHAR(160) NOT NULL COMMENT 'runId或analysisId',
                          next_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '最后分配的事件序号',
                          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                          PRIMARY KEY (sequence_key)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨实例Graph事件序号分配表'
                        """);
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_agent_node_trace (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                          run_id VARCHAR(80) NULL COMMENT 'Agent运行ID',
                          analysis_id VARCHAR(80) NULL COMMENT '分析ID',
                          sequence_no BIGINT NOT NULL COMMENT '事件序号',
                          event_type VARCHAR(128) NOT NULL COMMENT '事件类型',
                          node_id VARCHAR(256) NULL COMMENT 'Graph节点ID',
                          node_type VARCHAR(48) NULL COMMENT 'Graph节点类型',
                          agent VARCHAR(256) NULL COMMENT 'Agent',
                          source VARCHAR(256) NULL COMMENT '数据源',
                          status VARCHAR(32) NULL COMMENT '节点状态',
                          summary TEXT NULL COMMENT '节点摘要',
                          started_at VARCHAR(32) NULL COMMENT '开始时间',
                          finished_at VARCHAR(32) NULL COMMENT '结束时间',
                          duration_ms BIGINT NULL COMMENT '耗时毫秒',
                          payload_json TEXT NULL COMMENT '扩展上下文',
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_run_sequence (run_id, sequence_no),
                          KEY idx_run_id (run_id),
                          KEY idx_analysis_id (analysis_id),
                          KEY idx_node_id (node_id),
                          KEY idx_create_time (create_time)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent Graph节点事件与审计表'
                        """);
                template.execute("""
                        ALTER TABLE ai_ops_agent_node_trace
                        MODIFY COLUMN event_type VARCHAR(128) NOT NULL COMMENT '事件类型'
                        """);
                template.execute("""
                        ALTER TABLE ai_ops_agent_node_trace
                        MODIFY COLUMN node_id VARCHAR(256) NULL COMMENT 'Graph节点ID'
                        """);
                template.execute("""
                        ALTER TABLE ai_ops_agent_node_trace
                        MODIFY COLUMN agent VARCHAR(256) NULL COMMENT 'Agent',
                        MODIFY COLUMN source VARCHAR(256) NULL COMMENT '数据源'
                        """);
            }
            initialized = true;
        }
    }

    private Map<String, Object> payload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null ? Map.of() : new LinkedHashMap<>(object);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Long longNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void logFallback(Exception error) {
        if (fallbackLogged) return;
        fallbackLogged = true;
        log.warn("Graph Event Store 不可用，已降级为内存事件：{}", error.getMessage());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
