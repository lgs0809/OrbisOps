package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.api.dto.OpsCodeDeliveryDTO;
import cn.lgs.orbisops.api.dto.OpsCodeDeliveryRequestDTO;
import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCandidate;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCapabilities;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsCodeDeliveryMapper {

    public CodeDeliveryCandidate candidate(OpsCodeDeliveryRequestDTO request) {
        if (request == null) return new CodeDeliveryCandidate(CodeDeliveryMode.LOCAL_BRANCH, "", "");
        return new CodeDeliveryCandidate(
                CodeDeliveryMode.require(request.getMode()), request.getTitle(), request.getBaseBranch());
    }

    public OpsCodeDeliveryDTO view(CodeDelivery delivery) {
        if (delivery == null) return null;
        return OpsCodeDeliveryDTO.builder()
                .deliveryId(delivery.deliveryId())
                .workspaceId(delivery.workspaceId())
                .projectId(delivery.projectId())
                .serviceId(delivery.serviceId())
                .mode(delivery.mode().name())
                .branchName(delivery.branchName())
                .commitSha(delivery.commitSha())
                .pullRequestUrl(delivery.pullRequestUrl())
                .ciStatus(delivery.ciStatus())
                .ciUrl(delivery.ciUrl())
                .createdBy(delivery.createdBy())
                .createdAt(delivery.createdAt())
                .updatedAt(delivery.updatedAt())
                .build();
    }

    public List<OpsCodeDeliveryDTO> views(List<CodeDelivery> deliveries) {
        return deliveries == null ? List.of() : deliveries.stream().map(this::view).toList();
    }

    public Map<String, Object> capabilities(CodeDeliveryCapabilities capabilities) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("localBranch", capabilities.localBranch());
        result.put("githubPullRequest", capabilities.githubPullRequest());
        result.put("githubActionsStatus", capabilities.githubActionsStatus());
        result.put("arbitraryRemote", capabilities.arbitraryRemote());
        return result;
    }
}
