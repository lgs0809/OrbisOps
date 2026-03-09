package cn.lgs.orbisops.trigger.ops.source;

import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionDTO;
import cn.lgs.orbisops.api.dto.OpsDeploymentRevisionRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceFileDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryDTO;
import cn.lgs.orbisops.api.dto.OpsSourceRepositoryRequestDTO;
import cn.lgs.orbisops.api.dto.OpsSourceSearchHitDTO;
import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.trigger.application.source.OpsSourceMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Source Repository compatibility ACL.
 *
 * Existing Repair and Runtime callers keep their stable DTO-facing contract while
 * typed Application/Domain/Infrastructure own repository, deployment and Git behavior.
 */
@Service
public class OpsSourceRepositoryService {

    private final SourceRepositoryApplicationService sources;
    private final OpsSourceMapper mapper;

    public OpsSourceRepositoryService(
            SourceRepositoryApplicationService sources,
            OpsSourceMapper mapper) {
        this.sources = sources;
        this.mapper = mapper;
    }

    public Map<String, Object> capabilities() {
        return mapper.capabilities(sources.capabilities());
    }

    public OpsSourceRepositoryDTO registerRepository(OpsSourceRepositoryRequestDTO request, String actor) {
        return mapper.repositoryView(sources.register(mapper.repositoryCandidate(request), actor));
    }

    public OpsDeploymentRevisionDTO recordDeployment(OpsDeploymentRevisionRequestDTO request, String actor) {
        return mapper.deploymentView(sources.recordDeployment(mapper.deploymentCandidate(request), actor));
    }

    public List<OpsSourceRepositoryDTO> listRepositories(String projectId) {
        return mapper.repositoryViews(sources.list(projectId));
    }

    public Optional<OpsSourceRepositoryDTO> getRepository(String projectId, String repositoryId) {
        return sources.find(projectId, repositoryId).map(mapper::repositoryView);
    }

    public Optional<OpsMcpServerConfig> resolveMcpServer(String mcpId) {
        return sources.resolveMcp(mcpId).map(mapper::mcpView);
    }

    public Optional<OpsMcpServerConfig> resolveMcpServer(String projectId, String mcpId) {
        return sources.resolveMcp(projectId, mcpId).map(mapper::mcpView);
    }

    public List<OpsDeploymentRevisionDTO> listDeployments(String projectId, String environment) {
        return mapper.deploymentViews(sources.listDeployments(projectId, environment));
    }

    public Optional<OpsDeploymentRevisionDTO> resolveDeployment(
            String projectId,
            String environment,
            String serviceName) {
        return sources.resolveDeployment(projectId, environment, serviceName).map(mapper::deploymentView);
    }

    public OpsSourceFileDTO readFile(
            String projectId,
            String repositoryId,
            String revision,
            String filePath) {
        return mapper.fileView(sources.readFile(projectId, repositoryId, revision, filePath));
    }

    public List<OpsSourceSearchHitDTO> search(
            String projectId,
            String repositoryId,
            String revision,
            String query,
            int limit) {
        return mapper.hitViews(sources.search(projectId, repositoryId, revision, query, limit));
    }
}
