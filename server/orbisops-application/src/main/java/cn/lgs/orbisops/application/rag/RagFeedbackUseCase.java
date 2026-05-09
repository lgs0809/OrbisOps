package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Application process manager for feedback, knowledge-gap triage and eval-case promotion. */
public final class RagFeedbackUseCase {

    private static final int MAX_LIMIT = 500;
    private static final String DEFAULT_GAP_STATUS = "OPEN";
    private static final String TRIAGED_STATUS = "TRIAGED";

    private final RagFeedbackCatalogPort catalogPort;
    private final RagFeedbackEvalCasePort evalCasePort;
    private final boolean autoInit;

    public RagFeedbackUseCase(
            RagFeedbackCatalogPort catalogPort,
            RagFeedbackEvalCasePort evalCasePort,
            boolean autoInit) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("RAG_FEEDBACK_CATALOG_PORT_REQUIRED");
        }
        if (evalCasePort == null) {
            throw new IllegalArgumentException("RAG_FEEDBACK_EVAL_CASE_PORT_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.evalCasePort = evalCasePort;
        this.autoInit = autoInit;
    }

    public void initialize() {
        if (autoInit) {
            catalogPort.ensureReady();
        }
    }

    public RagFeedbackSubmissionResult submit(RagFeedbackSubmitCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("RAG_FEEDBACK_SUBMIT_COMMAND_REQUIRED");
        }
        initialize();
        Long id = catalogPort.insertFeedback(command);
        boolean createGap = Boolean.FALSE.equals(command.useful())
                || Boolean.FALSE.equals(command.resolved());
        if (!createGap) {
            return new RagFeedbackSubmissionResult(id, false, null);
        }
        RagKnowledgeGap gap = catalogPort.upsertGap(
                command.query(),
                command.knowledgeTag(),
                command.comment());
        return new RagFeedbackSubmissionResult(id, true, gap);
    }

    public List<RagFeedbackEntry> listFeedback(
            String knowledgeTag,
            Boolean useful,
            Boolean resolved,
            int limit) {
        initialize();
        List<RagFeedbackEntry> entries = catalogPort.listFeedback(
                text(knowledgeTag), useful, resolved, safeLimit(limit));
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public List<RagKnowledgeGap> listGaps(
            String status,
            String knowledgeTag,
            int limit) {
        initialize();
        List<RagKnowledgeGap> gaps = catalogPort.listGaps(
                text(status), text(knowledgeTag), safeLimit(limit));
        return gaps == null ? List.of() : List.copyOf(gaps);
    }

    public boolean updateGapStatus(Long id, String status) {
        initialize();
        return catalogPort.updateGapStatus(id, normalizeStatus(status));
    }

    public RagQualityCaseRecord promoteGapToEvalCase(Long id) {
        initialize();
        RagKnowledgeGap gap = catalogPort.findGap(id);
        if (gap == null) {
            throw new IllegalArgumentException("知识缺口不存在：" + id);
        }
        RagQualityCaseRecord saved = evalCasePort.save(new RagQualityCaseSaveCommand(
                null,
                "知识缺口 #" + id,
                gap.queryText(),
                gap.knowledgeTag(),
                List.of(),
                8,
                true));
        updateGapStatus(id, TRIAGED_STATUS);
        return saved;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private String normalizeStatus(String status) {
        String normalized = text(status);
        return normalized.isBlank() ? DEFAULT_GAP_STATUS : normalized.toUpperCase();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
