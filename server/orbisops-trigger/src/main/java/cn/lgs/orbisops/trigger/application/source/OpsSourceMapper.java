package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionDTO;
import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionRequestDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceFileDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceSearchHitDTO;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.DeploymentRevisionCandidate;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCandidate;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCapabilities;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCapabilities;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsSourceMapper {

    public SourceRepositoryCandidate repositoryCandidate(OpsSourceRepositoryRequestDTO request) {
        if (request == null) return null;
        return new SourceRepositoryCandidate(
                request.getProjectId(), request.getRepositoryId(), request.getName(),
                request.getLocalPath(), request.getDefaultRevision(),
                SourceRepositoryAccessMode.require(request.getAccessMode()),
                request.getCodeMcpId(), request.getLogicalRoot());
    }

    public OpsSourceRepositoryDTO repositoryView(SourceRepository repository) {
        if (repository == null) return null;
        return OpsSourceRepositoryDTO.builder()
                .repositoryId(repository.repositoryId())
                .mcpId(repository.mcpId())
                .projectId(repository.projectId())
                .name(repository.name())
                .localPath(repository.localPath())
                .accessMode(repository.accessMode().name())
                .codeMcpId(repository.codeMcpId())
                .logicalRoot(repository.logicalRoot())
                .defaultRevision(repository.defaultRevision())
                .defaultCommitSha(repository.defaultCommitSha())
                .status(repository.status())
                .createdBy(repository.createdBy())
                .createdAt(repository.createdAt())
                .updatedAt(repository.updatedAt())
                .build();
    }

    public List<OpsSourceRepositoryDTO> repositoryViews(List<SourceRepository> values) {
        return values == null ? List.of() : values.stream().map(this::repositoryView).toList();
    }

    public DeploymentRevisionCandidate deploymentCandidate(OpsDeploymentRevisionRequestDTO request) {
        if (request == null) return null;
        return new DeploymentRevisionCandidate(
                request.getProjectId(), request.getRepositoryId(), request.getEnvironment(),
                request.getServiceName(), request.getRevision(), request.getImageRef());
    }

    public OpsDeploymentRevisionDTO deploymentView(DeploymentRevision deployment) {
        if (deployment == null) return null;
        return OpsDeploymentRevisionDTO.builder()
                .deploymentId(deployment.deploymentId())
                .projectId(deployment.projectId())
                .repositoryId(deployment.repositoryId())
                .environment(deployment.environment())
                .serviceName(deployment.serviceName())
                .commitSha(deployment.commitSha())
                .imageRef(deployment.imageRef())
                .recordedBy(deployment.recordedBy())
                .deployedAt(deployment.deployedAt())
                .updatedAt(deployment.updatedAt())
                .build();
    }

    public List<OpsDeploymentRevisionDTO> deploymentViews(List<DeploymentRevision> values) {
        return values == null ? List.of() : values.stream().map(this::deploymentView).toList();
    }

    public ProjectServiceCandidate serviceCandidate(OpsProjectServiceRequestDTO request) {
        if (request == null) return null;
        return new ProjectServiceCandidate(
                request.getProjectId(), request.getServiceId(), request.getName(), request.getRepositoryId(),
                request.getModulePath(), request.getBuildProfile(), request.getArtifactPath(),
                request.getDeploymentResourceId(), request.getHealthUrl(), request.getSmokeUrls());
    }

    public OpsProjectServiceDTO serviceView(ProjectService service) {
        if (service == null) return null;
        return OpsProjectServiceDTO.builder()
                .serviceId(service.serviceId())
                .projectId(service.projectId())
                .name(service.name())
                .repositoryId(service.repositoryId())
                .modulePath(service.modulePath())
                .buildProfile(service.buildProfile().name())
                .artifactPath(service.artifactPath())
                .deploymentResourceId(service.deploymentResourceId())
                .healthUrl(service.healthUrl())
                .smokeUrls(service.smokeUrls())
                .status(service.status())
                .createdAt(service.createdAt())
                .updatedAt(service.updatedAt())
                .build();
    }

    public ProjectService service(OpsProjectServiceDTO value) {
        if (value == null) return null;
        return new ProjectService(
                value.getServiceId(), value.getProjectId(), value.getName(), value.getRepositoryId(),
                value.getModulePath(), cn.lgs.orbisops.domain.source.model.BuildProfile.require(value.getBuildProfile()),
                value.getArtifactPath(), value.getDeploymentResourceId(), value.getHealthUrl(), value.getSmokeUrls(),
                value.getStatus(), value.getCreatedAt(), value.getUpdatedAt());
    }

    public List<OpsProjectServiceDTO> serviceViews(List<ProjectService> values) {
        return values == null ? List.of() : values.stream().map(this::serviceView).toList();
    }

    public OpsSourceFileDTO fileView(SourceFile file) {
        if (file == null) return null;
        return OpsSourceFileDTO.builder()
                .repositoryId(file.repositoryId())
                .commitSha(file.commitSha())
                .path(file.path())
                .sizeBytes(file.sizeBytes())
                .content(file.content())
                .build();
    }

    public OpsSourceSearchHitDTO hitView(SourceSearchHit hit) {
        if (hit == null) return null;
        return OpsSourceSearchHitDTO.builder()
                .repositoryId(hit.repositoryId())
                .commitSha(hit.commitSha())
                .path(hit.path())
                .line(hit.line())
                .text(hit.text())
                .build();
    }

    public List<OpsSourceSearchHitDTO> hitViews(List<SourceSearchHit> values) {
        return values == null ? List.of() : values.stream().map(this::hitView).toList();
    }

    public Map<String, Object> capabilities(SourceRepositoryCapabilities capabilities) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", capabilities.enabled());
        result.put("mode", capabilities.mode());
        result.put("operations", capabilities.operations());
        result.put("remoteCloneEnabled", capabilities.remoteCloneEnabled());
        result.put("allowedLocalRootsConfigured", capabilities.allowedLocalRootsConfigured());
        return result;
    }

    public Map<String, Object> capabilities(ProjectServiceCapabilities capabilities) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", capabilities.enabled());
        result.put("buildProfiles", capabilities.buildProfiles());
        result.put("arbitraryShellAllowed", capabilities.arbitraryShellAllowed());
        result.put("serviceCount", capabilities.serviceCount());
        return result;
    }

    public OpsMcpServerConfig mcpView(SourceMcpRuntimeSpec spec) {
        if (spec == null) return null;
        return OpsMcpServerConfig.builder()
                .name(spec.name())
                .description(spec.description())
                .transport(spec.transport())
                .command(spec.command())
                .args(spec.args())
                .env(spec.env())
                .timeoutSeconds(spec.timeoutSeconds())
                .toolCapabilities(spec.toolCapabilities())
                .allowedTools(spec.allowedTools())
                .build();
    }
}
