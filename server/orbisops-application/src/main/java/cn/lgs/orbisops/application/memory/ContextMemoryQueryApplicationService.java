package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;

import java.util.List;

/** Application use case for generic Context Memory search and identity lookup. */
public class ContextMemoryQueryApplicationService {

    private final ContextMemoryStoreApplicationService storeService;
    private final ContextMemoryDefinitionPolicy definitionPolicy;

    public ContextMemoryQueryApplicationService(ContextMemoryStoreApplicationService storeService,
                                                ContextMemoryDefinitionPolicy definitionPolicy) {
        this.storeService = storeService;
        this.definitionPolicy = definitionPolicy == null
                ? new ContextMemoryDefinitionPolicy()
                : definitionPolicy;
    }

    public List<ContextMemorySnapshot> search(ContextMemoryQuery query) {
        if (query == null || storeService == null) return List.of();
        return storeService.search(new ContextMemorySearchCriteria(
                definitionPolicy.normalizeScope(query.scopeType(), false),
                query.scopeId(),
                definitionPolicy.normalizeMemoryType(query.memoryType(), false),
                definitionPolicy.normalizeStatus(query.status(), false),
                query.limit()));
    }

    public ContextMemorySnapshot require(String memoryId) {
        if (storeService == null) throw new IllegalStateException("Context Memory 数据库未配置");
        return storeService.require(memoryId);
    }
}
