package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.AiClientToolMcpQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientToolMcpRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientToolMcpResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.config.AiClientToolMcpApplicationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * MCP 客户端配置管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-tool-mcp")
public class AiClientToolMcpAdminController {

    private final AiClientToolMcpApplicationService aiClientToolMcpApplicationService;

    public AiClientToolMcpAdminController(AiClientToolMcpApplicationService aiClientToolMcpApplicationService) {
        this.aiClientToolMcpApplicationService = aiClientToolMcpApplicationService;
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientToolMcp(@RequestBody AiClientToolMcpRequestDTO request) {
        return handleBoolean("创建MCP客户端配置失败", () -> aiClientToolMcpApplicationService.create(request));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientToolMcpById(@RequestBody AiClientToolMcpRequestDTO request) {
        if (request == null || request.getId() == null) {
            return illegalParameter("ID不能为空");
        }
        return handleBoolean("根据ID更新MCP客户端配置失败", () -> aiClientToolMcpApplicationService.updateById(request));
    }

    @PutMapping("/update-by-mcp-id")
    public Response<Boolean> updateAiClientToolMcpByMcpId(@RequestBody AiClientToolMcpRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getMcpId())) {
            return illegalParameter("MCP ID不能为空");
        }
        return handleBoolean("根据MCP ID更新MCP客户端配置失败", () -> aiClientToolMcpApplicationService.updateByMcpId(request));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientToolMcpById(@PathVariable("id") Long id) {
        return handleBoolean("根据ID删除MCP客户端配置失败", () -> aiClientToolMcpApplicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-mcp-id/{mcpId}")
    public Response<Boolean> deleteAiClientToolMcpByMcpId(@PathVariable("mcpId") String mcpId) {
        return handleBoolean("根据MCP ID删除MCP客户端配置失败", () -> aiClientToolMcpApplicationService.deleteByMcpId(mcpId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientToolMcpResponseDTO> queryAiClientToolMcpById(@PathVariable("id") Long id) {
        return handleItem("根据ID查询MCP客户端配置失败", () -> aiClientToolMcpApplicationService.queryById(id));
    }

    @GetMapping("/query-by-mcp-id/{mcpId}")
    public Response<AiClientToolMcpResponseDTO> queryAiClientToolMcpByMcpId(@PathVariable("mcpId") String mcpId) {
        return handleItem("根据MCP ID查询MCP客户端配置失败", () -> aiClientToolMcpApplicationService.queryByMcpId(mcpId));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientToolMcpResponseDTO>> queryAllAiClientToolMcps() {
        return handleList("查询所有MCP客户端配置失败", aiClientToolMcpApplicationService::queryAll);
    }

    @GetMapping("/query-by-status/{status}")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpsByStatus(@PathVariable("status") Integer status) {
        return handleList("根据状态查询MCP客户端配置失败", () -> aiClientToolMcpApplicationService.queryByStatus(status));
    }

    @GetMapping("/query-by-transport-type/{transportType}")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpsByTransportType(@PathVariable("transportType") String transportType) {
        return handleList("根据传输类型查询MCP客户端配置失败", () -> aiClientToolMcpApplicationService.queryByTransportType(transportType));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientToolMcpResponseDTO>> queryEnabledAiClientToolMcps() {
        return handleList("查询启用的MCP客户端配置失败", aiClientToolMcpApplicationService::queryEnabled);
    }

    @PostMapping("/query-list")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpList(@RequestBody(required = false) AiClientToolMcpQueryRequestDTO request) {
        return handleList("根据查询条件查询MCP客户端配置列表失败", () -> aiClientToolMcpApplicationService.queryList(request));
    }

    private Response<Boolean> handleBoolean(String errorMessage, Supplier<Boolean> action) {
        try {
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    private Response<AiClientToolMcpResponseDTO> handleItem(String errorMessage, Supplier<AiClientToolMcpResponseDTO> action) {
        try {
            return Response.<AiClientToolMcpResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<AiClientToolMcpResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    private Response<List<AiClientToolMcpResponseDTO>> handleList(String errorMessage, Supplier<List<AiClientToolMcpResponseDTO>> action) {
        try {
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    private Response<Boolean> illegalParameter(String message) {
        return Response.<Boolean>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .data(false)
                .build();
    }
}
