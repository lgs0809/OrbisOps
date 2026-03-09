package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.ContextMemoryApplicationFacade;
import cn.lgs.orbisops.application.memory.ContextMemoryQuery;
import cn.lgs.orbisops.application.memory.ContextMemorySceneQuery;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.trigger.application.memory.OpsContextMemoryCommandMapper;
import cn.lgs.orbisops.trigger.application.memory.OpsContextMemoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpsContextMemoryService {

    private final ContextMemoryApplicationFacade applicationFacade;
    private final OpsContextMemoryMapper mapper = new OpsContextMemoryMapper();
    private final OpsContextMemoryCommandMapper commandMapper = new OpsContextMemoryCommandMapper();

    public OpsContextMemoryService(ContextMemoryApplicationFacade applicationFacade) {
        this.applicationFacade = applicationFacade;
    }

    public List<Map<String, Object>> list(String scopeType,
                                          String scopeId,
                                          String memoryType,
                                          String status,
                                          int limit) {
        if (applicationFacade == null) return List.of();
        return mapper.views(applicationFacade.search(new ContextMemoryQuery(
                scopeType,
                scopeId,
                memoryType,
                status,
                limit)));
    }

    public Map<String, Object> get(String memoryId) {
        return mapper.view(requireFacade().require(memoryId));
    }

    public Map<String, Object> create(Map<String, Object> request) {
        return mapper.view(requireFacade().create(commandMapper.command(request)));
    }

    public Map<String, Object> update(String memoryId, Map<String, Object> request) {
        return mapper.view(requireFacade().update(memoryId, commandMapper.command(request)));
    }

    public Map<String, Object> updateStatus(String memoryId, String status) {
        return mapper.view(requireFacade().updateStatus(memoryId, status));
    }

    public List<Map<String, Object>> listForScene(String scene, String userId, String projectId, int limit) {
        if (applicationFacade == null) return List.of();
        return mapper.views(applicationFacade.queryScene(new ContextMemorySceneQuery(
                scene,
                userId,
                projectId,
                limit)));
    }

    public void saveExtractedItems(List<ColdMemoryItemSnapshot> items) {
        if (applicationFacade != null) applicationFacade.saveExtractedItems(items);
    }

    private ContextMemoryApplicationFacade requireFacade() {
        if (applicationFacade == null) throw new IllegalStateException("Context Memory 数据库未配置");
        return applicationFacade;
    }
}
