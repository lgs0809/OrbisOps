package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcKnowledgeBaseCatalogRepositoryTest {

    @Test
    void missingJdbcReadsEmptyAndWritesFailClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcKnowledgeBaseCatalogRepository repository = new JdbcKnowledgeBaseCatalogRepository(provider);

        assertEquals(List.of(), repository.list(KnowledgeScope.GLOBAL, ""));
        assertEquals(List.of(), repository.listEnabledProject("project-1"));
        assertThrows(IllegalStateException.class, () -> repository.save(entry()));
    }

    @Test
    void savesCatalogEntryUsingIdempotentUpsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcKnowledgeBaseCatalogRepository repository = new JdbcKnowledgeBaseCatalogRepository(provider);

        repository.save(entry());

        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_knowledge_base")
                                && sql.contains("ON DUPLICATE KEY UPDATE")
                                && !sql.contains("create_by=VALUES(create_by)")),
                any(Object[].class));
    }

    @Test
    void workspaceQueryPreservesRequestedGlobalAuthorizationOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcKnowledgeBaseCatalogRepository repository = new JdbcKnowledgeBaseCatalogRepository(provider);
        JdbcKnowledgeBaseCatalogRepository spy = org.mockito.Mockito.spy(repository);
        KnowledgeBaseCatalogEntry a = entry("global-a");
        KnowledgeBaseCatalogEntry b = entry("global-b");
        org.mockito.Mockito.doReturn(List.of(a, b))
                .when(spy).list(KnowledgeScope.GLOBAL, "");

        List<KnowledgeBaseCatalogEntry> result = spy.listEnabledGlobalByIds(
                List.of("global-b", "missing", "global-a"));

        assertEquals(List.of("global-b", "global-a"),
                result.stream().map(item -> item.key().kbId()).toList());
    }

    private KnowledgeBaseCatalogEntry entry() {
        return entry("ops-kb");
    }

    private KnowledgeBaseCatalogEntry entry(String kbId) {
        return new KnowledgeBaseCatalogEntry(
                null,
                new KnowledgeBaseCatalogKey(KnowledgeScope.GLOBAL, "", kbId),
                kbId,
                "",
                KnowledgeStatus.ENABLED,
                0L,
                0L,
                "DB",
                "",
                "alice",
                "",
                "");
    }
}
