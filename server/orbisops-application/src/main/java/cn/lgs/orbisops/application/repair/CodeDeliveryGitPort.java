package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.CodeDeliveryBranch;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;

import java.nio.file.Path;

public interface CodeDeliveryGitPort {

    CodeDeliveryBranch publishBranch(
            RepairWorkspace workspace,
            Path worktree,
            String branchName,
            String title,
            boolean pushRemote);
}
