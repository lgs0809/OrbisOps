package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationProofPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class OpsChangePackagePreparationProofAdapter implements ChangePackagePreparationProofPort {

    private final OpsTrustedProofService trustedProofService;

    public OpsChangePackagePreparationProofAdapter(
            ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider) {
        this.trustedProofService = trustedProofServiceProvider.getIfAvailable();
    }

    @Override
    public void recordExecutionProofs(String packageId,
                                      int version,
                                      String packageHash,
                                      String projectId,
                                      ChangePackageSnapshot snapshot,
                                      String actor) {
        Map<String, Object> values = snapshot.toMap();
        List<Map<String, Object>> legacyPassed = legacyPrepareDryRunSteps(values);
        List<Map<String, Object>> authoritativePassed = authoritativeMcpDryRunEvidence(values);
        if (legacyPassed.isEmpty() && authoritativePassed.isEmpty()) return;
        if (trustedProofService == null) {
            throw new IllegalStateException(
                    "TrustedProofService 未初始化，PREPARE_DRY_RUN proof 必须 fail closed");
        }

        Set<String> recordedResultIds = new LinkedHashSet<>();
        for (Map<String, Object> step : legacyPassed) {
            String resultId = text(step.get("resultId"));
            if (!recordedResultIds.add(resultId)) continue;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("operationId", text(step.get("operationId")));
            metadata.put("mcpId", text(step.get("mcpId")));
            metadata.put("toolName", text(step.get("toolName")));
            metadata.put("resultId", resultId);
            metadata.put("outputHash", text(step.get("outputHash")));
            metadata.put("sourcePackageVersion", version);
            metadata.put("sourcePackageHash", packageHash);
            recordProof(
                    packageId,
                    version,
                    packageHash,
                    projectId,
                    firstNonBlank(step.get("riskLevel"), values.get("riskLevel"), "MEDIUM"),
                    "TOOL_EXECUTED",
                    resultId,
                    text(step.get("outputHash")),
                    metadata,
                    actor);
        }
        for (Map<String, Object> evidence : authoritativePassed) {
            String resultId = text(evidence.get("toolResultId"));
            if (!recordedResultIds.add(resultId)) continue;
            Map<String, Object> evidenceMetadata = object(evidence.get("metadata"));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceEvidenceId", text(evidence.get("evidenceId")));
            metadata.put("sourceRunId", text(evidence.get("runId")));
            metadata.put("source", text(evidenceMetadata.get("source")));
            metadata.put("mcpId", mcpIdFromExecutionSource(text(evidenceMetadata.get("source"))));
            metadata.put("toolName", text(evidenceMetadata.get("toolName")));
            metadata.put("resultId", resultId);
            metadata.put("outputHash", text(evidence.get("outputHash")));
            metadata.put("fullOutputRef", text(evidence.get("fullOutputRef")));
            metadata.put("sourcePackageVersion", version);
            metadata.put("sourcePackageHash", packageHash);
            recordProof(
                    packageId,
                    version,
                    packageHash,
                    projectId,
                    firstNonBlank(values.get("riskLevel"), "MEDIUM"),
                    "TOOL_EXECUTED",
                    resultId,
                    text(evidence.get("outputHash")),
                    metadata,
                    actor);
        }
    }

    private List<Map<String, Object>> legacyPrepareDryRunSteps(Map<String, Object> values) {
        Map<String, Object> dryRun = object(values.get("dryRunResultJson"));
        if (!"PREPARE_DRY_RUN".equals(text(dryRun.get("type")))
                || !"PASSED".equalsIgnoreCase(text(dryRun.get("status")))) {
            return List.of();
        }
        return operations(dryRun.get("steps")).stream()
                .filter(step -> Boolean.TRUE.equals(step.get("executed")))
                .filter(step -> "PASSED".equalsIgnoreCase(text(step.get("status"))))
                .filter(step -> !text(step.get("resultId")).isBlank())
                .filter(step -> validHash(text(step.get("outputHash"))))
                .toList();
    }

    private List<Map<String, Object>> authoritativeMcpDryRunEvidence(Map<String, Object> values) {
        Set<String> allowedExecutionSources = new LinkedHashSet<>();
        for (Map<String, Object> operation : operations(values.get("mcpStepsJson"))) {
            if (!"DRY_RUN".equalsIgnoreCase(text(operation.get("effectType")))) continue;
            if (Boolean.TRUE.equals(operation.get("writesTargetResource"))) continue;
            String mcpId = text(operation.get("mcpId"));
            String toolName = firstNonBlank(operation.get("remoteToolName"), operation.get("toolName"));
            if (mcpId.isBlank() || toolName.isBlank()) continue;
            allowedExecutionSources.add("PRE_APPROVAL_WORKFLOW:mcp." + mcpId + ":" + toolName);
        }
        if (allowedExecutionSources.isEmpty()) return List.of();

        Map<String, Object> evidenceRoot = object(values.get("evidenceJson"));
        return operations(evidenceRoot.get("trustedEvidence")).stream()
                .filter(evidence -> Boolean.TRUE.equals(evidence.get("verified")))
                .filter(evidence -> "MCP".equalsIgnoreCase(text(evidence.get("sourceType"))))
                .filter(evidence -> !text(evidence.get("toolResultId")).isBlank())
                .filter(evidence -> validHash(text(evidence.get("outputHash"))))
                .filter(evidence -> text(evidence.get("fullOutputRef")).startsWith("db:"))
                .filter(evidence -> {
                    Map<String, Object> metadata = object(evidence.get("metadata"));
                    String source = text(metadata.get("source"));
                    String toolName = text(metadata.get("toolName"));
                    return !toolName.isBlank()
                            && allowedExecutionSources.contains(source)
                            && source.endsWith(":" + toolName);
                })
                .toList();
    }

    private void recordProof(String packageId,
                             int version,
                             String packageHash,
                             String projectId,
                             String riskLevel,
                             String source,
                             String resultId,
                             String outputHash,
                             Map<String, Object> metadata,
                             String actor) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("proofId", "proof-" + UUID.randomUUID());
        proof.put("projectId", projectId);
        proof.put("packageId", packageId);
        proof.put("packageVersion", version);
        proof.put("packageHash", packageHash);
        proof.put("riskLevel", riskLevel);
        proof.put("proofType", "MCP_DRY_RUN");
        proof.put("source", source);
        proof.put("externalRunId", resultId);
        proof.put("resultStatus", "PASSED");
        proof.put("outputHash", outputHash);
        proof.put("metadata", metadata);
        trustedProofService.recordTrustedProof(proof, actor);
    }

    private String mcpIdFromExecutionSource(String source) {
        String prefix = "PRE_APPROVAL_WORKFLOW:mcp.";
        if (!source.startsWith(prefix)) return "";
        int toolSeparator = source.lastIndexOf(':');
        if (toolSeparator <= prefix.length()) return "";
        return source.substring(prefix.length(), toolSeparator);
    }

    private boolean validHash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private Map<String, Object> object(Object raw) {
        if (raw instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        String value = text(raw);
        if (value.isBlank() || !value.startsWith("{")) return Map.of();
        Map<String, Object> parsed = JSON.parseObject(value);
        return parsed == null ? Map.of() : parsed;
    }

    private List<Map<String, Object>> operations(Object raw) {
        Object value = raw;
        if (raw instanceof String text && !text.isBlank()) value = JSON.parseArray(text);
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> source) {
                Map<String, Object> operation = new LinkedHashMap<>();
                source.forEach((key, entry) -> operation.put(String.valueOf(key), entry));
                result.add(operation);
            }
        }
        return result;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
