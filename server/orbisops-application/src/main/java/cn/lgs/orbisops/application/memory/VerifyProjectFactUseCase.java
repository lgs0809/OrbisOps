package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryType;

import java.util.List;
import java.util.Map;

public final class VerifyProjectFactUseCase {

    private final GovernedMemoryApplicationService memoryApplication;

    public VerifyProjectFactUseCase(GovernedMemoryApplicationService memoryApplication) {
        if (memoryApplication == null) {
            throw new IllegalArgumentException("GOVERNED_MEMORY_APPLICATION_REQUIRED");
        }
        this.memoryApplication = memoryApplication;
    }

    public GovernedMemorySnapshot verify(
            String memoryId,
            List<Map<String, Object>> proofRefs,
            String actor) {
        String id = required(memoryId, "MEMORY_ID_REQUIRED");
        GovernedMemorySnapshot current = memoryApplication.require(id);
        if (current.type() != MemoryType.PROJECT_FACT) {
            throw new IllegalArgumentException("MEMORY_PROJECT_FACT_REQUIRED");
        }
        if (proofRefs == null || proofRefs.isEmpty()) {
            throw new IllegalArgumentException("MEMORY_PROJECT_FACT_PROOF_REQUIRED");
        }
        return memoryApplication.verifyProjectFact(new GovernedMemoryVerifyCommand(
                id,
                List.copyOf(proofRefs),
                required(actor, "MEMORY_ACTOR_REQUIRED")));
    }

    private String required(String input, String error) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }
}
