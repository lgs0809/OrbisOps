package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionDTO;
import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionRequestDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceFileDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceSearchHitDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.source.OpsProjectServiceCatalogService;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops/source-repositories")
public class OpsSourceRepositoryAdminController {

    private final OpsSourceRepositoryService repositories;
    private final OpsProjectServiceCatalogService services;

    public OpsSourceRepositoryAdminController(
            OpsSourceRepositoryService repositories,
            OpsProjectServiceCatalogService services) {
        this.repositories = repositories;
        this.services = services;
    }

    @GetMapping("/capabilities")
    public Response<Map<String, Object>> capabilities() {
        return success(repositories.capabilities());
    }

    @PostMapping
    public Response<OpsSourceRepositoryDTO> registerRepository(
            @RequestBody OpsSourceRepositoryRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("登记代码仓库失败",
                () -> repositories.registerRepository(request, actor(servletRequest)));
    }

    @GetMapping
    public Response<List<OpsSourceRepositoryDTO>> listRepositories(
            @RequestParam(value = "projectId", required = false) String projectId) {
        return handle("查询代码仓库失败", () -> repositories.listRepositories(projectId));
    }

    @GetMapping("/services/capabilities")
    public Response<Map<String, Object>> serviceCapabilities() {
        return success(services.capabilities());
    }

    @PostMapping("/services")
    public Response<OpsProjectServiceDTO> upsertService(
            @RequestBody OpsProjectServiceRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("保存项目服务失败", () -> services.upsert(request, actor(servletRequest)));
    }

    @GetMapping("/services")
    public Response<List<OpsProjectServiceDTO>> listServices(
            @RequestParam(value = "projectId", required = false) String projectId) {
        return handle("查询项目服务失败", () -> services.list(projectId));
    }

    @PostMapping("/deployments")
    public Response<OpsDeploymentRevisionDTO> recordDeployment(
            @RequestBody OpsDeploymentRevisionRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("记录部署版本失败",
                () -> repositories.recordDeployment(request, actor(servletRequest)));
    }

    @GetMapping("/deployments")
    public Response<List<OpsDeploymentRevisionDTO>> listDeployments(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "environment", required = false) String environment) {
        return handle("查询部署版本失败",
                () -> repositories.listDeployments(projectId, environment));
    }

    @GetMapping("/deployments/resolve")
    public Response<OpsDeploymentRevisionDTO> resolveDeployment(
            @RequestParam("projectId") String projectId,
            @RequestParam("environment") String environment,
            @RequestParam("serviceName") String serviceName) {
        return handle("解析部署版本失败",
                () -> repositories.resolveDeployment(projectId, environment, serviceName).orElse(null));
    }

    @GetMapping("/file")
    public Response<OpsSourceFileDTO> readFile(
            @RequestParam("projectId") String projectId,
            @RequestParam("repositoryId") String repositoryId,
            @RequestParam(value = "revision", required = false) String revision,
            @RequestParam("path") String path) {
        return handle("读取代码文件失败",
                () -> repositories.readFile(projectId, repositoryId, revision, path));
    }

    @GetMapping("/search")
    public Response<List<OpsSourceSearchHitDTO>> search(
            @RequestParam("projectId") String projectId,
            @RequestParam("repositoryId") String repositoryId,
            @RequestParam(value = "revision", required = false) String revision,
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return handle("检索代码失败", () -> repositories.search(
                projectId, repositoryId, revision, query, Optional.ofNullable(limit).orElse(50)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private <T> Response<T> handle(String message, Supplier<T> action) {
        try {
            return success(action.get());
        } catch (Exception e) {
            log.warn("{}：{}", message, e.getMessage());
            return Response.<T>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(message + "：" + e.getMessage())
                    .data(null)
                    .build();
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
