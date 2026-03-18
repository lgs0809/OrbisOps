package cn.lgs.orbisops.domain.audit.adapter.repository;

import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;

import java.util.List;

public interface IAnalysisAuditRepository {

    void upsert(AnalysisAuditRecord record);

    List<AnalysisAuditRecord> list(int limit);
}
