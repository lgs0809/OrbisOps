package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.infrastructure.dao.IAiClientModelDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientModel;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Repository
public class AiClientModelConfigRepository implements IAiClientModelConfigRepository {

    @Autowired(required = false)
    private IAiClientModelDao dao;

    @Override
    public int insert(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.insert(toPo(record));
    }

    @Override
    public int updateById(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateById(toPo(record));
    }

    @Override
    public int updateByModelId(AiClientConfigRecord record) {
        return dao == null ? 0 : dao.updateByModelId(toPo(record));
    }

    @Override
    public int deleteById(Long id) {
        return dao == null ? 0 : dao.deleteById(id);
    }

    @Override
    public int deleteByModelId(String modelId) {
        return dao == null ? 0 : dao.deleteByModelId(modelId);
    }

    @Override
    public AiClientConfigRecord queryById(Long id) {
        return dao == null ? null : toRecord(dao.queryById(id));
    }

    @Override
    public AiClientConfigRecord queryByModelId(String modelId) {
        return dao == null ? null : toRecord(dao.queryByModelId(modelId));
    }

    @Override
    public List<AiClientConfigRecord> queryByApiId(String apiId) {
        return dao == null ? List.of() : toRecords(dao.queryByApiId(apiId));
    }

    @Override
    public List<AiClientConfigRecord> queryByModelType(String modelType) {
        return dao == null ? List.of() : toRecords(dao.queryByModelType(modelType));
    }

    @Override
    public List<AiClientConfigRecord> queryEnabledModels() {
        return dao == null ? List.of() : toRecords(dao.queryEnabledModels());
    }

    @Override
    public List<AiClientConfigRecord> queryAll() {
        return dao == null ? List.of() : toRecords(dao.queryAll());
    }

    private AiClientModel toPo(AiClientConfigRecord record) {
        AiClientModel po = new AiClientModel();
        BeanUtils.copyProperties(record, po);
        return po;
    }

    private AiClientConfigRecord toRecord(AiClientModel po) {
        if (po == null) {
            return null;
        }
        AiClientConfigRecord record = new AiClientConfigRecord();
        BeanUtils.copyProperties(po, record);
        return record;
    }

    private List<AiClientConfigRecord> toRecords(List<AiClientModel> rows) {
        return rows == null ? List.of() : rows.stream().map(this::toRecord).toList();
    }
}
