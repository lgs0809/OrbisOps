package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Verifies persisted MCP observations without inferring authority from provider or tool names. */
@Component
final class OpsStoredMcpPreparationEvidence {
    private final EvidenceApplicationService evidence;
    private final ToolResultApplicationService results;

    OpsStoredMcpPreparationEvidence(EvidenceApplicationService evidence, ToolResultApplicationService results) {
        this.evidence = evidence;
        this.results = results;
    }

    List<OpsChangePackageRunEvidenceCollector.Evidence> collect(String projectId, String runId) {
        List<OpsChangePackageRunEvidenceCollector.Evidence> verified = new ArrayList<>();
        for (var item : evidence.listForRun(projectId, runId, 100)) {
            if (verified.size() >= 20) break;
            if (!item.verified() || !"MCP".equals(item.sourceType())
                    || !"MCP_REMOTE_TOOL".equals(item.metadata().get("source"))) continue;
            try {
                item.requireScope(projectId, runId);
                var result = results.require(item.toolResultId());
                if (!projectId.equals(result.projectId()) || !runId.equals(result.runId())
                        || !"MCP_REMOTE_TOOL".equals(result.source()) || !"SUCCEEDED".equals(result.status())
                        || !item.outputHash().equals(result.outputHash())
                        || !item.outputHash().equals(sha256(result.fullOutput()))
                        || !item.fullOutputRef().equals(result.fullOutputRef())
                        || !result.toolName().equals(item.metadata().get("toolName"))) continue;
                Object parsed = JSON.parse(result.fullOutput());
                if (!(parsed instanceof Map<?, ?> envelope)
                        || !(envelope.get("orbisopsResultVersion") instanceof Number version) || version.intValue() != 1
                        || !Boolean.FALSE.equals(envelope.get("isError"))
                        || !(envelope.get("normalizedContent") instanceof Map<?, ?> content) || content.isEmpty()) continue;
                if (envelope.get("_meta") instanceof Map<?, ?> meta && Boolean.TRUE.equals(meta.get("fixture"))) continue;
                String provider = String.valueOf(item.metadata().getOrDefault("mcpId", ""));
                if (provider.isBlank() || !result.toolsetId().equals("mcp." + provider)) continue;
                String summary = JSON.toJSONString(content);
                verified.add(new OpsChangePackageRunEvidenceCollector.Evidence(item.evidenceId(), "MCP_REMOTE",
                        result.resultId(), summary.substring(0, Math.min(1800, summary.length())), item.createdAt(),
                        "sha256:" + result.outputHash(), Map.of("verified", true, "resultId", result.resultId(),
                        "evidenceId", item.evidenceId(), "outputHash", result.outputHash(),
                        "fullOutputRef", result.fullOutputRef(), "mcpId", provider, "remoteToolName", result.toolName())));
            } catch (RuntimeException invalid) {
                // Missing, cross-scope, corrupt or invalid envelopes cannot become preparation proof.
            }
        }
        return List.copyOf(verified);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception unavailable) { throw new IllegalStateException(unavailable); }
    }
}
