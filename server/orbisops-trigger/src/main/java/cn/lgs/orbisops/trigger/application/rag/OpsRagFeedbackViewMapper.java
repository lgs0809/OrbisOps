package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackEntry;
import cn.lgs.orbisops.application.rag.RagFeedbackSubmissionResult;
import cn.lgs.orbisops.application.rag.RagKnowledgeGap;
import cn.lgs.orbisops.application.rag.RagQualityCaseRecord;
import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility projection from typed feedback records to the legacy admin Map protocol. */
public final class OpsRagFeedbackViewMapper {

    public Map<String, Object> submissionView(RagFeedbackSubmissionResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", result.id());
        data.put("gapCreated", result.gapCreated());
        if (result.gapCreated()) {
            data.put("gap", gapView(result.gap()));
        }
        return data;
    }

    public List<Map<String, Object>> feedbackViews(List<RagFeedbackEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream().map(this::feedbackView).toList();
    }

    public List<Map<String, Object>> gapViews(List<RagKnowledgeGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }
        return gaps.stream().map(this::gapView).toList();
    }

    public Map<String, Object> caseView(RagQualityCaseRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.id());
        data.put("caseName", record.caseName());
        data.put("query", record.query());
        data.put("knowledgeTag", record.knowledgeTag());
        data.put("expectedKeywords", record.expectedKeywords());
        data.put("topK", record.topK());
        data.put("enabled", record.enabled());
        return data;
    }

    private Map<String, Object> feedbackView(RagFeedbackEntry entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entry.id());
        data.put("queryText", entry.queryText());
        data.put("answerText", entry.answerText());
        data.put("useful", databaseBoolean(entry.useful()));
        data.put("resolved", databaseBoolean(entry.resolved()));
        data.put("sourceType", entry.sourceType());
        data.put("sourceId", entry.sourceId());
        data.put("knowledgeTag", entry.knowledgeTag());
        data.put("chunkIdsJson", JSON.toJSONString(entry.chunkIds()));
        data.put("commentText", entry.commentText());
        data.put("createTime", entry.createTime());
        return data;
    }

    private Map<String, Object> gapView(RagKnowledgeGap gap) {
        if (gap == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", gap.id());
        data.put("gapKey", gap.gapKey());
        data.put("queryText", gap.queryText());
        data.put("knowledgeTag", gap.knowledgeTag());
        data.put("status", gap.status());
        data.put("feedbackCount", gap.feedbackCount());
        data.put("sampleComment", gap.sampleComment());
        data.put("lastFeedbackAt", gap.lastFeedbackAt());
        data.put("createTime", gap.createTime());
        data.put("updateTime", gap.updateTime());
        return data;
    }

    private Integer databaseBoolean(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }
}
