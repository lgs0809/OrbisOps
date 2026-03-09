package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcKnowledgeRetrievalPolicyRepositoryTest {

    @Test
    void missingJdbcReturnsNoOverrideAndRejectsWrites() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcKnowledgeRetrievalPolicyRepository repository = new JdbcKnowledgeRetrievalPolicyRepository(provider);
        KnowledgeRetrievalPolicyKey key = new KnowledgeRetrievalPolicyKey(
                KnowledgeScope.GLOBAL, "", "ops-kb");

        assertTrue(repository.find(key).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> repository.save(key, policy()));
    }

    @Test
    void savesTypedPolicyUsingScopeProjectAndKnowledgeKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        JdbcKnowledgeRetrievalPolicyRepository repository = new JdbcKnowledgeRetrievalPolicyRepository(provider);
        KnowledgeRetrievalPolicyKey key = new KnowledgeRetrievalPolicyKey(
                KnowledgeScope.PROJECT, "project-1", "ops-kb");

        var saved = repository.save(key, policy());

        assertEquals(key, saved.key());
        assertEquals(4096, saved.policy().maxSegmentChars());
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_knowledge_retrieval_policy")
                                && sql.contains("ON DUPLICATE KEY UPDATE")),
                any(Object[].class));
    }

    private KnowledgeRetrievalPolicy policy() {
        return new KnowledgeRetrievalPolicy(4096, 256, 8, true, "embed-v2", "{}");
    }
}
