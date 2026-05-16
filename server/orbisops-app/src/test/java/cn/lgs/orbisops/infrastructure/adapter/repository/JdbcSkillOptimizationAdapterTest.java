package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillDefectDiagnosis;
import cn.lgs.orbisops.domain.skill.model.SkillDefectLayer;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemory;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemoryType;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRound;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class JdbcSkillOptimizationAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcSkillOptimizationAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        adapter = new JdbcSkillOptimizationAdapter(jdbcTemplate);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void diagnosisInsertMustKeepTypedLayerAndEvidenceLists() {
        adapter.save(new SkillDefectDiagnosis(
                "diagnosis-1", "skill-1", 3, SkillDefectLayer.ROUTING,
                "false positive", "boundary too broad",
                List.of("trajectory-1"), List.of("counterexample-1"),
                0.91, "narrow routing summary", Instant.parse("2026-08-02T00:00:00Z")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("ai_ops_skill_defect_diagnosis"));
        assertEquals("ROUTING", args.getValue()[3]);
        assertEquals("[\"trajectory-1\"]", args.getValue()[6]);
    }

    @Test
    void optimizationMemoryMustUseDedicatedTable() {
        adapter.save(new SkillOptimizationMemory(
                "memory-1", "skill-1", 3,
                SkillOptimizationMemoryType.EFFECTIVE_PATCH,
                "Evidence threshold patch improved recall", List.of("evaluation-1"),
                "model-v2", "sandbox", true,
                Instant.parse("2026-08-02T00:00:00Z")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("ai_ops_skill_optimization_memory"));
        assertEquals("EFFECTIVE_PATCH", args.getValue()[3]);
        assertEquals(true, args.getValue()[8]);
    }

    @Test
    void runSnapshotMustPersistBoundedRoundAuditFacts() {
        SkillOptimizationRound round = new SkillOptimizationRound(
                1, List.of("diagnosis-1"), List.of("candidate-1"),
                List.of("evaluation-1"), "candidate-1", "verifier-v1",
                "PROMOTABLE", Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-02T00:01:00Z"));
        adapter.save(new SkillOptimizationRun(
                "run-1", "skill-1", 3, "base-hash",
                SkillOptimizationStatus.SUCCEEDED, 2, List.of(round),
                Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-02T00:01:00Z")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("ai_ops_skill_optimization_run"));
        assertTrue(String.valueOf(args.getValue()[6]).contains("verifier-v1"));
        assertTrue(String.valueOf(args.getValue()[6]).contains("candidate-1"));
    }

    @Test
    void queriesMustHaveStableExplicitOrdering() {
        reset(jdbcTemplate);
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));

        adapter.findBySkill("skill-1", 3, 10);
        ArgumentCaptor<String> diagnosisSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                diagnosisSql.capture(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class));
        assertTrue(diagnosisSql.getValue().contains(
                "ORDER BY confidence DESC, diagnosed_at DESC, diagnosis_id ASC"));

        reset(jdbcTemplate);
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
        adapter.findBySkill("skill-1", 10);
        ArgumentCaptor<String> memorySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                memorySql.capture(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class));
        assertTrue(memorySql.getValue().contains(
                "ORDER BY recorded_at DESC, memory_id ASC"));
    }
}
