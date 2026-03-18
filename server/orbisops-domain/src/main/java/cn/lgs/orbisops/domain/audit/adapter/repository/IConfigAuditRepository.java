package cn.lgs.orbisops.domain.audit.adapter.repository;

import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditDraft;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditReadiness;

import java.util.List;
import java.util.Optional;

public interface IConfigAuditRepository {

    ConfigAuditEntry append(ConfigAuditDraft draft);

    List<ConfigAuditEntry> search(ConfigAuditCriteria criteria);

    Optional<ConfigAuditEntry> find(String auditId);

    List<ConfigAuditEntry> listForOperator(String operator, int limit);

    ConfigAuditReadiness readiness();
}
