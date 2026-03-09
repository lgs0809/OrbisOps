package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSet;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteDraft;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteSnapshot;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSkillHiddenEvaluationSuiteAdapterTest {

    private static final String BASE_HASH = "a".repeat(64);
    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("structural-v1", "behavior-v1", "judge-v1");
    private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void publishMustPersistCanonicalTargetAndReturnStoredSnapshot() throws Exception {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        SkillHiddenEvaluationSuiteDraft draft = draft(hidden(), mutation());
        ResultSet row = row(
                draft.suiteId(),
                draft.hiddenCases(),
                draft.mutationCases(),
                draft.hiddenEvalHash(),
                draft.mutationEvalHash());
        stubRows(jdbc, row);
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);

        SkillHiddenEvaluationSuiteSnapshot result = adapter.publish(draft);

        assertEquals(draft.suiteId(), result.suiteId());
        assertEquals("project-1", result.projectId());
        assertEquals("skill-1", result.skillId());
        assertEquals(7, result.baseVersion());
        assertEquals(BASE_HASH, result.baseSkillHash());
        assertEquals("v1", result.suiteVersion());
        verify(jdbc).update(
                argThat(sql -> sql.contains("INSERT INTO ai_ops_skill_hidden_eval_suite")
                        && !sql.contains("ON DUPLICATE KEY UPDATE")),
                any(Object[].class));
        verify(jdbc).query(
                argThat(sql -> sql.contains("project_id=? AND skill_id=? AND base_version=?")
                        && sql.contains("base_skill_hash=? AND suite_version=?")
                        && sql.contains("status='ACTIVE'")),
                any(RowMapper.class),
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("skill-1"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(BASE_HASH),
                org.mockito.ArgumentMatchers.eq("v1"));
    }

    @Test
    void publishMustRejectSensitiveHiddenDataBeforeJdbcWrite() {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);
        SkillHiddenEvaluationSuiteDraft draft = draft(
                List.of(Map.of("caseId", "hidden-1", "apiToken", "secret-value")),
                mutation());

        SecurityException error = assertThrows(SecurityException.class,
                () -> adapter.publish(draft));

        assertEquals("SKILL_HIDDEN_SUITE_SENSITIVE_DATA_FORBIDDEN", error.getMessage());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadMustUseCompleteTournamentContextAndVerifyHashes() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        List<Map<String, Object>> hidden = hidden();
        List<Map<String, Object>> mutation = mutation();
        stubRows(jdbc, row(
                hidden,
                mutation,
                CanonicalObjectHasher.sha256(hidden),
                CanonicalObjectHasher.sha256(mutation)));
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);

        SkillHiddenEvaluationSet result = adapter.load(context(), candidate());

        assertEquals("suite-v1", result.suiteId());
        assertEquals(hidden, result.hiddenCases());
        assertEquals(mutation, result.mutationCases());
        verify(jdbc).query(
                anyString(),
                any(RowMapper.class),
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("skill-1"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(BASE_HASH),
                org.mockito.ArgumentMatchers.eq("v1"));
    }

    @Test
    void legacyLoadWithoutContextMustBeRejected() {
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.load("skill-1", 7, candidate()));

        assertEquals("SKILL_HIDDEN_EVAL_CONTEXT_REQUIRED", error.getMessage());
    }

    @Test
    void hiddenHashDriftMustFailClosed() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        stubRows(jdbc, row(
                hidden(),
                mutation(),
                "d".repeat(64),
                CanonicalObjectHasher.sha256(mutation())));
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);

        SecurityException error = assertThrows(SecurityException.class,
                () -> adapter.load(context(), candidate()));

        assertEquals("SKILL_HIDDEN_EVAL_HASH_MISMATCH", error.getMessage());
    }

    @Test
    void mutationHashDriftMustFailClosed() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        stubRows(jdbc, row(
                hidden(),
                mutation(),
                CanonicalObjectHasher.sha256(hidden()),
                "e".repeat(64)));
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);

        SecurityException error = assertThrows(SecurityException.class,
                () -> adapter.load(context(), candidate()));

        assertEquals("SKILL_MUTATION_EVAL_HASH_MISMATCH", error.getMessage());
    }

    @Test
    void duplicateStoredTargetsMustFailClosed() throws Exception {
        ObjectProvider<JdbcTemplate> provider = provider();
        JdbcTemplate jdbc = provider.getIfAvailable();
        ResultSet first = row(
                hidden(), mutation(),
                CanonicalObjectHasher.sha256(hidden()),
                CanonicalObjectHasher.sha256(mutation()));
        ResultSet second = row(
                hidden(), mutation(),
                CanonicalObjectHasher.sha256(hidden()),
                CanonicalObjectHasher.sha256(mutation()));
        stubRows(jdbc, first, second);
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.find("project-1", "skill-1", 7, BASE_HASH, "v1"));

        assertEquals("SKILL_HIDDEN_SUITE_DUPLICATE", error.getMessage());
    }

    @Test
    void missingJdbcMustFailClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcSkillHiddenEvaluationSuiteAdapter adapter = adapter(provider);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.find("project-1", "skill-1", 7, BASE_HASH, "v1"));

        assertEquals("SKILL_HIDDEN_SUITE_STORE_UNAVAILABLE", error.getMessage());
    }

    private JdbcSkillHiddenEvaluationSuiteAdapter adapter(
            ObjectProvider<JdbcTemplate> provider) {
        return new JdbcSkillHiddenEvaluationSuiteAdapter(provider, false);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubRows(JdbcTemplate jdbc, ResultSet... rows) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    List<Object> mapped = new ArrayList<>();
                    for (int index = 0; index < rows.length; index++) {
                        mapped.add(mapper.mapRow(rows[index], index));
                    }
                    return mapped;
                });
    }

    private ResultSet row(
            List<Map<String, Object>> hidden,
            List<Map<String, Object>> mutation,
            String hiddenHash,
            String mutationHash) throws Exception {
        return row("suite-v1", hidden, mutation, hiddenHash, mutationHash);
    }

    private ResultSet row(
            String suiteId,
            List<Map<String, Object>> hidden,
            List<Map<String, Object>> mutation,
            String hiddenHash,
            String mutationHash) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("suite_id")).thenReturn(suiteId);
        when(row.getString("project_id")).thenReturn("project-1");
        when(row.getString("skill_id")).thenReturn("skill-1");
        when(row.getLong("base_version")).thenReturn(7L);
        when(row.getString("base_skill_hash")).thenReturn(BASE_HASH);
        when(row.getString("suite_version")).thenReturn("v1");
        when(row.getString("hidden_cases_json")).thenReturn(JSON.toJSONString(hidden));
        when(row.getString("mutation_cases_json")).thenReturn(JSON.toJSONString(mutation));
        when(row.getString("hidden_eval_hash")).thenReturn(hiddenHash);
        when(row.getString("mutation_eval_hash")).thenReturn(mutationHash);
        when(row.getString("status")).thenReturn("ACTIVE");
        when(row.getString("actor")).thenReturn("alice");
        when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        when(row.getTimestamp("updated_at")).thenReturn(Timestamp.from(NOW));
        return row;
    }

    private SkillHiddenEvaluationSuiteDraft draft(
            List<Map<String, Object>> hidden,
            List<Map<String, Object>> mutation) {
        return new SkillHiddenEvaluationSuiteDraft(
                "project-1",
                "skill-1",
                7,
                BASE_HASH,
                "v1",
                hidden,
                mutation,
                "alice");
    }

    private List<Map<String, Object>> hidden() {
        return List.of(Map.of("caseId", "hidden-1", "input", "diagnose"));
    }

    private List<Map<String, Object>> mutation() {
        return List.of(Map.of("caseId", "mutation-1", "input", "diagnose-mutated"));
    }

    private SkillCandidateTournamentContext context() {
        return new SkillCandidateTournamentContext(
                "tournament-1",
                "project-1",
                "skill-1",
                7,
                BASE_HASH,
                "v1",
                VERSION);
    }

    private SkillTournamentCandidate candidate() {
        Map<String, Object> payload = Map.of(
                "patchType", "PATCH",
                "changes", List.of(Map.of(
                        "section", "procedure",
                        "key", "step-1",
                        "operation", "REPLACE")),
                "evalCases", List.of(Map.of("caseId", "eval-1")),
                "authoringSource", "TEST");
        SkillEvolutionAuthoredCandidate authored = SkillEvolutionAuthoredCandidate.from(payload);
        return new SkillTournamentCandidate(
                "candidate-1",
                CanonicalObjectHasher.sha256(payload),
                authored,
                1);
    }
}
