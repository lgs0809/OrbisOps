package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackSubmitCommand;
import cn.lgs.orbisops.application.rag.RagFeedbackUseCase;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Legacy admin Map compatibility facade over the typed feedback Application process manager. */
@Service
public class RagFeedbackService {

    private final RagFeedbackUseCase useCase;
    private final OpsRagFeedbackViewMapper viewMapper;

    public RagFeedbackService(OpsRagFeedbackManagementAssembly assembly) {
        if (assembly == null) {
            throw new IllegalArgumentException("RAG_FEEDBACK_ASSEMBLY_REQUIRED");
        }
        this.useCase = assembly.useCase();
        this.viewMapper = assembly.viewMapper();
    }

    @PostConstruct
    public void ensureTables() {
        useCase.initialize();
    }

    public Map<String, Object> submitFeedback(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        RagFeedbackSubmitCommand command = new RagFeedbackSubmitCommand(
                text(safe.get("query")),
                text(safe.get("answer")),
                boolObject(safe.get("useful")),
                boolObject(safe.get("resolved")),
                text(safe.get("sourceType")),
                text(safe.get("sourceId")),
                text(safe.get("knowledgeTag")),
                stringList(safe.get("chunkIds")),
                text(safe.get("comment")));
        return viewMapper.submissionView(useCase.submit(command));
    }

    public List<Map<String, Object>> listFeedback(
            String knowledgeTag,
            Boolean useful,
            Boolean resolved,
            int limit) {
        return viewMapper.feedbackViews(
                useCase.listFeedback(knowledgeTag, useful, resolved, limit));
    }

    public List<Map<String, Object>> listGaps(
            String status,
            String knowledgeTag,
            int limit) {
        return viewMapper.gapViews(useCase.listGaps(status, knowledgeTag, limit));
    }

    public boolean updateGapStatus(Long id, String status) {
        return useCase.updateGapStatus(id, status);
    }

    public Map<String, Object> saveGapAsEvalCase(Long id) {
        return viewMapper.caseView(useCase.promoteGapToEvalCase(id));
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .toList();
        }
        return List.of(String.valueOf(value));
    }

    private Boolean boolObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = text(value);
        if (normalized.isBlank()) {
            return null;
        }
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
