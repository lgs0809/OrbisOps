package cn.lgs.orbisops.domain.audit.adapter.repository;

import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;

import java.util.Optional;

public interface IConfigAuditPolicyRepository {

    Optional<ConfigAuditPolicySnapshot> find(String projectId);

    ConfigAuditPolicySnapshot save(AuditPolicy policy);

    boolean persistent();
}
