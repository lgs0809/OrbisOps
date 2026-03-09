package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.mcp.ManageMcpTemplateUseCase;
import cn.lgs.orbisops.application.mcp.QueryMcpTemplateUseCase;
import cn.lgs.orbisops.application.project.ManageProjectWorkspaceUseCase;
import cn.lgs.orbisops.application.project.QueryProjectWorkspaceUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsMcpTemplateAdminController {

    private final ManageMcpTemplateUseCase manageTemplates;
    private final QueryMcpTemplateUseCase queryTemplates;
    private final ManageProjectWorkspaceUseCase manageProjects;
    private final QueryProjectWorkspaceUseCase queryProjects;

    public OpsMcpTemplateAdminController(ManageMcpTemplateUseCase manageTemplates,
                                         QueryMcpTemplateUseCase queryTemplates,
                                         ManageProjectWorkspaceUseCase manageProjects,
                                         QueryProjectWorkspaceUseCase queryProjects) {
        this.manageTemplates = manageTemplates;
        this.queryTemplates = queryTemplates;
        this.manageProjects = manageProjects;
        this.queryProjects = queryProjects;
    }

    @GetMapping("/mcp-templates")
    public Response<List<Map<String, Object>>> listTemplates() {
        return handle("查询 MCP 模板失败", queryTemplates::list);
    }

    @PostMapping("/mcp-templates")
    public Response<Map<String, Object>> createTemplate(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("创建 MCP 模板失败",
                () -> manageTemplates.create(safe(request), actor(servletRequest)));
    }

    @PutMapping("/mcp-templates/{templateId}")
    public Response<Map<String, Object>> updateTemplate(
            @PathVariable("templateId") String templateId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新 MCP 模板失败",
                () -> manageTemplates.update(templateId, safe(request), actor(servletRequest)));
    }

    @PatchMapping("/mcp-templates/{templateId}/status")
    public Response<Map<String, Object>> updateTemplateStatus(
            @PathVariable("templateId") String templateId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新 MCP 模板状态失败", () -> manageTemplates.updateStatus(
                templateId,
                String.valueOf(safe(request).getOrDefault("status", "DISABLED")),
                actor(servletRequest)));
    }

    @PostMapping("/mcp-templates/{templateId}/copy")
    public Response<Map<String, Object>> copyTemplate(
            @PathVariable("templateId") String templateId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("复制 MCP 模板失败",
                () -> manageTemplates.copy(templateId, safe(request), actor(servletRequest)));
    }

    @GetMapping("/mcp-templates/{templateId}/generated-tools")
    public Response<List<Map<String, Object>>> generatedTools(@PathVariable("templateId") String templateId) {
        return handle("查询 MCP 模板生成记录失败", () -> queryTemplates.generatedTools(templateId));
    }

    @GetMapping("/projects/{projectId}/tools")
    public Response<List<Map<String, Object>>> listProjectTools(@PathVariable("projectId") String projectId) {
        return handle("查询项目工具失败", () -> queryProjects.projectTools(projectId));
    }

    @PostMapping("/projects/{projectId}/tools/generate-from-template")
    public Response<Map<String, Object>> generateProjectTool(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("生成项目工具失败", () -> {
            Map<String, Object> safe = safe(request);
            String templateId = String.valueOf(safe.getOrDefault("templateId",
                    safe.getOrDefault("mcpTemplateId", "")));
            return manageTemplates.generateProjectTool(
                    projectId, templateId, safe, actor(servletRequest));
        });
    }

    @PutMapping("/projects/{projectId}/tools/{toolId}")
    public Response<Map<String, Object>> updateProjectTool(
            @PathVariable("projectId") String projectId,
            @PathVariable("toolId") String toolId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新项目工具失败",
                () -> manageProjects.updateProjectTool(
                        projectId, toolId, safe(request), actor(servletRequest)));
    }

    @PatchMapping("/projects/{projectId}/tools/{toolId}/status")
    public Response<Map<String, Object>> updateProjectToolStatus(
            @PathVariable("projectId") String projectId,
            @PathVariable("toolId") String toolId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新项目工具状态失败", () -> manageProjects.updateProjectToolStatus(
                projectId,
                toolId,
                String.valueOf(safe(request).getOrDefault("status", "DISABLED")),
                actor(servletRequest)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private <T> Response<T> handle(String message, Supplier<T> supplier) {
        try {
            return Response.<T>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(supplier.get())
                    .build();
        } catch (Exception e) {
            log.warn("{}：{}", message, e.getMessage());
            return Response.<T>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(message + "：" + e.getMessage())
                    .data(null)
                    .build();
        }
    }
}
