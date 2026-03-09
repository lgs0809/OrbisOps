package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.project.ManageProjectWorkspaceUseCase;
import cn.lgs.orbisops.application.project.QueryProjectWorkspaceUseCase;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务系统、资源接入和项目级 MCP 生成接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops-projects")
public class OpsProjectWorkspaceAdminController {

    private final ManageProjectWorkspaceUseCase manageProjects;
    private final QueryProjectWorkspaceUseCase queryProjects;

    public OpsProjectWorkspaceAdminController(ManageProjectWorkspaceUseCase manageProjects,
                                              QueryProjectWorkspaceUseCase queryProjects) {
        this.manageProjects = manageProjects;
        this.queryProjects = queryProjects;
    }

    @GetMapping("/snapshot")
    public Response<Map<String, Object>> snapshot() {
        return item("查询业务系统空间失败", queryProjects::snapshot);
    }

    @GetMapping("/templates")
    public Response<List<Map<String, Object>>> templates() {
        return list("查询资源模板失败", queryProjects::templates);
    }

    @PostMapping("/projects")
    public Response<Map<String, Object>> createProject(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return item("创建项目失败", () -> manageProjects.createProject(
                safe(request), currentOperator(servletRequest)));
    }

    @PostMapping("/projects/update")
    public Response<Map<String, Object>> updateProject(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return item("更新项目失败", () -> manageProjects.updateProject(
                safe(request), currentOperator(servletRequest)));
    }

    @PostMapping("/projects/{projectId}/default-agent/ensure")
    public Response<Map<String, Object>> ensureDefaultAgent(
            @PathVariable("projectId") String projectId,
            HttpServletRequest servletRequest) {
        return item("修复项目默认 Agent 失败", () -> manageProjects.ensureDefaultAgent(
                projectId, currentOperator(servletRequest)));
    }

    @GetMapping("/projects/{projectId}/members")
    public Response<List<Map<String, Object>>> projectMembers(@PathVariable("projectId") String projectId) {
        return list("查询项目成员失败", () -> queryProjects.members(projectId));
    }

    @PutMapping("/projects/{projectId}/members")
    public Response<List<Map<String, Object>>> replaceProjectMembers(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return list("更新项目成员失败", () -> {
            List<Map<String, Object>> members = listOfMaps(safe(request).get("members"));
            return manageProjects.replaceMembers(projectId, members, currentOperator(servletRequest));
        });
    }

    @PostMapping("/resources")
    public Response<Map<String, Object>> addResource(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return item("添加数据连接失败", () -> manageProjects.addResource(
                safe(request), currentOperator(servletRequest)));
    }

    @PostMapping("/resources/update")
    public Response<Map<String, Object>> updateResource(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return item("更新数据连接失败", () -> manageProjects.updateResource(
                safe(request), currentOperator(servletRequest)));
    }

    @PostMapping("/resources/permission")
    public Response<Map<String, Object>> updatePermission(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return item("更新数据连接权限失败", () -> manageProjects.updatePermission(
                safe(request), currentOperator(servletRequest)));
    }

    @PostMapping("/mcps/generate")
    public Response<Map<String, Object>> generateMcp(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return item("生成项目 MCP 失败", () -> manageProjects.generateMcp(
                safe(request), currentOperator(servletRequest)));
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<?, ?> source = (Map<?, ?>) item;
                    Map<String, Object> target = new java.util.LinkedHashMap<>();
                    source.forEach((key, rawValue) -> target.put(String.valueOf(key), rawValue));
                    return target;
                })
                .toList();
    }

    private String currentOperator(HttpServletRequest request) {
        Object principal = request == null
                ? null
                : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (principal instanceof AdminAuthService.AuthPrincipal authPrincipal) {
            return authPrincipal.username();
        }
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private Response<Map<String, Object>> item(String errorMessage, Supplier<Map<String, Object>> action) {
        try {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(errorMessage + "：" + e.getMessage())
                    .data(Map.of())
                    .build();
        }
    }

    private Response<List<Map<String, Object>>> list(String errorMessage, Supplier<List<Map<String, Object>>> action) {
        try {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(action.get())
                    .build();
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(errorMessage + "：" + e.getMessage())
                    .data(List.of())
                    .build();
        }
    }
}
