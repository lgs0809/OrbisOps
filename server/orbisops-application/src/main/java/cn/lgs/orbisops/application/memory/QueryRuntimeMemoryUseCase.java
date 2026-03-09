package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryRuntimeQuery;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;

import java.util.List;

public final class QueryRuntimeMemoryUseCase {

    private final GovernedMemoryApplicationService memoryApplication;

    public QueryRuntimeMemoryUseCase(GovernedMemoryApplicationService memoryApplication) {
        if (memoryApplication == null) {
            throw new IllegalArgumentException("GOVERNED_MEMORY_APPLICATION_REQUIRED");
        }
        this.memoryApplication = memoryApplication;
    }

    public List<GovernedMemorySnapshot> select(
            String userId,
            String projectId,
            String sessionId,
            String excludedSourceRunId,
            int limit) {
        return memoryApplication.selectForRuntime(new GovernedMemoryRuntimeQuery(
                userId,
                projectId,
                sessionId,
                excludedSourceRunId,
                limit));
    }
}
