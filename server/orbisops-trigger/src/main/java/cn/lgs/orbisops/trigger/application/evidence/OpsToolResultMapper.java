package cn.lgs.orbisops.trigger.application.evidence;

import cn.lgs.orbisops.application.evidence.EvidenceStoreReadiness;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultBudget;
import cn.lgs.orbisops.domain.evidence.model.ToolResultDraft;
import cn.lgs.orbisops.domain.evidence.model.ToolResultPage;
import cn.lgs.orbisops.domain.evidence.model.ToolResultSearch;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolOutputBudget;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsToolResultMapper {

    public ToolResultDraft draft(
            String projectId,
            String sessionId,
            String runId,
            String userId,
            String toolsetId,
            String toolName,
            String source,
            String query,
            String output,
            OpsToolOutputBudget budget,
            String actor,
            String status,
            long durationMs) {
        OpsToolOutputBudget safe = budget == null ? OpsToolOutputBudget.builder().build() : budget;
        return new ToolResultDraft(
                projectId, sessionId, runId, userId, toolsetId, toolName, source, status,
                query, output,
                new ToolResultBudget(
                        safe.getMaxRows(), safe.getMaxBytes(), safe.getMaxLines(),
                        safe.getMaxPoints(), safe.getMaxTimeRangeMinutes()),
                actor, durationMs);
    }

    public Map<String, Object> view(ToolResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultId", result.resultId());
        data.put("projectId", result.projectId());
        data.put("sessionId", result.sessionId());
        data.put("runId", result.runId());
        data.put("userId", result.userId());
        data.put("toolsetId", result.toolsetId());
        data.put("toolName", result.toolName());
        data.put("source", result.source());
        data.put("status", result.status());
        data.put("query", result.query());
        data.put("inputHash", result.inputHash());
        data.put("preview", result.preview());
        data.put("fullOutputRef", result.fullOutputRef());
        data.put("outputHash", result.outputHash());
        data.put("truncated", result.truncated());
        data.put("durationMs", result.durationMs());
        data.put("maxRows", result.budget().maxRows());
        data.put("maxBytes", result.budget().maxBytes());
        data.put("maxLines", result.budget().maxLines());
        data.put("maxPoints", result.budget().maxPoints());
        data.put("maxTimeRange", result.budget().maxTimeRangeMinutes());
        data.put("createdBy", result.createdBy());
        data.put("createdAt", result.createdAt());
        return data;
    }

    public List<Map<String, Object>> views(List<ToolResult> results) {
        return results == null ? List.of() : results.stream().map(this::view).toList();
    }

    public Map<String, Object> view(ToolResultPage page) {
        return Map.of(
                "resultId", page.resultId(),
                "offset", page.offset(),
                "limit", page.limit(),
                "lines", page.lines(),
                "totalLines", page.totalLines(),
                "outputHash", page.outputHash());
    }

    public Map<String, Object> view(ToolResultSearch search) {
        List<Map<String, Object>> hits = search.hits().stream()
                .map(hit -> Map.<String, Object>of("line", hit.line(), "snippet", hit.snippet()))
                .toList();
        return Map.of("resultId", search.resultId(), "hits", hits, "count", hits.size());
    }

    public Map<String, Object> view(EvidenceStoreReadiness readiness) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("store", readiness.store());
        data.put("autoInit", readiness.autoInit());
        data.put("memoryFallbackAllowed", readiness.memoryFallbackAllowed());
        data.put("status", readiness.status());
        if (!readiness.reason().isBlank()) data.put("reason", readiness.reason());
        data.put("recordCount", readiness.recordCount());
        return data;
    }
}
