package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.capability.CapabilityReadinessUseCase;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectProductReadinessApplicationService;
import cn.lgs.orbisops.application.project.ProjectMemberApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryLoadResult;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.trigger.application.project.OpsProjectCapabilityMetadataPolicy;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceCompatibilityPayloadStore;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceMaterializationMapper;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceProjectionMapper;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceViewAssembler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务系统资源空间服务。
 * <p>
 * 项目是运维运行域：资源、项目级 MCP、默认知识库和 Agent 编排都围绕 projectId 隔离。
 * 内存 Map 是运行态缓存，MySQL 是配置事实来源。
 */
@Slf4j
@Service
public class OpsProjectWorkspaceService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectDefinitionApplicationService projectDefinitionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectMemberApplicationService projectMemberService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectSkillAuthorizationApplicationService projectSkillAuthorizationService;

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectWorkspaceProjectionApplicationService workspaceProjectionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectProductReadinessApplicationService productReadinessService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CapabilityReadinessUseCase capabilityReadinessUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory;

    @org.springframework.beans.factory.annotation.Autowired
    private OpsProjectWorkspaceProjectionMapper workspaceProjectionMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private OpsProjectWorkspaceMaterializationMapper materializationMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private OpsProjectWorkspaceCompatibilityPayloadStore compatibilityPayloadStore;

    @PostConstruct
    public synchronized void init() {
        compatibilityPayloadStore.clear();
        ProjectWorkspaceRuntimeDirectoryLoadResult result = runtimeDirectory.loadPersisted();
        if (result.loaded()) {
            log.info("从 MySQL 加载业务系统空间配置，projectCount={}", result.projectCount());
            return;
        }
        log.info("业务系统空间为空，等待管理员创建项目或显式启用预设导入");
    }

    public synchronized Map<String, Object> snapshot() {
        OpsProjectWorkspaceViewAssembler assembler = workspaceViewAssembler();
        List<Map<String, Object>> projectViews = runtimeDirectory.projects().stream()
                .map(assembler::detail)
                .toList();
        return Map.of(
                "projects", projectViews,
                "templates", templates()
        );
    }

    public synchronized Map<String, Object> projectDetail(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return Map.of();
        }
        OpsProjectWorkspaceViewAssembler assembler = workspaceViewAssembler();
        return runtimeDirectory.project(projectId.trim())
                .map(assembler::detail)
                .orElseGet(Map::of);
    }

    public synchronized void materializeProjectDefinition(Map<String, Object> definition) {
        if (definition == null || definition.isEmpty()) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_REQUIRED");
        }
        ProjectDefinition materialized = materializationMapper.project(definition);
        runtimeDirectory.materializeDefinition(materialized);
        compatibilityPayloadStore.putProject(materialized.projectId(), definition);
    }

    public synchronized void materializeProjectResource(Map<String, Object> resource) {
        if (resource == null || resource.isEmpty()) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_REQUIRED");
        }
        ProjectResourceDefinition materialized = materializationMapper.resource(resource);
        if (runtimeDirectory.materializeResource(materialized)) {
            compatibilityPayloadStore.putResource(
                    materialized.projectId(), materialized.resourceId(), resource);
        }
    }

    public synchronized void materializeProjectMcp(Map<String, Object> mcp) {
        if (mcp == null || mcp.isEmpty()) {
            throw new IllegalArgumentException("PROJECT_MCP_REQUIRED");
        }
        ProjectMcpDefinition materialized = materializationMapper.mcp(mcp);
        if (runtimeDirectory.materializeMcp(materialized)) {
            compatibilityPayloadStore.putMcp(
                    materialized.projectId(), materialized.mcpId(), mcp);
        }
    }

    public synchronized List<Map<String, Object>> publicCatalog() {
        OpsProjectWorkspaceViewAssembler assembler = workspaceViewAssembler();
        return runtimeDirectory.projects().stream()
                .map(assembler::catalog)
                .toList();
    }

    public List<Map<String, Object>> listProjectMembers(String projectId) {
        if (!workspaceProjectExists(projectId)) {
            throw new IllegalArgumentException("项目不存在：" + projectId);
        }
        return projectMemberService == null
                ? List.of()
                : projectMemberService.list(projectId.trim());
    }

    public Map<String, Object> grantProjectMemberIfAbsent(String projectId,
                                                          String userId,
                                                          String username,
                                                          String memberRole,
                                                          String grantedBy) {
        if (!workspaceProjectExists(projectId)) {
            throw new IllegalArgumentException("项目不存在：" + projectId);
        }
        if (projectMemberService == null) {
            throw new IllegalStateException("项目成员授权需要 MySQL 持久化");
        }
        return projectMemberService.grantIfAbsent(
                projectId.trim(), userId, username, memberRole, grantedBy);
    }

    public List<Map<String, Object>> replaceProjectMembers(String projectId,
                                                           List<Map<String, Object>> members,
                                                           String grantedBy) {
        if (!workspaceProjectExists(projectId)) {
            throw new IllegalArgumentException("项目不存在：" + projectId);
        }
        if (projectMemberService == null) {
            throw new IllegalStateException("项目成员授权需要 MySQL 持久化");
        }
        return projectMemberService.replace(projectId.trim(), members, grantedBy);
    }

    public List<Map<String, Object>> templates() {
        return List.of(
                template("mysql", "MySQL", "关系型数据库", List.of("SHOW_SCHEMA", "SELECT", "EXPLAIN", "AGGREGATE", "JOIN"), "表/字段/索引/慢 SQL 诊断"),
                template("postgresql", "PostgreSQL", "关系型数据库", List.of("SHOW_SCHEMA", "SELECT", "EXPLAIN", "AGGREGATE", "JOIN"), "schema/table/column/index/执行计划"),
                template("redis", "Redis", "缓存与键值存储", List.of("INFO", "SCAN_PATTERN", "GET", "TTL", "MEMORY_USAGE", "SLOWLOG"), "key pattern/数据类型/TTL/慢命令"),
                template("rabbitmq", "RabbitMQ", "消息队列", List.of("READ_OVERVIEW", "READ_QUEUE", "READ_CONSUMER"), "vhost/queue/consumer/ready/unacked"),
                template("elasticsearch", "Elasticsearch", "日志与检索", List.of("READ_MAPPING", "SEARCH_INDEX", "AGGREGATION", "READ_LOG_DETAIL"), "index/mapping/字段/时间窗口"),
                template("prometheus", "Prometheus", "指标与时序", List.of("QUERY_INSTANT", "QUERY_RANGE", "READ_TARGETS", "READ_METADATA"), "metric/label/job/时间窗口"),
                template("grafana", "Grafana", "看板与告警", List.of("READ_DASHBOARD", "READ_ALERT_RULE", "READ_DATASOURCE"), "dashboard/alert/datasource 只读治理"),
                template("kubernetes", "Kubernetes", "容器编排", List.of("READ_WORKLOAD", "READ_EVENT", "READ_LOG", "DRY_RUN_APPLY"), "namespace/workload/event/log/dry-run"),
                template("nacos", "Nacos", "配置中心", List.of("READ_CONFIG", "READ_HISTORY", "DIFF_CONFIG"), "namespace/group/dataId 配置读取和比对"),
                template("jenkins", "Jenkins", "CI/CD", List.of("READ_JOB", "READ_BUILD", "READ_LOG", "DRY_RUN_PIPELINE"), "job/build/log/pipeline 验证"),
                template("gitlab_ci", "GitLab CI", "CI/CD", List.of("READ_PIPELINE", "READ_JOB", "READ_LOG", "DRY_RUN_PIPELINE"), "pipeline/job/log 验证"),
                template("cmdb", "CMDB", "资产与拓扑", List.of("READ_SERVICE", "READ_DEPENDENCY", "READ_OWNER"), "service/dependency/owner 查询"),
                template("http_api", "HTTP API", "内部 API", List.of("GET", "HEAD", "OPTIONS", "DRY_RUN"), "受控 HTTP 只读或 dry-run 调用"),
                template("webhook", "Webhook", "事件通知", List.of("READ_SUBSCRIPTION", "DRY_RUN_NOTIFY"), "订阅读取与通知 dry-run"),
                template("custom", "自定义组件", "扩展接入", List.of("READ", "VALIDATE", "DRY_RUN"), "通过 Bridge/MCP 契约扩展，默认不可生产写")
        );
    }

    private boolean workspaceProjectExists(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return false;
        }
        String normalizedProjectId = projectId.trim();
        return projectDefinitionService != null
                ? projectDefinitionService.exists(normalizedProjectId)
                : runtimeDirectory.projectExists(normalizedProjectId);
    }

    public synchronized Map<String, Object> createProject(Map<String, Object> request) {
        ProjectDefinitionApplicationService service = requireProjectDefinitionService();
        Map<String, Object> definition = service.create(request);
        materializeProjectDefinition(definition);
        return projectDetail(text(definition.get("projectId"), ""));
    }

    public synchronized Map<String, Object> updateProject(Map<String, Object> request) {
        ProjectDefinitionApplicationService service = requireProjectDefinitionService();
        Map<String, Object> definition = service.update(request);
        materializeProjectDefinition(definition);
        return projectDetail(text(definition.get("projectId"), ""));
    }

    private Map<String, Object> template(
            String type,
            String name,
            String category,
            List<String> actions,
            String schemaHint) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("type", type);
        template.put("name", name);
        template.put("category", category);
        template.put("actions", actions);
        template.put("schemaHint", schemaHint);
        template.put("multiObjectPermission",
                OpsProjectCapabilityMetadataPolicy.multiObjectPermission(type, false));
        return template;
    }

    private OpsProjectWorkspaceViewAssembler workspaceViewAssembler() {
        return new OpsProjectWorkspaceViewAssembler(
                runtimeDirectory,
                materializationMapper,
                compatibilityPayloadStore,
                workspaceProjectionService,
                workspaceProjectionMapper,
                projectSkillAuthorizationService,
                projectKnowledgeAuthorizationService,
                projectDefinitionService,
                productReadinessService,
                capabilityReadinessUseCase);
    }

    private ProjectDefinitionApplicationService requireProjectDefinitionService() {
        if (projectDefinitionService == null) {
            throw new IllegalStateException("PROJECT_DEFINITION_SERVICE_UNAVAILABLE");
        }
        return projectDefinitionService;
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
