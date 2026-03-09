package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.ops.OpsAgentDefinitionApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * 运维 Agent 定义、版本和发布管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/ops-agents")
public class OpsAgentDefinitionAdminController {

    private final OpsAgentDefinitionApplicationService opsAgentDefinitionApplicationService;

    public OpsAgentDefinitionAdminController(OpsAgentDefinitionApplicationService opsAgentDefinitionApplicationService) {
        this.opsAgentDefinitionApplicationService = opsAgentDefinitionApplicationService;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> listAgents(
            @RequestParam("projectId") String projectId) {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.listAgents(projectId))
                .build();
    }

    @GetMapping("/{agentId}")
    public Response<Map<String, Object>> getAgent(@PathVariable("agentId") String agentId) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.getAgent(agentId))
                .build();
    }

    @GetMapping("/projects/{projectId}/agent-capabilities")
    public Response<Map<String, Object>> projectAgentCapabilities(@PathVariable("projectId") String projectId) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.projectAgentCapabilities(projectId))
                .build();
    }

    @PostMapping("/{agentId}/validate-bindings")
    public Response<Map<String, Object>> validateAgentBindings(@PathVariable("agentId") String agentId,
                                                               @RequestBody OpsAgentDefinition definition) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.validateBindings(agentId, definition))
                .build();
    }

    @GetMapping("/{agentId}/bindings")
    public Response<List<Map<String, Object>>> listAgentBindings(@PathVariable("agentId") String agentId) {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.agentBindings(agentId))
                .build();
    }

    @PutMapping("/{agentId}/bindings")
    public Response<Map<String, Object>> updateAgentBindings(@PathVariable("agentId") String agentId,
                                                             @RequestBody Map<String, Object> request) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.updateAgentBindings(agentId, request))
                .build();
    }

    @PostMapping
    public Response<Map<String, Object>> saveAgent(@RequestBody OpsAgentDefinition definition) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.saveAgent(definition))
                .build();
    }

    @PostMapping("/drafts")
    public Response<Map<String, Object>> saveAgentDraft(@RequestBody OpsAgentDefinition definition) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.saveDraft(definition))
                .build();
    }

    @PostMapping("/{agentId}/clone")
    public Response<Map<String, Object>> cloneAgent(@PathVariable("agentId") String agentId,
                                                    @RequestBody Map<String, String> request) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.cloneAgent(
                        agentId,
                        request.get("projectId"),
                        request.get("agentId"),
                        request.get("name")))
                .build();
    }

    @GetMapping("/{agentId}/versions")
    public Response<List<Map<String, Object>>> listAgentVersions(@PathVariable("agentId") String agentId) {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.versions(agentId))
                .build();
    }

    @PostMapping("/{agentId}/versions/{version}/validate")
    public Response<Map<String, Object>> validateAgentVersion(@PathVariable("agentId") String agentId,
                                                              @PathVariable("version") Integer version) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.validateVersion(agentId, version))
                .build();
    }

    @PostMapping("/{agentId}/versions/{version}/publish")
    public Response<Map<String, Object>> publishAgentVersion(@PathVariable("agentId") String agentId,
                                                             @PathVariable("version") Integer version,
                                                             HttpServletRequest servletRequest) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.publishVersion(agentId, version, actor(servletRequest)))
                .build();
    }

    @PostMapping("/{agentId}/eval-suites")
    public Response<Map<String, Object>> createEvalSuite(@PathVariable("agentId") String agentId,
                                                         @RequestBody Map<String, Object> request,
                                                         HttpServletRequest servletRequest) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.createEvalSuite(
                        String.valueOf(request.get("projectId")), agentId, request,
                        actor(servletRequest)))
                .build();
    }

    @PostMapping("/{agentId}/versions/{version}/eval-runs")
    public Response<Map<String, Object>> runEval(@PathVariable("agentId") String agentId,
                                                 @PathVariable("version") Integer version,
                                                 @RequestBody Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.runEval(
                        String.valueOf(request.get("projectId")), agentId, version,
                        String.valueOf(request.get("suiteId")),
                        actor(servletRequest)))
                .build();
    }

    @PostMapping("/{agentId}/versions/{version}/rollback")
    public Response<Map<String, Object>> rollbackAgentVersion(@PathVariable("agentId") String agentId,
                                                              @PathVariable("version") Integer version) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.rollbackVersion(agentId, version))
                .build();
    }

    @PostMapping("/{agentId}/versions/{version}/disable")
    public Response<Boolean> disableAgentVersion(@PathVariable("agentId") String agentId,
                                                 @PathVariable("version") Integer version) {
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.disableVersion(agentId, version))
                .build();
    }

    @DeleteMapping("/{agentId}")
    public Response<Boolean> deleteAgent(@PathVariable("agentId") String agentId) {
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.deleteAgent(agentId))
                .build();
    }

    @PostMapping("/reload")
    public Response<List<Map<String, Object>>> reloadAgents() {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsAgentDefinitionApplicationService.reloadAgents())
                .build();
    }

    private String actor(HttpServletRequest request) {
        Object value = request == null ? null
                : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof AdminAuthService.AuthPrincipal principal)) {
            throw new SecurityException("未获取到已认证管理员");
        }
        return org.springframework.util.StringUtils.hasText(principal.userId())
                ? principal.userId() : principal.username();
    }
}
