package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.source.adapter.repository.IDeploymentRevisionRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.IProjectServiceRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.ISourceRepositoryRepository;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class JdbcSourceCatalogRepository implements
        ISourceRepositoryRepository,
        IDeploymentRevisionRepository,
        IProjectServiceRepository {

    private final JdbcTemplate jdbc;
    private final boolean jdbcEnabled;
    private final boolean memoryAllowed;
    private final Map<String, SourceRepository> repositoryMemory = new ConcurrentHashMap<>();
    private final Map<String, DeploymentRevision> deploymentMemory = new ConcurrentHashMap<>();
    private final Map<String, ProjectService> serviceMemory = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcSourceCatalogRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${orbisops.source-repository.jdbc-enabled:true}") boolean jdbcEnabled) {
        this(jdbcProvider.getIfAvailable(), jdbcEnabled, false);
    }

    JdbcSourceCatalogRepository(JdbcTemplate jdbc, boolean jdbcEnabled, boolean memoryAllowed) {
        this.jdbc = jdbc;
        this.jdbcEnabled = jdbcEnabled;
        this.memoryAllowed = memoryAllowed;
    }

    @Override
    public boolean available() {
        return jdbcAvailable() || memoryAllowed;
    }

    @Override
    public SourceRepository save(SourceRepository repository) {
        requireAvailable();
        if (!jdbcAvailable()) {
            repositoryMemory.put(repository.repositoryId(), repository);
            return repository;
        }
        jdbc.update("""
                INSERT INTO ai_ops_source_repository
                (repository_id, project_id, name, local_path, access_mode, code_mcp_id, logical_root,
                 default_revision, default_commit_sha, status, created_by, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  project_id=VALUES(project_id), name=VALUES(name), local_path=VALUES(local_path),
                  access_mode=VALUES(access_mode), code_mcp_id=VALUES(code_mcp_id), logical_root=VALUES(logical_root),
                  default_revision=VALUES(default_revision), default_commit_sha=VALUES(default_commit_sha),
                  status=VALUES(status), update_time=VALUES(update_time)
                """,
                repository.repositoryId(), repository.projectId(), repository.name(), repository.localPath(),
                repository.accessMode().name(), repository.codeMcpId(), repository.logicalRoot(),
                repository.defaultRevision(), repository.defaultCommitSha(), repository.status(), repository.createdBy(),
                repository.createdAt(), repository.updatedAt());
        return repository;
    }

    @Override
    public Optional<SourceRepository> find(String projectId, String repositoryId) {
        if (!available() || !StringUtils.hasText(projectId) || !StringUtils.hasText(repositoryId)) return Optional.empty();
        if (!jdbcAvailable()) {
            SourceRepository value = repositoryMemory.get(repositoryId.trim());
            return value != null && projectId.trim().equals(value.projectId()) ? Optional.of(value) : Optional.empty();
        }
        return jdbc.query("""
                SELECT repository_id, project_id, name, local_path, access_mode, code_mcp_id, logical_root,
                       default_revision, default_commit_sha,
                       status, created_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_source_repository
                WHERE project_id = ? AND repository_id = ?
                LIMIT 1
                """, (rs, rowNum) -> sourceRepository(
                rs.getString("repository_id"), rs.getString("project_id"), rs.getString("name"),
                rs.getString("local_path"), rs.getString("access_mode"), rs.getString("code_mcp_id"),
                rs.getString("logical_root"), rs.getString("default_revision"), rs.getString("default_commit_sha"),
                rs.getString("status"), rs.getString("created_by"),
                rs.getString("create_time_text"), rs.getString("update_time_text")),
                projectId.trim(), repositoryId.trim()).stream().findFirst();
    }

    @Override
    public Optional<SourceRepository> findByRepositoryId(String repositoryId) {
        if (!available() || !StringUtils.hasText(repositoryId)) return Optional.empty();
        if (!jdbcAvailable()) return Optional.ofNullable(repositoryMemory.get(repositoryId.trim()));
        return jdbc.query("""
                SELECT repository_id, project_id, name, local_path, access_mode, code_mcp_id, logical_root,
                       default_revision, default_commit_sha,
                       status, created_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_source_repository
                WHERE repository_id = ?
                LIMIT 1
                """, (rs, rowNum) -> sourceRepository(
                rs.getString("repository_id"), rs.getString("project_id"), rs.getString("name"),
                rs.getString("local_path"), rs.getString("access_mode"), rs.getString("code_mcp_id"),
                rs.getString("logical_root"), rs.getString("default_revision"), rs.getString("default_commit_sha"),
                rs.getString("status"), rs.getString("created_by"),
                rs.getString("create_time_text"), rs.getString("update_time_text")), repositoryId.trim())
                .stream().findFirst();
    }

    @Override
    public Optional<SourceRepository> findByMcpId(String projectId, String mcpId) {
        if (!available() || !StringUtils.hasText(mcpId)) return Optional.empty();
        String suffix = "-readonly-git-mcp";
        if (!mcpId.endsWith(suffix)) return Optional.empty();
        String repositoryId = mcpId.substring(0, mcpId.length() - suffix.length());
        return StringUtils.hasText(projectId)
                ? find(projectId.trim(), repositoryId)
                : findByRepositoryId(repositoryId);
    }

    @Override
    public List<SourceRepository> list(String projectId) {
        requireAvailable();
        if (!jdbcAvailable()) {
            return repositoryMemory.values().stream()
                    .filter(value -> !StringUtils.hasText(projectId) || projectId.trim().equals(value.projectId()))
                    .sorted(Comparator.comparing(SourceRepository::repositoryId))
                    .toList();
        }
        String filter = StringUtils.hasText(projectId) ? " WHERE project_id = ?" : "";
        Object[] args = StringUtils.hasText(projectId) ? new Object[]{projectId.trim()} : new Object[]{};
        return jdbc.query("""
                SELECT repository_id, project_id, name, local_path, access_mode, code_mcp_id, logical_root,
                       default_revision, default_commit_sha,
                       status, created_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_source_repository
                """ + filter + " ORDER BY repository_id", (rs, rowNum) -> sourceRepository(
                rs.getString("repository_id"), rs.getString("project_id"), rs.getString("name"),
                rs.getString("local_path"), rs.getString("access_mode"), rs.getString("code_mcp_id"),
                rs.getString("logical_root"), rs.getString("default_revision"), rs.getString("default_commit_sha"),
                rs.getString("status"), rs.getString("created_by"),
                rs.getString("create_time_text"), rs.getString("update_time_text")), args);
    }

    @Override
    public DeploymentRevision save(DeploymentRevision deployment) {
        requireAvailable();
        if (!jdbcAvailable()) {
            deploymentMemory.put(deployment.deploymentId(), deployment);
            return deployment;
        }
        jdbc.update("""
                INSERT INTO ai_ops_deployment_revision
                (deployment_id, project_id, repository_id, environment, service_name, commit_sha,
                 image_ref, recorded_by, deployed_at, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  repository_id=VALUES(repository_id), commit_sha=VALUES(commit_sha), image_ref=VALUES(image_ref),
                  recorded_by=VALUES(recorded_by), deployed_at=VALUES(deployed_at), update_time=VALUES(update_time)
                """,
                deployment.deploymentId(), deployment.projectId(), deployment.repositoryId(),
                deployment.environment(), deployment.serviceName(), deployment.commitSha(), deployment.imageRef(),
                deployment.recordedBy(), deployment.deployedAt(), deployment.updatedAt());
        return deployment;
    }

    @Override
    public List<DeploymentRevision> list(String projectId, String environment) {
        requireAvailable();
        if (!jdbcAvailable()) {
            return deploymentMemory.values().stream()
                    .filter(value -> !StringUtils.hasText(projectId) || projectId.trim().equals(value.projectId()))
                    .filter(value -> !StringUtils.hasText(environment)
                            || environment.trim().equalsIgnoreCase(value.environment()))
                    .sorted(Comparator.comparing(DeploymentRevision::updatedAt).reversed())
                    .toList();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT deployment_id, project_id, repository_id, environment, service_name, commit_sha,
                       image_ref, recorded_by,
                       DATE_FORMAT(deployed_at, '%Y-%m-%d %H:%i:%s') deployed_at_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_deployment_revision WHERE 1=1
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(projectId)) { sql.append(" AND project_id = ?"); args.add(projectId.trim()); }
        if (StringUtils.hasText(environment)) { sql.append(" AND environment = ?"); args.add(environment.trim()); }
        sql.append(" ORDER BY update_time DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new DeploymentRevision(
                rs.getString("deployment_id"), rs.getString("project_id"), rs.getString("repository_id"),
                rs.getString("environment"), rs.getString("service_name"), rs.getString("commit_sha"),
                rs.getString("image_ref"), rs.getString("recorded_by"),
                rs.getString("deployed_at_text"), rs.getString("update_time_text")), args.toArray());
    }

    @Override
    public Optional<DeploymentRevision> resolve(String projectId, String environment, String serviceName) {
        requireAvailable();
        String deploymentId = projectId + ":" + environment + ":" + serviceName;
        if (!jdbcAvailable()) return Optional.ofNullable(deploymentMemory.get(deploymentId));
        return jdbc.query("""
                SELECT deployment_id, project_id, repository_id, environment, service_name, commit_sha,
                       image_ref, recorded_by,
                       DATE_FORMAT(deployed_at, '%Y-%m-%d %H:%i:%s') deployed_at_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_deployment_revision
                WHERE deployment_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new DeploymentRevision(
                rs.getString("deployment_id"), rs.getString("project_id"), rs.getString("repository_id"),
                rs.getString("environment"), rs.getString("service_name"), rs.getString("commit_sha"),
                rs.getString("image_ref"), rs.getString("recorded_by"),
                rs.getString("deployed_at_text"), rs.getString("update_time_text")), deploymentId)
                .stream().findFirst();
    }

    @Override
    public ProjectService saveService(ProjectService service) {
        requireAvailable();
        if (!jdbcAvailable()) {
            serviceMemory.put(serviceKey(service.projectId(), service.serviceId()), service);
            return service;
        }
        jdbc.update("""
                INSERT INTO ai_ops_project_service
                (service_id, project_id, name, repository_id, module_path, build_profile, artifact_path,
                 deployment_resource_id, health_url, smoke_urls_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  name=VALUES(name), repository_id=VALUES(repository_id), module_path=VALUES(module_path),
                  build_profile=VALUES(build_profile), artifact_path=VALUES(artifact_path),
                  deployment_resource_id=VALUES(deployment_resource_id), health_url=VALUES(health_url),
                  smoke_urls_json=VALUES(smoke_urls_json), status=VALUES(status), update_time=VALUES(update_time)
                """,
                service.serviceId(), service.projectId(), service.name(), service.repositoryId(), service.modulePath(),
                service.buildProfile().name(), service.artifactPath(), service.deploymentResourceId(), service.healthUrl(),
                JSON.toJSONString(service.smokeUrls()), service.status(), service.createdAt(), service.updatedAt());
        return service;
    }

    @Override
    public Optional<ProjectService> findService(String projectId, String serviceId) {
        if (!available() || !StringUtils.hasText(projectId) || !StringUtils.hasText(serviceId)) return Optional.empty();
        if (!jdbcAvailable()) return Optional.ofNullable(serviceMemory.get(serviceKey(projectId.trim(), serviceId.trim())));
        return jdbc.query("""
                SELECT service_id, project_id, name, repository_id, module_path, build_profile,
                       artifact_path, deployment_resource_id, health_url, smoke_urls_json, status,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_project_service
                WHERE project_id = ? AND service_id = ? AND status <> 'DELETED'
                LIMIT 1
                """, (rs, rowNum) -> projectService(
                rs.getString("service_id"), rs.getString("project_id"), rs.getString("name"),
                rs.getString("repository_id"), rs.getString("module_path"), rs.getString("build_profile"),
                rs.getString("artifact_path"), rs.getString("deployment_resource_id"), rs.getString("health_url"),
                rs.getString("smoke_urls_json"), rs.getString("status"),
                rs.getString("create_time_text"), rs.getString("update_time_text")),
                projectId.trim(), serviceId.trim()).stream().findFirst();
    }

    @Override
    public List<ProjectService> listServices(String projectId) {
        requireAvailable();
        if (!jdbcAvailable()) {
            return serviceMemory.values().stream()
                    .filter(value -> !StringUtils.hasText(projectId) || projectId.trim().equals(value.projectId()))
                    .sorted(Comparator.comparing(ProjectService::serviceId))
                    .toList();
        }
        String projectFilter = StringUtils.hasText(projectId) ? " AND project_id = ?" : "";
        Object[] args = StringUtils.hasText(projectId) ? new Object[]{projectId.trim()} : new Object[]{};
        return jdbc.query("""
                SELECT service_id, project_id, name, repository_id, module_path, build_profile,
                       artifact_path, deployment_resource_id, health_url, smoke_urls_json, status,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') create_time_text,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') update_time_text
                FROM ai_ops_project_service
                WHERE status <> 'DELETED'
                """ + projectFilter + " ORDER BY service_id", (rs, rowNum) -> projectService(
                rs.getString("service_id"), rs.getString("project_id"), rs.getString("name"),
                rs.getString("repository_id"), rs.getString("module_path"), rs.getString("build_profile"),
                rs.getString("artifact_path"), rs.getString("deployment_resource_id"), rs.getString("health_url"),
                rs.getString("smoke_urls_json"), rs.getString("status"),
                rs.getString("create_time_text"), rs.getString("update_time_text")), args);
    }

    private SourceRepository sourceRepository(
            String repositoryId, String projectId, String name, String localPath,
            String accessMode, String codeMcpId, String logicalRoot,
            String defaultRevision, String defaultCommitSha, String status, String createdBy,
            String createdAt, String updatedAt) {
        return new SourceRepository(repositoryId, repositoryId + "-readonly-git-mcp", projectId, name,
                localPath, SourceRepositoryAccessMode.require(accessMode), codeMcpId, logicalRoot,
                defaultRevision, defaultCommitSha, status, createdBy, createdAt, updatedAt);
    }

    private ProjectService projectService(
            String serviceId, String projectId, String name, String repositoryId,
            String modulePath, String buildProfile, String artifactPath, String deploymentResourceId,
            String healthUrl, String smokeUrlsJson, String status, String createdAt, String updatedAt) {
        List<String> smokeUrls = StringUtils.hasText(smokeUrlsJson)
                ? Optional.ofNullable(JSON.parseArray(smokeUrlsJson, String.class)).orElse(List.of())
                : List.of();
        return new ProjectService(serviceId, projectId, name, repositoryId, modulePath,
                BuildProfile.require(buildProfile), artifactPath, deploymentResourceId, healthUrl,
                smokeUrls, status, createdAt, updatedAt);
    }

    private void requireAvailable() {
        if (!available()) throw new IllegalStateException("代码仓库配置必须使用 MySQL 持久化");
    }

    private boolean jdbcAvailable() {
        return jdbcEnabled && jdbc != null;
    }

    private String serviceKey(String projectId, String serviceId) {
        return projectId + ":" + serviceId;
    }
}
