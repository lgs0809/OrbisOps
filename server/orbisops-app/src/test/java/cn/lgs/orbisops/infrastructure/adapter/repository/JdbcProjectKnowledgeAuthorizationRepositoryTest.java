package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcProjectKnowledgeAuthorizationRepositoryTest {

    @Test
    void missingJdbcReadsEmptyAndWritesFailClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcProjectKnowledgeAuthorizationRepository repository =
                new JdbcProjectKnowledgeAuthorizationRepository(provider);

        assertEquals(List.of(), repository.listEnabledKnowledgeBaseIds("project-1"));
        assertEquals(List.of(), repository.listUsageProjects("global-kb"));
        assertEquals(List.of(), repository.listEnabledUsageCounts());
        assertThrows(IllegalStateException.class,
                () -> repository.save(authorization()));
    }

    @Test
    void savesAuthorizationWithIdempotentUpsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcProjectKnowledgeAuthorizationRepository repository =
                new JdbcProjectKnowledgeAuthorizationRepository(provider);

        ProjectKnowledgeAuthorization saved = repository.save(authorization());

        assertEquals("project-1", saved.projectId());
        assertEquals("global-kb", saved.globalKbId());
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_project_knowledge_base")
                                && sql.contains("ON DUPLICATE KEY UPDATE")
                                && sql.contains("enabled_time=CURRENT_TIMESTAMP")),
                any(Object[].class));
    }

    @Test
    void queriesOnlyEnabledKnowledgeBasesForProject() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("project-1"), eq("ENABLED")))
                .thenReturn(List.of("global-a", "global-b"));
        JdbcProjectKnowledgeAuthorizationRepository repository =
                new JdbcProjectKnowledgeAuthorizationRepository(provider);

        assertEquals(List.of("global-a", "global-b"),
                repository.listEnabledKnowledgeBaseIds("project-1"));
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("WHERE project_id = ? AND status = ?")),
                eq(String.class), eq("project-1"), eq("ENABLED"));
    }

    private ProjectKnowledgeAuthorization authorization() {
        return new ProjectKnowledgeAuthorization(
                "project-1", "global-kb", KnowledgeStatus.ENABLED,
                "alice", "", "");
    }
}
