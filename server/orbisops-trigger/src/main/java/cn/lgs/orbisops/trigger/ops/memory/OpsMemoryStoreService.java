package cn.lgs.orbisops.trigger.ops.memory;

import cn.lgs.orbisops.application.memory.GovernedMemoryApplicationService;
import cn.lgs.orbisops.application.memory.GovernedMemoryCreateCommand;
import cn.lgs.orbisops.trigger.application.memory.OpsGovernedMemoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Legacy governed-memory facade limited to protocol mapping and application delegation. */
@Service
public class OpsMemoryStoreService {

    private final GovernedMemoryApplicationService applicationService;
    private final OpsGovernedMemoryMapper mapper;

    public OpsMemoryStoreService(
            GovernedMemoryApplicationService applicationService,
            OpsGovernedMemoryMapper mapper) {
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    public Map<String, Object> save(Map<String, Object> request, String actor) {
        GovernedMemoryCreateCommand command = mapper.createCommand(request, actor);
        try {
            return mapper.creationView(applicationService.create(command));
        } catch (IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("MEMORY_TYPE_")) {
                throw new IllegalArgumentException("Memory 类型不允许：" + command.memoryType().toUpperCase());
            }
            throw error;
        }
    }

    public Map<String, Object> get(String memoryId) {
        return mapper.view(applicationService.require(memoryId));
    }

    public List<Map<String, Object>> selectForRuntime(
            String userId,
            String projectId,
            String sessionId,
            String excludedSourceRunId,
            int limit) {
        return mapper.views(applicationService.selectForRuntime(mapper.runtimeQuery(
                userId,
                projectId,
                sessionId,
                excludedSourceRunId,
                limit)));
    }

    public Map<String, Object> verifyProjectFact(
            String memoryId,
            List<Map<String, Object>> proofRefs,
            String actor) {
        return mapper.view(applicationService.verifyProjectFact(
                mapper.verifyCommand(memoryId, proofRefs, actor)));
    }
}
