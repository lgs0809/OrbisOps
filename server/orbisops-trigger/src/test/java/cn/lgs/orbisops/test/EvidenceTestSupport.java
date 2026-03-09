package cn.lgs.orbisops.test;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.EvidenceAuditPort;
import cn.lgs.orbisops.application.evidence.EvidenceIdentityFactory;
import cn.lgs.orbisops.application.evidence.EvidenceTransactionPort;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.application.evidence.TrustedProofApplicationService;
import cn.lgs.orbisops.domain.evidence.adapter.repository.IEvidenceRepository;
import cn.lgs.orbisops.domain.evidence.adapter.repository.IToolResultRepository;
import cn.lgs.orbisops.domain.evidence.adapter.repository.ITrustedProofRepository;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.TrustedProof;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofCriteria;
import cn.lgs.orbisops.trigger.application.evidence.OpsEvidenceMapper;
import cn.lgs.orbisops.trigger.application.evidence.OpsToolResultMapper;
import cn.lgs.orbisops.trigger.application.evidence.OpsTrustedProofMapper;
import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class EvidenceTestSupport {

    private EvidenceTestSupport() {
    }

    public static OpsToolResultStore toolResultStore() {
        Ports ports = new Ports();
        return new OpsToolResultStore(
                new ToolResultApplicationService(ports.toolResults, ports.identities, ports, ports, false),
                new OpsToolResultMapper());
    }

    public static OpsEvidenceStore evidenceStore() {
        Ports ports = new Ports();
        return new OpsEvidenceStore(
                new EvidenceApplicationService(ports.evidence, ports.identities, ports, ports, false),
                new OpsEvidenceMapper());
    }

    public static OpsTrustedProofService trustedProofService() {
        Ports ports = new Ports();
        return new OpsTrustedProofService(
                new TrustedProofApplicationService(ports.proofs, ports.identities, ports, ports, false),
                new OpsTrustedProofMapper());
    }

    private static final class Ports implements EvidenceAuditPort, EvidenceTransactionPort {
        private final AtomicInteger ids = new AtomicInteger();
        private final EvidenceIdentityFactory identities = new EvidenceIdentityFactory(
                () -> "test-" + ids.incrementAndGet(),
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC));
        private final InMemoryToolResults toolResults = new InMemoryToolResults();
        private final InMemoryEvidence evidence = new InMemoryEvidence();
        private final InMemoryProofs proofs = new InMemoryProofs();

        @Override public void record(EvidenceAuditEvent event) { }
        @Override public <T> T required(Supplier<T> action) { return action.get(); }
    }

    private static final class InMemoryToolResults implements IToolResultRepository {
        private final Map<String, ToolResult> values = new ConcurrentHashMap<>();
        @Override public ToolResult save(ToolResult result) { values.put(result.resultId(), result); return result; }
        @Override public Optional<ToolResult> find(String resultId) { return Optional.ofNullable(values.get(resultId)); }
        @Override public List<ToolResult> listForRun(String projectId, String runId, int limit) {
            return values.values().stream().filter(item -> projectId.equals(item.projectId()) && runId.equals(item.runId())).limit(limit).toList();
        }
        @Override public long count() { return values.size(); }
        @Override public boolean persistent() { return false; }
        @Override public boolean memoryFallbackAllowed() { return true; }
    }

    private static final class InMemoryEvidence implements IEvidenceRepository {
        private final Map<String, EvidenceRecord> values = new ConcurrentHashMap<>();
        @Override public EvidenceRecord save(EvidenceRecord evidence) { values.put(evidence.evidenceId(), evidence); return evidence; }
        @Override public Optional<EvidenceRecord> findByIdempotencyKey(String key) {
            return values.values().stream().filter(item -> key.equals(item.idempotencyKey())).findFirst();
        }
        @Override public Optional<EvidenceRecord> findScoped(String id, String projectId, String runId) {
            return Optional.ofNullable(values.get(id)).filter(item -> projectId.equals(item.projectId()) && runId.equals(item.runId()));
        }
        @Override public List<EvidenceRecord> listForRun(String projectId, String runId, int limit) {
            return values.values().stream().filter(item -> projectId.equals(item.projectId()) && runId.equals(item.runId()))
                    .sorted(Comparator.comparing(EvidenceRecord::createdAt).reversed()).limit(limit).toList();
        }
        @Override public long count() { return values.size(); }
    }

    private static final class InMemoryProofs implements ITrustedProofRepository {
        private final Map<String, TrustedProof> values = new ConcurrentHashMap<>();
        @Override public TrustedProof save(TrustedProof proof) { values.put(proof.proofId(), proof); return proof; }
        @Override public Optional<TrustedProof> find(TrustedProofCriteria criteria) {
            return values.values().stream().filter(item -> item.matches(criteria)).findFirst();
        }
        @Override public long count() { return values.size(); }
        @Override public boolean persistent() { return false; }
        @Override public boolean memoryFallbackAllowed() { return true; }
    }
}
