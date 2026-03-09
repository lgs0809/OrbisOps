package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientToolMcpQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientToolMcpRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientToolMcpResponseDTO;
import cn.lgs.orbisops.application.config.McpClientCatalogQuery;
import cn.lgs.orbisops.application.config.McpClientCatalogUseCase;
import cn.lgs.orbisops.application.config.McpClientCommand;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientToolMcpApplicationServiceTest {

    @Test
    void updateProjectsDtoToTypedCommand() {
        McpClientCatalogUseCase useCase = mock(McpClientCatalogUseCase.class);
        AiClientToolMcpApplicationService service = new AiClientToolMcpApplicationService(useCase);
        when(useCase.updateById(any())).thenReturn(false);
        AiClientToolMcpRequestDTO request = AiClientToolMcpRequestDTO.builder()
                .id(404L)
                .mcpId("missing-mcp")
                .mcpName("Missing MCP")
                .transportType("sse")
                .transportConfig("{}")
                .requestTimeout(30)
                .status(1)
                .build();

        assertFalse(service.updateById(request));

        ArgumentCaptor<McpClientCommand> command = ArgumentCaptor.forClass(McpClientCommand.class);
        verify(useCase).updateById(command.capture());
        assertEquals(404L, command.getValue().id());
        assertEquals("missing-mcp", command.getValue().mcpId());
        assertEquals("{}", command.getValue().transportConfig());
    }

    @Test
    void queryListProjectsCompatibilitySelectors() {
        McpClientCatalogUseCase useCase = mock(McpClientCatalogUseCase.class);
        AiClientToolMcpApplicationService service = new AiClientToolMcpApplicationService(useCase);
        AiClientToolMcpQueryRequestDTO request = AiClientToolMcpQueryRequestDTO.builder()
                .mcpId("ops-mcp")
                .mcpName("Ops")
                .transportType("stdio")
                .status(1)
                .pageNum(2)
                .pageSize(10)
                .build();
        when(useCase.query(any())).thenReturn(List.of());

        service.queryList(request);

        ArgumentCaptor<McpClientCatalogQuery> query = ArgumentCaptor.forClass(McpClientCatalogQuery.class);
        verify(useCase).query(query.capture());
        assertEquals("ops-mcp", query.getValue().mcpId());
        assertEquals("Ops", query.getValue().mcpName());
        assertEquals("stdio", query.getValue().transportType());
        assertEquals(1, query.getValue().status());
    }

    @Test
    void queryProjectsTypedDefinitionToResponseDto() {
        McpClientCatalogUseCase useCase = mock(McpClientCatalogUseCase.class);
        AiClientToolMcpApplicationService service = new AiClientToolMcpApplicationService(useCase);
        McpClientDefinition definition = new McpClientDefinition(
                7L,
                "ops-mcp",
                "Ops MCP",
                "stdio",
                "protected-config",
                30,
                1,
                LocalDateTime.of(2026, 7, 30, 7, 0),
                LocalDateTime.of(2026, 7, 30, 7, 30));
        when(useCase.findById(7L)).thenReturn(definition);

        AiClientToolMcpResponseDTO response = service.queryById(7L);

        assertEquals(7L, response.getId());
        assertEquals("ops-mcp", response.getMcpId());
        assertEquals("protected-config", response.getTransportConfig());
        assertEquals(LocalDateTime.of(2026, 7, 30, 7, 30), response.getUpdateTime());
    }
}
