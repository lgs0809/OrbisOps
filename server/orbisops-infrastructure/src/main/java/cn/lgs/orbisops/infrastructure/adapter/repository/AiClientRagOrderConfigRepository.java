package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.infrastructure.dao.IAiClientRagOrderDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientRagOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Repository
public class AiClientRagOrderConfigRepository implements IAiClientRagOrderConfigRepository {

    @Autowired(required = false)
    private IAiClientRagOrderDao dao;

    @Override
    public int insert(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.insert(toPo(record));
    }

    @Override
    public int updateById(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateById(toPo(record));
    }

    @Override
    public int updateByRagId(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateByRagId(toPo(record));
    }

    @Override
    public int deleteById(Long id) {
        return dao == null ? 0 : dao.deleteById(id);
    }

    @Override
    public int deleteByRagId(String ragId) {
        return dao == null ? 0 : dao.deleteByRagId(ragId);
    }

    @Override
    public AiClientConfigRecord queryById(Long id) {
        return dao == null ? null : toRecord(dao.queryById(id));
    }

    @Override
    public AiClientConfigRecord queryByRagId(String ragId) {
        return dao == null ? null : toRecord(dao.queryByRagId(ragId));
    }

    @Override
    public List<AiClientConfigRecord> queryEnabledRagOrders() {
        return dao == null ? List.of() : toRecords(dao.queryEnabledRagOrders());
    }

    @Override
    public List<AiClientConfigRecord> queryByKnowledgeTag(String knowledgeTag) {
        return dao == null ? List.of() : toRecords(dao.queryByKnowledgeTag(knowledgeTag));
    }

    @Override
    public List<AiClientConfigRecord> queryAll() {
        return dao == null ? List.of() : toRecords(dao.queryAll());
    }

    private AiClientRagOrder toPo(AiClientConfigRecord record) {
        AiClientRagOrder po = new AiClientRagOrder();
        BeanUtils.copyProperties(record, po);
        return po;
    }

    private AiClientConfigRecord toRecord(AiClientRagOrder po) {
        if (po == null) {
            return null;
        }
        AiClientConfigRecord record = new AiClientConfigRecord();
        BeanUtils.copyProperties(po, record);
        return record;
    }

    private List<AiClientConfigRecord> toRecords(List<AiClientRagOrder> rows) {
        return rows == null ? List.of() : rows.stream().map(this::toRecord).toList();
    }
}
