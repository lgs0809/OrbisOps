package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientApiDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientApiConfigRepositoryTest {

    @Test
    void persistsAndRestoresTypedApiDefinition() {
        IAiClientApiDao dao = mock(IAiClientApiDao.class);
        AiClientApiConfigRepository repository = repository(dao);
        AiClientApiDefinition definition = definition();
        when(dao.insert(any(AiClientApi.class))).thenReturn(1);
        when(dao.queryByApiId("openai-main")).thenReturn(row());

        assertTrue(repository.insert(definition));
        assertEquals(definition, repository.findByApiId("openai-main"));

        ArgumentCaptor<AiClientApi> saved = ArgumentCaptor.forClass(AiClientApi.class);
        verify(dao).insert(saved.capture());
        assertEquals("openai-main", saved.getValue().getApiId());
        assertEquals("configured-api-key", saved.getValue().getApiKey());
    }

    @Test
    void degradesToEmptyCatalogWhenDaoIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiClientApiDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiClientApiConfigRepository repository = new AiClientApiConfigRepository(provider);

        assertFalse(repository.insert(definition()));
        assertFalse(repository.updateById(definition()));
        assertFalse(repository.deleteByApiId("openai-main"));
        assertNull(repository.findByApiId("openai-main"));
        assertTrue(repository.listEnabled().isEmpty());
        assertTrue(repository.listAll().isEmpty());
    }

    private AiClientApiConfigRepository repository(IAiClientApiDao dao) {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiClientApiDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dao);
        return new AiClientApiConfigRepository(provider);
    }

    private AiClientApiDefinition definition() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 30, 3, 0);
        return new AiClientApiDefinition(
                7L, "openai-main", "OpenAI", "OPENAI_COMPATIBLE",
                "https://example.test/v1", "configured-api-key",
                "/chat/completions", "/embeddings", 1, time, time);
    }

    private AiClientApi row() {
        AiClientApiDefinition definition = definition();
        return AiClientApi.builder()
                .id(definition.id())
                .apiId(definition.apiId())
                .providerName(definition.providerName())
                .providerType(definition.providerType())
                .baseUrl(definition.baseUrl())
                .apiKey(definition.apiKey())
                .completionsPath(definition.completionsPath())
                .embeddingsPath(definition.embeddingsPath())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
