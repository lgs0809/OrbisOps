package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientModelDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientModelConfigRepositoryTest {

    @Test
    void persistsAndRestoresTypedModelDefinition() {
        IAiClientModelDao dao = mock(IAiClientModelDao.class);
        AiClientModelConfigRepository repository = repository(dao);
        AiClientModelDefinition definition = definition();
        when(dao.insert(any(AiClientModel.class))).thenReturn(1);
        when(dao.queryByModelId("gpt-main")).thenReturn(row());

        assertTrue(repository.insert(definition));
        assertEquals(definition, repository.findByModelId("gpt-main"));

        ArgumentCaptor<AiClientModel> saved = ArgumentCaptor.forClass(AiClientModel.class);
        verify(dao).insert(saved.capture());
        assertEquals("gpt-main", saved.getValue().getModelId());
        assertEquals("CHAT", saved.getValue().getModelUsage());
    }

    @Test
    void degradesToEmptyCatalogWhenDaoIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiClientModelDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiClientModelConfigRepository repository = new AiClientModelConfigRepository(provider);

        assertFalse(repository.insert(definition()));
        assertFalse(repository.updateByModelId(definition()));
        assertFalse(repository.deleteByModelId("gpt-main"));
        assertNull(repository.findByModelId("gpt-main"));
        assertTrue(repository.findByApiId("openai-main").isEmpty());
        assertTrue(repository.listEnabled().isEmpty());
    }

    private AiClientModelConfigRepository repository(IAiClientModelDao dao) {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiClientModelDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dao);
        return new AiClientModelConfigRepository(provider);
    }

    private AiClientModelDefinition definition() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 30, 5, 0);
        return new AiClientModelDefinition(
                7L, "gpt-main", "openai-main", "GPT Main", "CHAT", "CHAT",
                "primary", 1, time, time);
    }

    private AiClientModel row() {
        AiClientModelDefinition definition = definition();
        return AiClientModel.builder()
                .id(definition.id())
                .modelId(definition.modelId())
                .apiId(definition.apiId())
                .modelName(definition.modelName())
                .modelType(definition.modelType())
                .modelUsage(definition.modelUsage())
                .description(definition.description())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
