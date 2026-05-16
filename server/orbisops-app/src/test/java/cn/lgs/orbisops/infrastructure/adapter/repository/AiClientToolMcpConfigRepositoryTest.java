package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientToolMcpDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientToolMcp;
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

class AiClientToolMcpConfigRepositoryTest {

    @Test
    void persistsAndRestoresTypedMcpDefinition() {
        IAiClientToolMcpDao dao = mock(IAiClientToolMcpDao.class);
        AiClientToolMcpConfigRepository repository = repository(dao);
        McpClientDefinition definition = definition();
        when(dao.insert(any(AiClientToolMcp.class))).thenReturn(1);
        when(dao.queryByMcpId("ops-mcp")).thenReturn(row());
        when(dao.queryEnabledMcps()).thenReturn(List.of(row()));

        assertTrue(repository.insert(definition));
        assertEquals(definition, repository.findByMcpId("ops-mcp"));
        assertEquals(List.of(definition), repository.listEnabled());

        ArgumentCaptor<AiClientToolMcp> saved = ArgumentCaptor.forClass(AiClientToolMcp.class);
        verify(dao).insert(saved.capture());
        assertEquals("ops-mcp", saved.getValue().getMcpId());
        assertEquals("stdio", saved.getValue().getTransportType());
        assertEquals("{\"command\":\"node\"}", saved.getValue().getTransportConfig());
    }

    @Test
    void degradesToEmptyCatalogWhenDaoIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiClientToolMcpDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiClientToolMcpConfigRepository repository = new AiClientToolMcpConfigRepository(provider);

        assertFalse(repository.insert(definition()));
        assertFalse(repository.updateByMcpId(definition()));
        assertFalse(repository.deleteByMcpId("ops-mcp"));
        assertNull(repository.findByMcpId("ops-mcp"));
        assertTrue(repository.listAll().isEmpty());
        assertTrue(repository.listByStatus(1).isEmpty());
        assertTrue(repository.listEnabled().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private AiClientToolMcpConfigRepository repository(IAiClientToolMcpDao dao) {
        ObjectProvider<IAiClientToolMcpDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dao);
        return new AiClientToolMcpConfigRepository(provider);
    }

    private McpClientDefinition definition() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 30, 7, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 30, 7, 30);
        return new McpClientDefinition(
                7L, "ops-mcp", "Ops MCP", "stdio", "{\"command\":\"node\"}",
                30, 1, created, updated);
    }

    private AiClientToolMcp row() {
        McpClientDefinition definition = definition();
        return AiClientToolMcp.builder()
                .id(definition.id())
                .mcpId(definition.mcpId())
                .mcpName(definition.mcpName())
                .transportType(definition.transportType())
                .transportConfig(definition.transportConfig())
                .requestTimeout(definition.requestTimeout())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
