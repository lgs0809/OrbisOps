package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryQueryCommand;
import cn.lgs.orbisops.application.memory.MemoryQueryResult;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;

import java.util.Map;

/** Trigger compatibility mapper for the unified Memory Query Application use case. */
public class OpsMemoryQueryMapper {

    private final OpsMemorySelectionReferenceMapper referenceMapper =
            new OpsMemorySelectionReferenceMapper();

    public MemoryQueryCommand command(String sessionId,
                                      String userId,
                                      String query,
                                      Map<String, Object> metadata,
                                      int itemMatchLimit,
                                      int hotMessageLimit,
                                      int semanticTopK,
                                      int contextMaxChars,
                                      long timeoutMillis,
                                      boolean recencyAware,
                                      double recencyHalfLifeTurns) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        return new MemoryQueryCommand(
                sessionId,
                userId,
                query,
                value(safeMetadata.get("scene")),
                value(safeMetadata.get("taskType")),
                value(safeMetadata.get("projectId")),
                itemMatchLimit,
                hotMessageLimit,
                semanticTopK,
                contextMaxChars,
                timeoutMillis,
                recencyAware,
                recencyHalfLifeTurns);
    }

    public OpsMemorySelection selection(MemoryQueryResult result) {
        MemoryQueryResult safeResult = result == null ? MemoryQueryResult.empty() : result;
        return new OpsMemorySelection(
                safeResult.context(),
                referenceMapper.views(safeResult.references()));
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
