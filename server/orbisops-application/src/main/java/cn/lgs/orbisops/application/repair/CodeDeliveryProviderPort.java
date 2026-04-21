package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCiSnapshot;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryPullRequest;

import java.util.Optional;

public interface CodeDeliveryProviderPort {

    boolean configured();

    CodeDeliveryPullRequest openPullRequest(
            String branchName,
            String title,
            String baseBranch,
            String workspaceId);

    Optional<CodeDeliveryCiSnapshot> latestCi(String branchName);
}
