package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.api.dto.OpsExecutionResourceRequestDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateApplicationService;
import cn.lgs.orbisops.trigger.application.execution.OpsExecutionAdapterTemplateCommandMapper;
import cn.lgs.orbisops.trigger.application.execution.OpsExecutionResourceAdapter;
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
public class OpsExecutionAdapterTemplateAdminController {

    private final ExecutionAdapterTemplateApplicationService<OpsExecutionResourceDTO> templates;
    private final OpsExecutionResourceAdapter resources;
    private final OpsExecutionAdapterTemplateCommandMapper commandMapper;

    public OpsExecutionAdapterTemplateAdminController(
            ExecutionAdapterTemplateApplicationService<OpsExecutionResourceDTO> templates,
            OpsExecutionResourceAdapter resources,
            OpsExecutionAdapterTemplateCommandMapper commandMapper) {
        this.templates = templates;
        this.resources = resources;
        this.commandMapper = commandMapper;
    }

    @GetMapping("/execution-adapter-templates")
    public Response<List<Map<String, Object>>> listTemplates() {
        return handle("查询执行适配器模板失败", templates::list);
    }

    @PostMapping("/execution-adapter-templates")
    public Response<Map<String, Object>> createTemplate(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("创建执行适配器模板失败", () -> templates.create(
                commandMapper.mutation(request, actor(servletRequest))));
    }

    @PutMapping("/execution-adapter-templates/{templateId}")
    public Response<Map<String, Object>> updateTemplate(
            @PathVariable("templateId") String templateId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新执行适配器模板失败", () -> templates.update(
                templateId, commandMapper.mutation(request, actor(servletRequest))));
    }

    @PatchMapping("/execution-adapter-templates/{templateId}/status")
    public Response<Map<String, Object>> updateTemplateStatus(
            @PathVariable("templateId") String templateId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新执行适配器模板状态失败", () -> templates.updateStatus(
                templateId, commandMapper.status(request, actor(servletRequest))));
    }

    @PostMapping("/execution-adapter-templates/{templateId}/copy")
    public Response<Map<String, Object>> copyTemplate(
            @PathVariable("templateId") String templateId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("复制执行适配器模板失败", () -> templates.copy(
                templateId, commandMapper.mutation(request, actor(servletRequest))));
    }

    @GetMapping("/execution-adapter-templates/{templateId}/generated-targets")
    public Response<List<Map<String, Object>>> generatedTargets(@PathVariable("templateId") String templateId) {
        return handle("查询执行适配器模板生成记录失败", () -> templates.generatedTargets(templateId));
    }

    @GetMapping("/projects/{projectId}/execution-targets")
    public Response<List<OpsExecutionResourceDTO>> listProjectExecutionTargets(
            @PathVariable("projectId") String projectId) {
        return handle("查询项目执行目标失败", () -> resources.list(projectId));
    }

    @PostMapping("/projects/{projectId}/execution-targets/generate-from-template")
    public Response<OpsExecutionResourceDTO> generateProjectExecutionTarget(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("生成项目执行目标失败", () -> templates.generateTarget(
                commandMapper.target(projectId, request, actor(servletRequest))));
    }

    @PutMapping("/projects/{projectId}/execution-targets/{targetId}")
    public Response<OpsExecutionResourceDTO> updateProjectExecutionTarget(
            @PathVariable("projectId") String projectId,
            @PathVariable("targetId") String targetId,
            @RequestBody OpsExecutionResourceRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("更新项目执行目标失败", () -> resources.upsertForIdentity(
                projectId, targetId, request, actor(servletRequest)));
    }

    @PatchMapping("/projects/{projectId}/execution-targets/{targetId}/status")
    public Response<OpsExecutionResourceDTO> updateProjectExecutionTargetStatus(
            @PathVariable("projectId") String projectId,
            @PathVariable("targetId") String targetId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新项目执行目标状态失败", () -> resources.updateStatus(
                projectId, targetId,
                String.valueOf(safe(request).getOrDefault("status", "DISABLED")),
                actor(servletRequest)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
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
