package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

public record RepairExecutionCommand(
        String workspaceId,
        RepairWorkspaceCandidate candidate,
        ProjectService service,
        SourceRepository repository,
        String baseCommit,
        String actor) {
}
