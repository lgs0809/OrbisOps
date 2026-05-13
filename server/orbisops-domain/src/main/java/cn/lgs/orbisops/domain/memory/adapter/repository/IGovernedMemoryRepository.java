package cn.lgs.orbisops.domain.memory.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryHead;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryRuntimeQuery;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryVersionSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Persistence port for governed explicit memories, versions, conflicts and audit rows. */
public interface IGovernedMemoryRepository {

    <T> T withLogicalLock(String lockKey, Supplier<T> action);

    void appendSource(String memoryId, String idempotencyKey, String sourceRunId,
                      List<Map<String, Object>> proofRefs, String actor);

    boolean available();

    Optional<GovernedMemorySnapshot> findByIdempotencyKey(String idempotencyKey);

    Optional<GovernedMemoryHead> findLatestHead(
            MemoryScope scope,
            String scopeId,
            MemoryType type,
            String logicalKey);

    Optional<GovernedMemorySnapshot> findByMemoryId(String memoryId);

    List<GovernedMemorySnapshot> selectForRuntime(GovernedMemoryRuntimeQuery query);

    void insert(GovernedMemorySnapshot snapshot);

    void appendVersion(GovernedMemoryVersionSnapshot version);

    boolean markConflict(String memoryId);

    String recordConflict(
            MemoryScope scope,
            String scopeId,
            String logicalKey,
            String existingMemoryId,
            String incomingMemoryId,
            String actor);

    boolean verifyProjectFact(String memoryId, List<Map<String, Object>> proofRefs);

    void recordAudit(
            String memoryId,
            String action,
            String actor,
            GovernedMemorySnapshot snapshot,
            String conflictId);
}
