package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackCatalogPort;
import cn.lgs.orbisops.application.rag.RagFeedbackEntry;
import cn.lgs.orbisops.application.rag.RagFeedbackSubmitCommand;
import cn.lgs.orbisops.application.rag.RagKnowledgeGap;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagFeedbackRepository;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

import java.util.List;
import java.util.Map;

/** Trigger ACL from the legacy feedback repository Map/JSON protocol to typed Application records. */
public final class OpsRagFeedbackCatalogAdapter implements RagFeedbackCatalogPort {

    private final IRagFeedbackRepository repository;

    public OpsRagFeedbackCatalogAdapter(IRagFeedbackRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("RAG_FEEDBACK_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public void ensureReady() {
        repository.ensureTables();
    }

    @Override
    public Long insertFeedback(RagFeedbackSubmitCommand command) {
        return repository.insertFeedback(
                command.query(),
                command.answer(),
                command.useful(),
                command.resolved(),
                command.sourceType(),
                command.sourceId(),
                command.knowledgeTag(),
                JSON.toJSONString(command.chunkIds()),
                command.comment());
    }

    @Override
    public RagKnowledgeGap upsertGap(String query, String knowledgeTag, String comment) {
        return gap(repository.upsertGap(query, knowledgeTag, comment));
    }

    @Override
    public List<RagFeedbackEntry> listFeedback(
            String knowledgeTag,
            Boolean useful,
            Boolean resolved,
            int limit) {
        List<Map<String, Object>> rows = repository.listFeedback(
                knowledgeTag, useful, resolved, limit);
        return safe(rows).stream().map(this::feedback).toList();
    }

    @Override
    public List<RagKnowledgeGap> listGaps(String status, String knowledgeTag, int limit) {
        List<Map<String, Object>> rows = repository.listGaps(status, knowledgeTag, limit);
        return safe(rows).stream().map(this::gap).toList();
    }

    @Override
    public boolean updateGapStatus(Long id, String status) {
        return repository.updateGapStatus(id, status);
    }

    @Override
    public RagKnowledgeGap findGap(Long id) {
        Map<String, Object> row = repository.queryGap(id);
        return row == null || row.isEmpty() ? null : gap(row);
    }

    private RagFeedbackEntry feedback(Map<String, Object> row) {
        return new RagFeedbackEntry(
                longObject(row.get("id")),
                text(row.get("queryText")),
                text(row.get("answerText")),
                bool(row.get("useful")),
                bool(row.get("resolved")),
                text(row.get("sourceType")),
                text(row.get("sourceId")),
                text(row.get("knowledgeTag")),
                chunkIds(row.get("chunkIdsJson")),
                text(row.get("commentText")),
                text(row.get("createTime")));
    }

    private RagKnowledgeGap gap(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new RagKnowledgeGap(
                longObject(row.get("id")),
                text(row.get("gapKey")),
                text(row.get("queryText")),
                text(row.get("knowledgeTag")),
                text(row.get("status")),
                integer(row.get("feedbackCount")),
                text(row.get("sampleComment")),
                text(row.get("lastFeedbackAt")),
                text(row.get("createTime")),
                text(row.get("updateTime")));
    }

    private List<Map<String, Object>> safe(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private List<String> chunkIds(Object value) {
        String json = text(value);
        if (json.isBlank()) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            return array == null
                    ? List.of()
                    : array.stream().map(String::valueOf).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Boolean bool(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = text(value);
        if (normalized.isBlank()) {
            return null;
        }
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private Long longObject(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
