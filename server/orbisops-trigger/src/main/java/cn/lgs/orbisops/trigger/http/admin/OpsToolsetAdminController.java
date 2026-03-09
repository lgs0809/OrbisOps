package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.toolset.ToolsetApplicationService;
import cn.lgs.orbisops.domain.toolset.model.ToolsetDefinition;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsToolsetAdminController {

    private final ToolsetApplicationService toolsets;

    public OpsToolsetAdminController(ToolsetApplicationService toolsets) {
        this.toolsets = toolsets;
    }

    @GetMapping("/toolsets/built-in")
    public Response<List<ToolsetDefinition>> builtInToolsets() {
        return success(toolsets.builtIn());
    }

    @GetMapping("/projects/{projectId}/toolsets")
    public Response<List<ToolsetDefinition>> effectiveToolsets(
            @PathVariable("projectId") String projectId,
            HttpServletRequest servletRequest) {
        return success(toolsets.effective(projectId, actor(servletRequest)));
    }

    @GetMapping("/projects/{projectId}/toolsets/custom")
    public Response<List<ToolsetDefinition>> customToolsets(@PathVariable("projectId") String projectId) {
        return success(toolsets.custom(projectId));
    }

    @PostMapping("/projects/{projectId}/toolsets/custom")
    public Response<ToolsetDefinition> registerCustomToolset(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return success(toolsets.registerCustom(projectId, safe(request), actor(servletRequest)));
    }

    @PatchMapping("/projects/{projectId}/toolsets/{toolsetId}/enable")
    public Response<Map<String, Object>> enableToolset(
            @PathVariable("projectId") String projectId,
            @PathVariable("toolsetId") String toolsetId,
            HttpServletRequest servletRequest) {
        return success(toolsets.setEnabled(projectId, toolsetId, true, actor(servletRequest)));
    }

    @PatchMapping("/projects/{projectId}/toolsets/{toolsetId}/disable")
    public Response<Map<String, Object>> disableToolset(
            @PathVariable("projectId") String projectId,
            @PathVariable("toolsetId") String toolsetId,
            HttpServletRequest servletRequest) {
        return success(toolsets.setEnabled(projectId, toolsetId, false, actor(servletRequest)));
    }

    @PostMapping("/projects/{projectId}/toolsets/{toolsetId}/refresh-mcp")
    public Response<Map<String, Object>> refreshMcpToolset(
            @PathVariable("projectId") String projectId,
            @PathVariable("toolsetId") String toolsetId,
            HttpServletRequest servletRequest) {
        return success(toolsets.refreshMcp(projectId, toolsetId, actor(servletRequest)));
    }

    @PostMapping("/tool-executions")
    public Response<Map<String, Object>> executeTool(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return success(toolsets.execute(safe(request), actor(servletRequest)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
        throw new SecurityException("未获取到已认证操作者");
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
