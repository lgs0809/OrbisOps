package cn.lgs.orbisops.trigger.ops.rag;

import java.util.Locale;
import java.util.Map;

/**
 * Stable policy for identifying high-value visual extraction candidates and
 * projecting the compatible fallback status stored in RAG metadata.
 */
public final class RagVisualFallbackPolicy {

    public boolean highValueCandidate(String knowledgeTag, String fileName) {
        String value = ((knowledgeTag == null ? "" : knowledgeTag)
                + " " + (fileName == null ? "" : fileName)).toLowerCase(Locale.ROOT);
        return value.contains("ops")
                || value.contains("sop")
                || value.contains("runbook")
                || value.contains("incident")
                || value.contains("failure")
                || value.contains("故障")
                || value.contains("失败")
                || value.contains("排障");
    }

    public boolean highValueCandidate(Map<String, Object> metadata) {
        return metadata != null && Boolean.TRUE.equals(metadata.get("high_value_candidate"));
    }

    public String status(Map<String, Object> metadata) {
        return highValueCandidate(metadata)
                ? "recommended_manual_enable"
                : "skipped_low_value_or_cost_gate";
    }
}
