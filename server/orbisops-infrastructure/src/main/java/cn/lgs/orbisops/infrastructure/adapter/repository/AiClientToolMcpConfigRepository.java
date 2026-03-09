package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientToolMcpConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.infrastructure.dao.IAiClientToolMcpDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientToolMcp;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Repository
public class AiClientToolMcpConfigRepository implements IAiClientToolMcpConfigRepository {

    @Autowired(required = false)
    private IAiClientToolMcpDao dao;

    @Override
    public int insert(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.insert(toPo(record));
    }

    @Override
    public int updateById(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateById(toPo(record));
    }

    @Override
    public int updateByMcpId(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateByMcpId(toPo(record));
    }

    @Override
    public int deleteById(Long id) {
        return dao == null ? 0 : dao.deleteById(id);
    }

    @Override
    public int deleteByMcpId(String mcpId) {
        return dao == null ? 0 : dao.deleteByMcpId(mcpId);
    }

    @Override
    public AiClientConfigRecord queryById(Long id) {
        return dao == null ? null : toRecord(dao.queryById(id));
    }

    @Override
    public AiClientConfigRecord queryByMcpId(String mcpId) {
        return dao == null ? null : toRecord(dao.queryByMcpId(mcpId));
    }

    @Override
    public List<AiClientConfigRecord> queryAll() {
        return dao == null ? List.of() : toRecords(dao.queryAll());
    }

    @Override
    public List<AiClientConfigRecord> queryByStatus(Integer status) {
        return dao == null ? List.of() : toRecords(dao.queryByStatus(status));
    }

    @Override
    public List<AiClientConfigRecord> queryByTransportType(String transportType) {
        return dao == null ? List.of() : toRecords(dao.queryByTransportType(transportType));
    }

    @Override
    public List<AiClientConfigRecord> queryEnabledMcps() {
        return dao == null ? List.of() : toRecords(dao.queryEnabledMcps());
    }

    private AiClientToolMcp toPo(AiClientConfigRecord record) {
        AiClientToolMcp po = new AiClientToolMcp();
        BeanUtils.copyProperties(record, po);
        return po;
    }

    private AiClientConfigRecord toRecord(AiClientToolMcp po) {
        if (po == null) {
            return null;
        }
        AiClientConfigRecord record = new AiClientConfigRecord();
        BeanUtils.copyProperties(po, record);
        return record;
    }

    private List<AiClientConfigRecord> toRecords(List<AiClientToolMcp> rows) {
        return rows == null ? List.of() : rows.stream().map(this::toRecord).toList();
    }
}
