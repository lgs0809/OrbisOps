package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.infrastructure.dao.IAiClientApiDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientApi;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Repository
public class AiClientApiConfigRepository implements IAiClientApiConfigRepository {

    @Autowired(required = false)
    private IAiClientApiDao dao;

    @Override
    public int insert(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.insert(toPo(record));
    }

    @Override
    public int updateById(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateById(toPo(record));
    }

    @Override
    public int updateByApiId(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateByApiId(toPo(record));
    }

    @Override
    public int deleteById(Long id) {
        return dao == null ? 0 : dao.deleteById(id);
    }

    @Override
    public int deleteByApiId(String apiId) {
        return dao == null ? 0 : dao.deleteByApiId(apiId);
    }

    @Override
    public AiClientConfigRecord queryById(Long id) {
        return dao == null ? null : toRecord(dao.queryById(id));
    }

    @Override
    public AiClientConfigRecord queryByApiId(String apiId) {
        return dao == null ? null : toRecord(dao.queryByApiId(apiId));
    }

    @Override
    public List<AiClientConfigRecord> queryEnabledApis() {
        return dao == null ? List.of() : toRecords(dao.queryEnabledApis());
    }

    @Override
    public List<AiClientConfigRecord> queryAll() {
        return dao == null ? List.of() : toRecords(dao.queryAll());
    }

    private AiClientApi toPo(AiClientConfigRecord record) {
        AiClientApi po = new AiClientApi();
        BeanUtils.copyProperties(record, po);
        return po;
    }

    private AiClientConfigRecord toRecord(AiClientApi po) {
        if (po == null) {
            return null;
        }
        AiClientConfigRecord record = new AiClientConfigRecord();
        BeanUtils.copyProperties(po, record);
        return record;
    }

    private List<AiClientConfigRecord> toRecords(List<AiClientApi> rows) {
        return rows == null ? List.of() : rows.stream().map(this::toRecord).toList();
    }
}
