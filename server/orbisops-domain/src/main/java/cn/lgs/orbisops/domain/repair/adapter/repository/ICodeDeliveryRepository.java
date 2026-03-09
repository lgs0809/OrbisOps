package cn.lgs.orbisops.domain.repair.adapter.repository;

import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;

import java.util.List;
import java.util.Optional;

public interface ICodeDeliveryRepository {

    CodeDelivery save(CodeDelivery delivery);

    Optional<CodeDelivery> find(String deliveryId);

    Optional<CodeDelivery> findByWorkspaceAndMode(String workspaceId, CodeDeliveryMode mode);

    List<CodeDelivery> list(String workspaceId);
}
