package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;

import java.util.List;

/** Secondary port for lexical semantic-memory recall. */
@FunctionalInterface
public interface SemanticLexicalRecallPort {

    List<SemanticMemoryRankedCandidate> recallLexical(
            String sessionId,
            String userId,
            String query,
            int recallLimit);
}
