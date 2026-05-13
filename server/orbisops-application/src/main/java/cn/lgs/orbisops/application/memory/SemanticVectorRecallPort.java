package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;

import java.util.List;

/** Secondary port for vector semantic-memory recall. */
@FunctionalInterface
public interface SemanticVectorRecallPort {

    List<SemanticMemoryRankedCandidate> recallVector(
            String sessionId,
            String userId,
            String query,
            int recallLimit);
}
