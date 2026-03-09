package cn.lgs.orbisops.trigger.ops.source;

import cn.lgs.orbisops.api.dto.OpsProjectServiceDTO;
import cn.lgs.orbisops.api.dto.OpsProjectServiceRequestDTO;
import cn.lgs.orbisops.application.source.ProjectServiceApplicationService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceBuildCommand;
import cn.lgs.orbisops.trigger.application.source.OpsSourceMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Project Service compatibility ACL. */
@Service
public class OpsProjectServiceCatalogService {

    public static final String MAVEN_VERIFY = "MAVEN_VERIFY";
    public static final String NPM_TEST_BUILD = "NPM_TEST_BUILD";
    public static final String MAKE_CI = "MAKE_CI";

    private final ProjectServiceApplicationService services;
    private final OpsSourceMapper mapper;

    public OpsProjectServiceCatalogService(
            ProjectServiceApplicationService services,
            OpsSourceMapper mapper) {
        this.services = services;
        this.mapper = mapper;
    }

    public Map<String, Object> capabilities() {
        return mapper.capabilities(services.capabilities());
    }

    public OpsProjectServiceDTO upsert(OpsProjectServiceRequestDTO request) {
        return upsert(request, "SYSTEM");
    }

    public OpsProjectServiceDTO upsert(OpsProjectServiceRequestDTO request, String actor) {
        return mapper.serviceView(services.upsert(mapper.serviceCandidate(request), actor));
    }

    public List<OpsProjectServiceDTO> list(String projectId) {
        return mapper.serviceViews(services.list(projectId));
    }

    public Optional<OpsProjectServiceDTO> get(String projectId, String serviceId) {
        return services.find(projectId, serviceId).map(mapper::serviceView);
    }

    public BuildCommand buildCommand(OpsProjectServiceDTO service, Path repositoryRoot) {
        ProjectServiceBuildCommand command = services.buildCommand(mapper.service(service), repositoryRoot);
        return new BuildCommand(command.workingDirectory(), command.command());
    }

    public record BuildCommand(Path workingDirectory, List<String> command) {
    }
}
