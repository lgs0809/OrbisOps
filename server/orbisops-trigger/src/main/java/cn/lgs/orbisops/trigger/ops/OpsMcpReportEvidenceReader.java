package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.evidence.adapter.repository.IToolResultRepository;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reconstructs query receipts for both existing and new runs without rewriting their history. */
@Component
public final class OpsMcpReportEvidenceReader {
    private final IToolResultRepository results;

    public OpsMcpReportEvidenceReader(IToolResultRepository results) { this.results = results; }

    public List<Map<String, Object>> read(OpsAnalysisResponseDTO response) {
        if (response.getAnalysisId() == null || response.getAnalysisId().isBlank()) return List.of();
        var evidence = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<String>();
        for (var step : Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of())) {
            if (step == null || !"TOOL_CALL_FINISHED".equals(step.getEventType())
                    || !"SUCCEEDED".equals(step.getStatus()) || step.getResultId() == null
                    || step.getResultId().isBlank() || !seen.add(step.getResultId())) continue;
            // Bound database reads even for a malformed or unusually large historical response.
            if (seen.size() > 100) break;
            var saved = results.find(step.getResultId()).orElse(null);
            if (!valid(saved, response.getAnalysisId()) || !saved.source().startsWith("PRE_APPROVAL_WORKFLOW:mcp.")) continue;
            Map<String, Object> wrapper = object(saved.fullOutput());
            if (!Boolean.TRUE.equals(wrapper.get("allowed")) || !"ALLOWED".equals(wrapper.get("decision"))
                    || !"MCP".equals(wrapper.get("providerType"))) continue;
            String provider = text(wrapper.get("providerId"));
            String tool = text(wrapper.get("remoteToolName"));
            if (provider.isBlank() || tool.isBlank() || !tool.equals(saved.toolName())
                    || !saved.source().equals("PRE_APPROVAL_WORKFLOW:mcp." + provider + ":" + tool)) continue;
            String providerResultId = text(wrapper.get("providerResultId"));
            if (providerResultId.isBlank()) continue;
            var remote = results.find(providerResultId).orElse(null);
            if (!valid(remote, saved.runId()) || !saved.projectId().equals(remote.projectId())
                    || !saved.toolName().equals(remote.toolName()) || !"MCP_REMOTE_TOOL".equals(remote.source())
                    || !remote.outputHash().equals(wrapper.get("providerOutputHash"))
                    || !remote.fullOutputRef().equals(wrapper.get("providerFullOutputRef"))) continue;
            var envelope = object(remote.fullOutput());
            if (!"1".equals(text(envelope.get("orbisopsResultVersion"))) || !Boolean.FALSE.equals(envelope.get("isError"))) continue;
            String source = "mcp." + provider + ":" + tool;
            var item = new LinkedHashMap<String, Object>();
            item.put("id", "ev-" + saved.resultId());
            item.put("resultId", saved.resultId());
            item.put("outputHash", saved.outputHash());
            item.put("source", source);
            item.put("sourceName", "MCP · " + tool);
            item.put("agent", step.getAgent());
            item.put("status", "SUCCEEDED");
            item.put("receiptOnly", true);
            item.put("text", "MCP 查询 " + tool + " 已返回；已核对同一次运行的完整回执及内容哈希。查询成功不代表业务正常。");
            evidence.add(item);
        }
        return List.copyOf(evidence);
    }

    private boolean valid(ToolResult value, String runId) {
        return value != null && Objects.equals(runId, value.runId()) && "SUCCEEDED".equals(value.status())
                && ("db:" + value.resultId()).equals(value.fullOutputRef())
                && sha256(value.fullOutput()).equals(value.outputHash());
    }

    private Map<String, Object> object(String raw) {
        try { return CanonicalJson.parseObject(raw); }
        catch (IllegalArgumentException invalid) { return Map.of(); }
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
