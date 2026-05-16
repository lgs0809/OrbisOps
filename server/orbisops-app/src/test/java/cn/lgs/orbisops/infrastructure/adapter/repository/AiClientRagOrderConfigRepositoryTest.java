package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientRagOrderDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientRagOrder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientRagOrderConfigRepositoryTest {

    @Test
    void persistsAndRestoresTypedKnowledgeBaseDefinition() {
        IAiClientRagOrderDao dao = mock(IAiClientRagOrderDao.class);
        AiClientRagOrderConfigRepository repository = repository(dao);
        RagOrderDefinition definition = definition();
        when(dao.insert(any(AiClientRagOrder.class))).thenReturn(1);
        when(dao.queryByRagId("rag-1")).thenReturn(row());
        when(dao.queryByKnowledgeTag("ops")).thenReturn(List.of(row()));

        assertTrue(repository.insert(definition));
        assertEquals(definition, repository.queryByRagId("rag-1"));
        assertEquals(List.of(definition), repository.queryByKnowledgeTag("ops"));

        ArgumentCaptor<AiClientRagOrder> saved = ArgumentCaptor.forClass(AiClientRagOrder.class);
        verify(dao).insert(saved.capture());
        assertEquals("rag-1", saved.getValue().getRagId());
        assertEquals("ops", saved.getValue().getKnowledgeTag());
    }

    @Test
    void createsNormalizedTagOrderThroughTypedPort() {
        IAiClientRagOrderDao dao = mock(IAiClientRagOrderDao.class);
        AiClientRagOrderConfigRepository repository = repository(dao);

        repository.create("  运维知识库  ", "  ops  ");

        ArgumentCaptor<AiClientRagOrder> saved = ArgumentCaptor.forClass(AiClientRagOrder.class);
        verify(dao).insert(saved.capture());
        assertTrue(saved.getValue().getRagId().startsWith("RAG_"));
        assertEquals(20, saved.getValue().getRagId().length());
        assertEquals("运维知识库", saved.getValue().getRagName());
        assertEquals("ops", saved.getValue().getKnowledgeTag());
        assertEquals(1, saved.getValue().getStatus());
    }

    @Test
    void tagOrderValidationRemainsFailClosed() {
        AiClientRagOrderConfigRepository repository = repository(mock(IAiClientRagOrderDao.class));

        assertEquals("RAG_NAME_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> repository.create(" ", "ops"))
                        .getMessage());
        assertEquals("RAG_KNOWLEDGE_TAG_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> repository.create("name", " "))
                        .getMessage());
    }

    @Test
    void degradesToEmptyCatalogWhenDaoIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiClientRagOrderDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiClientRagOrderConfigRepository repository = new AiClientRagOrderConfigRepository(provider);

        assertFalse(repository.insert(definition()));
        assertFalse(repository.updateByRagId(definition()));
        assertFalse(repository.deleteByRagId("rag-1"));
        assertNull(repository.queryByRagId("rag-1"));
        assertTrue(repository.queryEnabled().isEmpty());
        assertTrue(repository.queryByKnowledgeTag("ops").isEmpty());
        assertTrue(repository.queryAll().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private AiClientRagOrderConfigRepository repository(IAiClientRagOrderDao dao) {
        ObjectProvider<IAiClientRagOrderDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dao);
        return new AiClientRagOrderConfigRepository(provider);
    }

    private RagOrderDefinition definition() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 30, 10, 0);
        return new RagOrderDefinition(7L, "rag-1", "运维知识库", "ops", 1, time, time);
    }

    private AiClientRagOrder row() {
        RagOrderDefinition definition = definition();
        return AiClientRagOrder.builder()
                .id(definition.id())
                .ragId(definition.ragId())
                .ragName(definition.ragName())
                .knowledgeTag(definition.knowledgeTag())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
