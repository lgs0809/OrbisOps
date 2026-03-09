package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

public record RepairWorktreeCommand(
        String workspaceId,
        String projectId,
        String serviceId,
        String environment,
        String baseCommit,
        String actor,
        ProjectService service,
        SourceRepository repository) {
}
