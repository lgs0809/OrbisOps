package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogEditor;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogReader;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill management API for operations agents.
 */
@RestController
@RequestMapping("/api/v1/admin/ops/skills")
public class OpsSkillAdminController {

    private final ObjectProvider<SkillCatalogReader> skillCatalogReader;
    private final ObjectProvider<SkillCatalogEditor> skillCatalogEditor;
    private final ObjectProvider<SkillRuntimeToolProvider> skillRuntimeToolProvider;
    private final OpsAgentDefinitionQueryGateway definitionRegistry;
    private final OpsConfigAuditService opsConfigAuditService;
    private final SkillManagementUseCase skillManagement;
    private final SkillCatalogQueryService skillCatalogQueries;

    public OpsSkillAdminController(ObjectProvider<SkillCatalogReader> skillCatalogReader,
                                   ObjectProvider<SkillCatalogEditor> skillCatalogEditor,
                                   ObjectProvider<SkillRuntimeToolProvider> skillRuntimeToolProvider,
                                   OpsAgentDefinitionQueryGateway definitionRegistry,
                                   OpsConfigAuditService opsConfigAuditService,
                                   SkillManagementUseCase skillManagement,
                                   SkillCatalogQueryService skillCatalogQueries) {
        this.skillCatalogReader = skillCatalogReader;
        this.skillCatalogEditor = skillCatalogEditor;
        this.skillRuntimeToolProvider = skillRuntimeToolProvider;
        this.definitionRegistry = definitionRegistry;
        this.opsConfigAuditService = opsConfigAuditService;
        this.skillManagement = skillManagement;
        this.skillCatalogQueries = skillCatalogQueries;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> listSkills() {
        SkillCatalogReader provider = skillCatalogReader.getIfAvailable();
        List<Map<String, Object>> data = provider == null ? List.of() : provider.loadSkills().stream()
                .map(skill -> skillView(skill, false))
                .toList();
        return success(data);
    }

    @PostMapping("/reload")
    public Response<List<Map<String, Object>>> reloadSkills() {
        return listSkills();
    }

    @GetMapping("/global")
    public Response<List<Map<String, Object>>> listGlobalSkills() {
        return success(skillCatalogQueries.listGlobalSkills());
    }

    @PostMapping("/global")
    public Response<Map<String, Object>> createGlobalSkill(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> data = skillManagement.createGlobalSkill(
                    safe(request), actor(servletRequest));
            opsConfigAuditService.record("skill", "create-global", String.valueOf(data.get("skillId")), null, data);
            return success(data);
        } catch (Exception e) {
            return failure("创建通用 Skill 失败：" + e.getMessage());
        }
    }

    @GetMapping("/global/{skillId}")
    public Response<Map<String, Object>> globalSkillDetail(@PathVariable("skillId") String skillId) {
        try {
            return success(skillCatalogQueries.getGlobalSkill(skillId));
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    @GetMapping("/global/{skillId}/artifacts")
    public Response<List<Map<String, Object>>> globalSkillArtifacts(@PathVariable("skillId") String skillId) {
        try {
            Map<String, Object> skill = skillCatalogQueries.getGlobalSkill(skillId);
            return success(skillCatalogQueries.listSkillArtifacts("", skillId,
                    number(skill.get("currentVersion"), number(skill.get("version"), 1)),
                    text(firstNonNull(skill.get("currentSkillHash"), skill.get("skillHash"))),
                    text(firstNonNull(skill.get("currentPackageHash"), skill.get("packageHash"))),
                    "GLOBAL"));
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询通用 Skill 文件失败：" + e.getMessage())
                    .data(List.of())
                    .build();
        }
    }

    @PutMapping("/global/{skillId}")
    public Response<Map<String, Object>> updateGlobalSkill(
            @PathVariable("skillId") String skillId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getGlobalSkill(skillId);
            Map<String, Object> data = skillManagement.updateGlobalSkill(
                    skillId, safe(request), actor(servletRequest));
            opsConfigAuditService.record("skill", "update-global", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("更新通用 Skill 失败：" + e.getMessage());
        }
    }

    @PatchMapping("/global/{skillId}/status")
    public Response<Map<String, Object>> updateGlobalSkillStatus(
            @PathVariable("skillId") String skillId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getGlobalSkill(skillId);
            Map<String, Object> data = skillManagement.updateGlobalStatus(
                    skillId,
                    String.valueOf(safe(request).getOrDefault("status", "DISABLED")),
                    actor(servletRequest));
            opsConfigAuditService.record("skill", "status-global", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("更新通用 Skill 状态失败：" + e.getMessage());
        }
    }

    @PatchMapping("/global/{skillId}/update-mode")
    public Response<Map<String, Object>> updateGlobalSkillMode(
            @PathVariable("skillId") String skillId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getGlobalSkill(skillId);
            Map<String, Object> data = skillManagement.updateGlobalUpdateMode(
                    skillId, safe(request), actor(servletRequest));
            opsConfigAuditService.record("skill", "update-mode-global", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("更新通用 Skill 自动更新策略失败：" + e.getMessage());
        }
    }

    @GetMapping("/global/{skillId}/versions")
    public Response<List<Map<String, Object>>> listGlobalSkillVersions(@PathVariable("skillId") String skillId) {
        try {
            return success(skillCatalogQueries.listGlobalVersions(skillId));
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询通用 Skill 版本失败：" + e.getMessage())
                    .data(List.of())
                    .build();
        }
    }

    @PostMapping("/global/{skillId}/versions/{version}/rollback")
    public Response<Map<String, Object>> rollbackGlobalSkillVersion(
            @PathVariable("skillId") String skillId,
            @PathVariable("version") Integer version,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getGlobalSkill(skillId);
            Map<String, Object> data = skillManagement.rollbackGlobalVersion(
                    skillId, version == null ? 0 : version, actor(servletRequest));
            opsConfigAuditService.record("skill", "rollback-global", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("回滚通用 Skill 失败：" + e.getMessage());
        }
    }

    @GetMapping("/global/{skillId}/usage")
    public Response<List<Map<String, Object>>> globalSkillUsage(@PathVariable("skillId") String skillId) {
        return success(references(skillId));
    }

    @GetMapping("/projects/{projectId}")
    public Response<List<Map<String, Object>>> listProjectSkills(@PathVariable("projectId") String projectId) {
        try {
            return success(skillCatalogQueries.listProjectSkills(projectId));
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询项目 Skill 失败：" + e.getMessage())
                    .data(List.of())
                    .build();
        }
    }

    @PostMapping("/projects/{projectId}")
    public Response<Map<String, Object>> createProjectSkill(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> data = skillManagement.createProjectSkill(
                    projectId, safe(request), actor(servletRequest));
            opsConfigAuditService.record(projectId, "skill", "create-project", String.valueOf(data.get("skillId")), null, data);
            return success(data);
        } catch (Exception e) {
            return failure("创建项目 Skill 失败：" + e.getMessage());
        }
    }

    @PostMapping("/projects/{projectId}/copy-from-global")
    public Response<Map<String, Object>> copyGlobalToProject(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        Map<String, Object> body = safe(request);
        String globalSkillId = String.valueOf(body.getOrDefault("globalSkillId", body.getOrDefault("skillId", "")));
        try {
            Map<String, Object> data = skillManagement.copyGlobalToProject(
                    projectId, globalSkillId, body, actor(servletRequest));
            opsConfigAuditService.record(projectId, "skill", "copy-to-project", String.valueOf(data.get("skillId")),
                    Map.of("globalSkillId", globalSkillId), data);
            return success(data);
        } catch (Exception e) {
            return failure("复制通用 Skill 到项目失败：" + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/{skillId}")
    public Response<Map<String, Object>> projectSkillDetail(@PathVariable("projectId") String projectId,
                                                            @PathVariable("skillId") String skillId) {
        try {
            return success(skillCatalogQueries.getProjectSkill(projectId, skillId));
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/{skillId}/artifacts")
    public Response<List<Map<String, Object>>> projectSkillArtifacts(@PathVariable("projectId") String projectId,
                                                                     @PathVariable("skillId") String skillId) {
        try {
            Map<String, Object> skill = skillCatalogQueries.getProjectSkill(projectId, skillId);
            return success(skillCatalogQueries.listSkillArtifacts(projectId, skillId,
                    number(skill.get("currentVersion"), number(skill.get("version"), 1)),
                    text(firstNonNull(skill.get("currentSkillHash"), skill.get("skillHash"))),
                    text(firstNonNull(skill.get("currentPackageHash"), skill.get("packageHash"))),
                    "PROJECT"));
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询项目 Skill 文件失败：" + e.getMessage())
                    .data(List.of())
                    .build();
        }
    }

    @PutMapping("/projects/{projectId}/{skillId}")
    public Response<Map<String, Object>> updateProjectSkill(
            @PathVariable("projectId") String projectId,
            @PathVariable("skillId") String skillId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getProjectSkill(projectId, skillId);
            Map<String, Object> data = skillManagement.updateProjectSkill(
                    projectId, skillId, safe(request), actor(servletRequest));
            opsConfigAuditService.record(projectId, "skill", "update-project", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("更新项目 Skill 失败：" + e.getMessage());
        }
    }

    @PatchMapping("/projects/{projectId}/{skillId}/status")
    public Response<Map<String, Object>> updateProjectSkillStatus(
            @PathVariable("projectId") String projectId,
            @PathVariable("skillId") String skillId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getProjectSkill(projectId, skillId);
            Map<String, Object> data = skillManagement.updateProjectStatus(
                    projectId,
                    skillId,
                    String.valueOf(safe(request).getOrDefault("status", "DISABLED")),
                    actor(servletRequest));
            opsConfigAuditService.record(projectId, "skill", "status-project", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("更新项目 Skill 状态失败：" + e.getMessage());
        }
    }

    @PatchMapping("/projects/{projectId}/{skillId}/update-mode")
    public Response<Map<String, Object>> updateProjectSkillMode(
            @PathVariable("projectId") String projectId,
            @PathVariable("skillId") String skillId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getProjectSkill(projectId, skillId);
            Map<String, Object> data = skillManagement.updateProjectUpdateMode(
                    projectId, skillId, safe(request), actor(servletRequest));
            opsConfigAuditService.record(projectId, "skill", "update-mode-project", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("更新项目 Skill 自动更新策略失败：" + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/{skillId}/versions")
    public Response<List<Map<String, Object>>> listProjectSkillVersions(@PathVariable("projectId") String projectId,
                                                                        @PathVariable("skillId") String skillId) {
        try {
            return success(skillCatalogQueries.listProjectVersions(projectId, skillId));
        } catch (Exception e) {
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询项目 Skill 版本失败：" + e.getMessage())
                    .data(List.of())
                    .build();
        }
    }

    @PostMapping("/projects/{projectId}/{skillId}/versions/{version}/rollback")
    public Response<Map<String, Object>> rollbackProjectSkillVersion(
            @PathVariable("projectId") String projectId,
            @PathVariable("skillId") String skillId,
            @PathVariable("version") Integer version,
            HttpServletRequest servletRequest) {
        try {
            Map<String, Object> before = skillCatalogQueries.getProjectSkill(projectId, skillId);
            Map<String, Object> data = skillManagement.rollbackProjectVersion(
                    projectId, skillId, version == null ? 0 : version, actor(servletRequest));
            opsConfigAuditService.record(projectId, "skill", "rollback-project", skillId, before, data);
            return success(data);
        } catch (Exception e) {
            return failure("回滚项目 Skill 失败：" + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/{skillId}/usage")
    public Response<List<Map<String, Object>>> projectSkillUsage(@PathVariable("projectId") String projectId,
                                                                 @PathVariable("skillId") String skillId) {
        return success(references(skillId).stream()
                .filter(item -> projectId.equals(String.valueOf(item.getOrDefault("projectId", projectId))))
                .toList());
    }

    @GetMapping("/context")
    public Response<Map<String, Object>> renderSkillContext(@RequestParam(value = "names", required = false) List<String> names,
                                                            @RequestParam(value = "maxChars", required = false) Integer maxChars) {
        SkillRuntimeToolProvider provider = skillRuntimeToolProvider.getIfAvailable();
        String context = provider == null
                ? ""
                : provider.renderSkillContext(Optional.ofNullable(names).orElse(List.of()), Math.max(1000, Optional.ofNullable(maxChars).orElse(12000)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("names", names == null ? List.of() : names);
        data.put("content", context);
        data.put("length", context.length());
        return success(data);
    }

    @GetMapping("/{name}")
    public Response<Map<String, Object>> skillDetail(@PathVariable("name") String name) {
        SkillCatalogReader provider = skillCatalogReader.getIfAvailable();
        if (provider == null) {
            return failure("SkillCatalogReader 未初始化。");
        }
        return provider.findSkill(name)
                .map(skill -> success(skillView(skill, true)))
                .orElseGet(() -> failure("Skill 不存在：" + name));
    }

    @PutMapping("/{name}")
    public Response<Map<String, Object>> updateSkill(@PathVariable("name") String name,
                                                     @RequestBody SkillUpdateRequest request) {
        SkillCatalogEditor provider = skillCatalogEditor.getIfAvailable();
        if (provider == null) {
            return failure("SkillCatalogEditor 未初始化。");
        }
        if (provider.findSkill(name).isEmpty()) {
            return failure("Skill 不存在：" + name);
        }
        try {
            Map<String, Object> before = provider.findSkill(name)
                    .map(skill -> skillView(skill, true))
                    .orElse(null);
            provider.saveSkill(name, request == null ? null : request.content());
            Response<Map<String, Object>> response = provider.findSkill(name)
                    .map(skill -> success(skillView(skill, true)))
                    .orElseGet(() -> failure("Skill 保存后未能重新加载：" + name));
            opsConfigAuditService.record("skill", "update", name, before, response.getData());
            return response;
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        } catch (IOException e) {
            return failure("保存 Skill 失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{name}")
    public Response<List<Map<String, Object>>> deleteSkill(@PathVariable("name") String name) {
        SkillCatalogEditor provider = skillCatalogEditor.getIfAvailable();
        if (provider == null) {
            return failure("SkillCatalogEditor 未初始化。");
        }
        if (provider.findSkill(name).isEmpty()) {
            return failure("Skill 不存在：" + name);
        }
        try {
            Map<String, Object> before = provider.findSkill(name)
                    .map(skill -> skillView(skill, true))
                    .orElse(null);
            provider.deleteSkill(name);
            Response<List<Map<String, Object>>> response = listSkills();
            opsConfigAuditService.record("skill", "delete", name, before, Map.of("deleted", true));
            return response;
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        } catch (IOException e) {
            return failure("删除 Skill 失败：" + e.getMessage());
        }
    }

    private Map<String, Object> skillView(SkillsTool.Skill skill, boolean includeContent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", skill.name());
        Map<String, Object> frontMatter = Optional.ofNullable(skill.frontMatter()).orElse(Map.of());
        data.put("description", stringValue(frontMatter.get("description")));
        data.put("basePath", skill.basePath());
        data.put("frontMatter", frontMatter);
        data.put("contentLength", skill.content() == null ? 0 : skill.content().length());
        data.put("referencedBy", references(skill.name()));
        if (includeContent) {
            data.put("content", skill.content());
            SkillCatalogReader provider = skillCatalogReader.getIfAvailable();
            data.put("markdown", provider == null ? skill.content() : provider.toMarkdown(skill));
            data.put("xml", skill.toXml());
        }
        return data;
    }

    private List<Map<String, Object>> references(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return List.of();
        }
        List<Map<String, Object>> references = new ArrayList<>();
        for (OpsAgentDefinition definition : definitionRegistry.list()) {
            if (contains(definition.getSkills(), skillName)) {
                references.add(reference("agent", definition.getAgentId(), definition.getName(), null));
            }
            for (OpsWorkflowNode node : Optional.ofNullable(definition.getNodes()).orElse(List.of())) {
                if (contains(node.getSkills(), skillName)) {
                    references.add(reference("node", definition.getAgentId(), definition.getName(), node.getNodeId()));
                }
            }
        }
        return references;
    }

    private Map<String, Object> reference(String type, String agentId, String agentName, String targetId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("agentId", agentId);
        data.put("agentName", agentName);
        data.put("targetId", targetId);
        definitionRegistry.list().stream()
                .filter(definition -> agentId.equals(definition.getAgentId()))
                .findFirst()
                .ifPresent(definition -> data.put("projectId", definition.getProjectId()));
        return data;
    }

    private boolean contains(Collection<String> values, String target) {
        return Optional.ofNullable(values).orElse(List.of()).stream().anyMatch(target::equals);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int number(Object value, int fallback) {
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
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

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> failure(String message) {
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message)
                .data(null)
                .build();
    }

    public record SkillUpdateRequest(String content) {
    }
}
