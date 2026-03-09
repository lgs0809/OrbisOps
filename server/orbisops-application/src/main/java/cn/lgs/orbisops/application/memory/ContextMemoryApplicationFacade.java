package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;

import java.util.List;

/** Single typed Application entry point for all Context Memory use cases. */
public class ContextMemoryApplicationFacade {

    private final ContextMemoryStoreApplicationService storeService;
    private final ContextMemoryQueryApplicationService queryService;
    private final ContextMemoryAdminApplicationService adminService;
    private final ContextMemorySceneQueryApplicationService sceneQueryService;

    public ContextMemoryApplicationFacade(ContextMemoryStoreApplicationService storeService,
                                          ContextMemoryQueryApplicationService queryService,
                                          ContextMemoryAdminApplicationService adminService,
                                          ContextMemorySceneQueryApplicationService sceneQueryService) {
        this.storeService = storeService;
        this.queryService = queryService;
        this.adminService = adminService;
        this.sceneQueryService = sceneQueryService;
    }

    public List<ContextMemorySnapshot> search(ContextMemoryQuery query) {
        return queryService == null ? List.of() : queryService.search(query);
    }

    public ContextMemorySnapshot require(String memoryId) {
        if (queryService == null) throw unavailable();
        return queryService.require(memoryId);
    }

    public ContextMemorySnapshot create(ContextMemoryMutationCommand command) {
        if (adminService == null) throw unavailable();
        return adminService.create(command);
    }

    public ContextMemorySnapshot update(String memoryId, ContextMemoryMutationCommand command) {
        if (adminService == null) throw unavailable();
        return adminService.update(memoryId, command);
    }

    public ContextMemorySnapshot updateStatus(String memoryId, String status) {
        if (adminService == null) throw unavailable();
        return adminService.updateStatus(memoryId, status);
    }

    public List<ContextMemorySnapshot> queryScene(ContextMemorySceneQuery query) {
        return sceneQueryService == null ? List.of() : sceneQueryService.query(query);
    }

    public void saveExtractedItems(List<ColdMemoryItemSnapshot> items) {
        if (storeService != null) storeService.saveExtractedItems(items);
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException("Context Memory 数据库未配置");
    }
}
