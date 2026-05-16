package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillDefectDiagnosisPort;
import cn.lgs.orbisops.application.skill.SkillOptimizationMemoryPort;
import cn.lgs.orbisops.application.skill.SkillOptimizationRunPort;
import cn.lgs.orbisops.domain.skill.model.SkillDefectDiagnosis;
import cn.lgs.orbisops.domain.skill.model.SkillDefectLayer;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemory;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemoryType;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRound;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationStatus;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC ACL for optimization-only Skill state. */
@Repository
public class JdbcSkillOptimizationAdapter implements
        SkillDefectDiagnosisPort,
        SkillOptimizationMemoryPort,
        SkillOptimizationRunPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillOptimizationAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_JDBC_REQUIRED");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SkillDefectDiagnosis save(SkillDefectDiagnosis diagnosis) {
        if (diagnosis == null) throw new IllegalArgumentException("SKILL_DIAGNOSIS_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_defect_diagnosis
                  (diagnosis_id,skill_id,skill_version,defect_layer,symptom,root_cause,
                   supporting_trajectory_ids_json,counterexample_ids_json,confidence,
                   suggested_direction,diagnosed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE diagnosis_id=diagnosis_id
                """,
                diagnosis.diagnosisId(), diagnosis.skillId(), diagnosis.skillVersion(),
                diagnosis.layer().name(), diagnosis.symptom(), diagnosis.rootCause(),
                JSON.toJSONString(diagnosis.supportingTrajectoryIds()),
                JSON.toJSONString(diagnosis.counterexampleIds()), diagnosis.confidence(),
                diagnosis.suggestedDirection(), Timestamp.from(diagnosis.diagnosedAt()));
        return diagnosis;
    }

    @Override
    public List<SkillDefectDiagnosis> findBySkill(
            String skillId,
            long skillVersion,
            int limit) {
        return jdbcTemplate.query("""
                        SELECT * FROM ai_ops_skill_defect_diagnosis
                        WHERE skill_id=? AND skill_version=?
                        ORDER BY confidence DESC, diagnosed_at DESC, diagnosis_id ASC
                        LIMIT ?
                        """,
                (row, index) -> diagnosis(rowMap(row)),
                skillId, skillVersion, limit);
    }

    @Override
    public SkillOptimizationMemory save(SkillOptimizationMemory memory) {
        if (memory == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_MEMORY_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_optimization_memory
                  (memory_id,skill_id,skill_version,memory_type,summary,evidence_ids_json,
                   model_compatibility,environment_compatibility,effective,recorded_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE memory_id=memory_id
                """,
                memory.memoryId(), memory.skillId(), memory.skillVersion(), memory.type().name(),
                memory.summary(), JSON.toJSONString(memory.evidenceIds()),
                memory.modelCompatibility(), memory.environmentCompatibility(), memory.effective(),
                Timestamp.from(memory.recordedAt()));
        return memory;
    }

    @Override
    public List<SkillOptimizationMemory> findBySkill(String skillId, int limit) {
        return jdbcTemplate.query("""
                        SELECT * FROM ai_ops_skill_optimization_memory
                        WHERE skill_id=?
                        ORDER BY recorded_at DESC, memory_id ASC
                        LIMIT ?
                        """,
                (row, index) -> memory(rowMap(row)),
                skillId, limit);
    }

    @Override
    public SkillOptimizationRun save(SkillOptimizationRun run) {
        if (run == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_RUN_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_optimization_run
                  (optimization_run_id,skill_id,base_version,base_skill_hash,status,max_rounds,
                   rounds_json,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  status=VALUES(status),max_rounds=VALUES(max_rounds),rounds_json=VALUES(rounds_json),
                  updated_at=VALUES(updated_at)
                """,
                run.runId(), run.skillId(), run.baseVersion(), run.baseSkillHash(),
                run.status().name(), run.maxRounds(), roundsJson(run.rounds()),
                Timestamp.from(run.createdAt()), Timestamp.from(run.updatedAt()));
        return run;
    }

    @Override
    public SkillOptimizationRun get(String runId) {
        return run(jdbcTemplate.queryForMap("""
                SELECT * FROM ai_ops_skill_optimization_run
                WHERE optimization_run_id=?
                """, runId));
    }

    private SkillDefectDiagnosis diagnosis(Map<String, Object> row) {
        return new SkillDefectDiagnosis(
                text(row.get("diagnosis_id")),
                text(row.get("skill_id")),
                number(row.get("skill_version")),
                SkillDefectLayer.valueOf(text(row.get("defect_layer"))),
                text(row.get("symptom")),
                text(row.get("root_cause")),
                stringList(row.get("supporting_trajectory_ids_json")),
                stringList(row.get("counterexample_ids_json")),
                decimal(row.get("confidence")),
                text(row.get("suggested_direction")),
                instant(row.get("diagnosed_at")));
    }

    private SkillOptimizationMemory memory(Map<String, Object> row) {
        return new SkillOptimizationMemory(
                text(row.get("memory_id")),
                text(row.get("skill_id")),
                number(row.get("skill_version")),
                SkillOptimizationMemoryType.valueOf(text(row.get("memory_type"))),
                text(row.get("summary")),
                stringList(row.get("evidence_ids_json")),
                text(row.get("model_compatibility")),
                text(row.get("environment_compatibility")),
                bool(row.get("effective")),
                instant(row.get("recorded_at")));
    }

    private SkillOptimizationRun run(Map<String, Object> row) {
        return new SkillOptimizationRun(
                text(row.get("optimization_run_id")),
                text(row.get("skill_id")),
                number(row.get("base_version")),
                text(row.get("base_skill_hash")),
                SkillOptimizationStatus.valueOf(text(row.get("status"))),
                integer(row.get("max_rounds")),
                rounds(row.get("rounds_json")),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private List<SkillOptimizationRound> rounds(Object value) {
        String json = text(value);
        if (json.isBlank() || "null".equalsIgnoreCase(json)) return List.of();
        List<Map> raw = JSON.parseArray(json, Map.class);
        if (raw == null || raw.isEmpty()) return List.of();
        List<SkillOptimizationRound> result = new ArrayList<>();
        for (Map<?, ?> item : raw) {
            Map<String, Object> row = new LinkedHashMap<>();
            item.forEach((key, entry) -> row.put(String.valueOf(key), entry));
            result.add(new SkillOptimizationRound(
                    integer(row.get("round")),
                    stringListValue(row.get("diagnosisIds")),
                    stringListValue(row.get("candidateIds")),
                    stringListValue(row.get("evaluationIds")),
                    text(row.get("selectedCandidateId")),
                    text(row.get("verifierVersion")),
                    text(row.get("reasonCode")),
                    instant(row.get("startedAt")),
                    optionalInstant(row.get("finishedAt"))));
        }
        return List.copyOf(result);
    }

    private String roundsJson(List<SkillOptimizationRound> rounds) {
        List<Map<String, Object>> values = rounds == null ? List.of() : rounds.stream()
                .map(round -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("round", round.round());
                    value.put("diagnosisIds", round.diagnosisIds());
                    value.put("candidateIds", round.candidateIds());
                    value.put("evaluationIds", round.evaluationIds());
                    value.put("selectedCandidateId", round.selectedCandidateId());
                    value.put("verifierVersion", round.verifierVersion());
                    value.put("reasonCode", round.reasonCode());
                    value.put("startedAt", round.startedAt().toString());
                    value.put("finishedAt", round.finishedAt() == null ? "" : round.finishedAt().toString());
                    return Map.copyOf(value);
                }).toList();
        return JSON.toJSONString(values);
    }

    private Map<String, Object> rowMap(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        java.sql.ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> result = new LinkedHashMap<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            result.put(metadata.getColumnLabel(column), resultSet.getObject(column));
        }
        return result;
    }

    private List<String> stringList(Object value) {
        String json = text(value);
        if (json.isBlank() || "null".equalsIgnoreCase(json)) return List.of();
        List<String> parsed = JSON.parseArray(json, String.class);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    private List<String> stringListValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::text).filter(item -> !item.isBlank()).toList();
        }
        return stringList(value);
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(text(value));
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(text(value));
    }

    private double decimal(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(text(value));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(text(value));
    }

    private Instant instant(Object value) {
        Instant parsed = optionalInstant(value);
        if (parsed == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_TIME_INVALID");
        return parsed;
    }

    private Instant optionalInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        String text = text(value);
        if (text.isBlank()) return null;
        return Instant.parse(text);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
