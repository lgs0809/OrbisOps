package cn.lgs.orbisops.application.evidence;

import cn.lgs.orbisops.domain.evidence.adapter.repository.IToolResultRepository;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultDraft;
import cn.lgs.orbisops.domain.evidence.model.ToolResultPage;
import cn.lgs.orbisops.domain.evidence.model.ToolResultSearch;
import cn.lgs.orbisops.domain.evidence.service.ToolResultPolicy;

import java.util.List;
import java.util.Map;

public final class ToolResultApplicationService {

    private final IToolResultRepository results;
    private final EvidenceIdentityFactory identities;
    private final EvidenceAuditPort audit;
    private final EvidenceTransactionPort transactions;
    private final ToolResultPolicy policy;
    private final boolean autoInit;

    public ToolResultApplicationService(
            IToolResultRepository results,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            boolean autoInit) {
        this(results, identities, audit, transactions, autoInit, new ToolResultPolicy());
    }

    ToolResultApplicationService(
            IToolResultRepository results,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            boolean autoInit,
            ToolResultPolicy policy) {
        if (results == null) throw new IllegalArgumentException("TOOL_RESULT_REPOSITORY_REQUIRED");
        if (identities == null) throw new IllegalArgumentException("EVIDENCE_IDENTITY_FACTORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("EVIDENCE_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("EVIDENCE_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("TOOL_RESULT_POLICY_REQUIRED");
        this.results = results;
        this.identities = identities;
        this.audit = audit;
        this.transactions = transactions;
        this.policy = policy;
        this.autoInit = autoInit;
    }

    public ToolResult record(ToolResultDraft draft) {
        ToolResultPolicy.Prepared prepared = policy.prepare(draft);
        String resultId = identities.nextToolResultId();
        ToolResult result = new ToolResult(
                resultId,
                draft.projectId(), draft.sessionId(), draft.runId(), draft.userId(),
                draft.toolsetId(), draft.toolName(), draft.source(), draft.status(),
                draft.query(), prepared.inputHash(), prepared.preview(), prepared.fullOutput(),
                "db:" + resultId, prepared.outputHash(), prepared.truncated(),
                draft.durationMs(), draft.budget(), draft.actor(), identities.now());
        return transactions.required(() -> {
            ToolResult saved = results.save(result);
            audit.record(new EvidenceAuditPort.EvidenceAuditEvent(
                    saved.projectId(), "tool-result", "record", saved.resultId(),
                    Map.of(
                            "toolsetId", saved.toolsetId(),
                            "toolName", saved.toolName(),
                            "truncated", saved.truncated(),
                            "outputHash", saved.outputHash())));
            return saved;
        });
    }

    public ToolResultPage read(String resultId, String projectId, String userId, int offset, int limit) {
        ToolResult result = require(resultId);
        policy.authorize(result, projectId, userId);
        return policy.page(result, offset, limit);
    }

    public ToolResultSearch grep(String resultId, String projectId, String userId, String pattern) {
        ToolResult result = require(resultId);
        policy.authorize(result, projectId, userId);
        return policy.search(result, pattern);
    }

    public ToolResultPage slice(
            String resultId,
            String projectId,
            String userId,
            int startLine,
            int endLine) {
        return read(resultId, projectId, userId,
                Math.max(0, startLine - 1), Math.max(1, endLine - startLine + 1));
    }

    public List<ToolResult> listForRun(String projectId, String runId, int limit) {
        String project = required(projectId, "ToolResult 查询必须提供 projectId/runId");
        String run = required(runId, "ToolResult 查询必须提供 projectId/runId");
        return results.listForRun(project, run, policy.listLimit(limit));
    }

    public EvidenceStoreReadiness readiness() {
        if (results.persistent()) {
            return new EvidenceStoreReadiness(
                    "ToolResultStore", "UP", autoInit,
                    results.memoryFallbackAllowed(), "", results.count());
        }
        if (results.memoryFallbackAllowed()) {
            return new EvidenceStoreReadiness(
                    "ToolResultStore", "DEGRADED_MEMORY", autoInit,
                    true, "dev/test explicit memory fallback", results.count());
        }
        throw new IllegalStateException("ToolResultStore 未配置持久化存储");
    }

    public ToolResult require(String resultId) {
        String id = required(resultId, "TOOL_RESULT_ID_REQUIRED");
        return results.find(id)
                .orElseThrow(() -> new IllegalArgumentException("工具结果不存在：" + id));
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
