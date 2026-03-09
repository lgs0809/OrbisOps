package cn.lgs.orbisops.trigger.ops.memory;

import cn.lgs.orbisops.application.memory.QueryRuntimeMemoryUseCase;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** Governed-memory query facade for one runtime request. */
@Service
public class OpsMemoryRuntimeInjectionService {

    private final QueryRuntimeMemoryUseCase memoryQueries;
    private final OpsMemoryRuntimeSelectionProjector projector;
    private final OpsMemoryRuntimeInjectionSettings settings;

    public OpsMemoryRuntimeInjectionService(QueryRuntimeMemoryUseCase memoryQueries) {
        this(memoryQueries, OpsMemoryRuntimeInjectionSettings.defaults());
    }

    @Autowired
    public OpsMemoryRuntimeInjectionService(
            QueryRuntimeMemoryUseCase memoryQueries,
            OpsMemoryRuntimeInjectionSettings settings) {
        this.memoryQueries = memoryQueries;
        this.projector = new OpsMemoryRuntimeSelectionProjector();
        this.settings = settings == null
                ? OpsMemoryRuntimeInjectionSettings.defaults()
                : settings;
    }

    public OpsMemorySelection select(OpsAgentChatRequest request) {
        if (request == null) {
            return projector.project(List.of());
        }
        List<GovernedMemorySnapshot> memories = memoryQueries.select(
                request.getUserId(),
                request.getProjectId(),
                request.getSessionId(),
                request.getRunId(),
                settings.maxInjectionCount());
        return projector.project(memories);
    }
}
