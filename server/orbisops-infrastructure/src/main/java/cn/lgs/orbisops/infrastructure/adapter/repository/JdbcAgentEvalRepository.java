package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agenteval.adapter.repository.IAgentEvalRepository;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseExecution;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunStart;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAgentEvalRepository implements IAgentEvalRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentEvalRepository(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) throw new IllegalStateException("Agent Eval 持久化未配置");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentEvalSuite saveSuite(AgentEvalSuite suite) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_agent_eval_suite
                  (suite_id, project_id, agent_id, suite_name, status, suite_version, created_by)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                suite.suiteId(), suite.projectId(), suite.agentId(), suite.name(),
                suite.version(), suite.createdBy());
        int order = 0;
        for (AgentEvalCase evalCase : suite.cases()) {
            jdbcTemplate.update("""
                    INSERT INTO ai_ops_agent_eval_case
                      (case_id, suite_id, project_id, agent_id, case_name, case_json, status, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                    """,
                    evalCase.caseId(), suite.suiteId(), suite.projectId(), suite.agentId(),
                    evalCase.name(), JSON.toJSONString(evalCase.toMap()), ++order);
        }
        return suite;
    }

    @Override
    public Optional<AgentEvalSuite> findSuite(String suiteId, String projectId) {
        List<Map<String, Object>> suites = jdbcTemplate.queryForList("""
                SELECT suite_id, project_id, agent_id, suite_name, suite_version, created_by
                FROM ai_ops_agent_eval_suite
                WHERE suite_id=? AND project_id=? AND status='ACTIVE' LIMIT 1
                """, suiteId, projectId);
        if (suites.isEmpty()) return Optional.empty();
        Map<String, Object> row = suites.get(0);
        List<AgentEvalCase> cases = jdbcTemplate.queryForList("""
                SELECT case_id, case_json
                FROM ai_ops_agent_eval_case
                WHERE suite_id=? AND project_id=? AND status='ACTIVE'
                ORDER BY sort_order ASC, id ASC
                """, suiteId, projectId).stream()
                .map(item -> {
                    Map<String, Object> payload = objectMap(item.get("case_json"));
                    payload.put("caseId", text(item.get("case_id")));
                    return AgentEvalCase.fromMap(payload);
                })
                .toList();
        return Optional.of(new AgentEvalSuite(
                text(row.get("suite_id")),
                text(row.get("project_id")),
                text(row.get("agent_id")),
                text(row.get("suite_name")),
                integer(row.get("suite_version")),
                cases,
                text(row.get("created_by"))));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void saveRun(
            AgentEvalRunStart run,
            AgentEvalRunResult result,
            Instant finishedAt) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_agent_eval_run
                  (eval_run_id, suite_id, project_id, agent_id, agent_version, definition_hash,
                   baseline_version, baseline_definition_hash, regression_status,
                   status, total_cases, passed_cases, failed_cases, started_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', 'RUNNING', ?, 0, 0, ?, ?)
                """,
                run.evalRunId(), run.suiteId(), run.projectId(), run.agentId(), run.agentVersion(),
                run.definitionHash(), run.baselineVersion(), run.baselineDefinitionHash(),
                run.totalCases(), timestamp(run.startedAt()), run.createdBy());

        for (AgentEvalCaseExecution execution : result.caseExecutions()) {
            jdbcTemplate.update("""
                    INSERT INTO ai_ops_agent_eval_case_run
                      (case_run_id, eval_run_id, case_id, project_id, agent_id, agent_version,
                       status, score, reason_codes_json, actual_json, started_at, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    execution.caseRunId(), execution.evalRunId(), execution.caseId(),
                    execution.projectId(), execution.agentId(), execution.agentVersion(),
                    execution.result().passed() ? "PASSED" : "FAILED",
                    execution.result().score(),
                    JSON.toJSONString(execution.result().reasonCodes()),
                    JSON.toJSONString(execution.result().actual()),
                    timestamp(execution.startedAt()), timestamp(execution.finishedAt()));
        }

        List<Map<String, Object>> resultViews = new ArrayList<>();
        for (int index = 0; index < result.caseExecutions().size(); index++) {
            AgentEvalCaseExecution execution = result.caseExecutions().get(index);
            Map<String, Object> view = new LinkedHashMap<>(execution.result().toMap());
            view.put("caseId", execution.caseId());
            if (index < result.baselineResults().size()) {
                view.put("baseline", result.baselineResults().get(index).toMap());
            }
            resultViews.add(view);
        }
        List<Map<String, Object>> baselineViews = new ArrayList<>();
        for (int index = 0; index < result.baselineResults().size(); index++) {
            Map<String, Object> view = new LinkedHashMap<>(result.baselineResults().get(index).toMap());
            if (index < result.caseExecutions().size()) {
                view.put("caseId", result.caseExecutions().get(index).caseId());
            }
            baselineViews.add(view);
        }

        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_eval_run
                SET status=?, regression_status=?, passed_cases=?, failed_cases=?, result_json=?,
                    baseline_result_json=?, finished_at=?
                WHERE eval_run_id=? AND status='RUNNING'
                """,
                result.status(), result.regressionStatus(), result.passedCases(), result.failedCases(),
                JSON.toJSONString(resultViews), JSON.toJSONString(baselineViews),
                timestamp(finishedAt), result.evalRunId());
        if (updated != 1) {
            throw new IllegalStateException("AGENT_EVAL_RUN_FINALIZE_CONFLICT:" + result.evalRunId());
        }
    }

    @Override
    public boolean hasPassedReleaseGate(
            String projectId,
            String agentId,
            int version,
            String definitionHash) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM ai_ops_agent_eval_run
                WHERE project_id=? AND agent_id=? AND agent_version=? AND definition_hash=?
                  AND status='PASSED' AND failed_cases=0 AND regression_status='PASSED'
                """, Integer.class, projectId, agentId, version, definitionHash);
        return count != null && count > 0;
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        String json = text(value);
        if (json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> result = JSON.parseObject(json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Agent Eval Case JSON 无法解析", error);
        }
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
