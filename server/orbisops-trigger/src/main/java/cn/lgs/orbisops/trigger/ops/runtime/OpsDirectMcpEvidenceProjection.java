package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Keeps one business payload in graph state; immutable tool records retain the complete MCP envelope. */
final class OpsDirectMcpEvidenceProjection {
    Projection project(Map<String,Object> response,String mcpId,String toolName) {
        if (!Boolean.TRUE.equals(response.get("allowed")) || !"ALLOWED".equals(response.get("decision"))
                || !Objects.equals(mcpId,response.get("providerId")) || !Objects.equals(toolName,response.get("remoteToolName"))
                || !(response.get("normalizedContent") instanceof Map<?,?>)
                || !(response.get("mcpEnvelope") instanceof Map<?,?> envelope)
                || !"1".equals(String.valueOf(envelope.get("orbisopsResultVersion")))
                || !Boolean.FALSE.equals(envelope.get("isError"))) {
            throw new IllegalArgumentException("DIRECT_MCP_EVIDENCE_RESPONSE_INVALID");
        }
        var reference = new LinkedHashMap<String,Object>();
        for(String key:List.of("resultId","providerResultId")) {
            if (!(response.get(key) instanceof String id) || !id.matches("tool-result-[A-Za-z0-9_-]{1,100}")) invalid();
        }
        if (!Objects.equals("db:"+response.get("resultId"),response.get("fullOutputRef"))
                || !Objects.equals("db:"+response.get("providerResultId"),response.get("providerFullOutputRef"))) invalid();
        for(String key:List.of("outputHash","providerOutputHash")) {
            if (!(response.get(key) instanceof String hash) || !hash.matches("[a-f0-9]{64}")) invalid();
        }
        for(String key:List.of("allowed","decision","providerId","remoteToolName","resultId","fullOutputRef","outputHash",
                "providerResultId","providerFullOutputRef","providerOutputHash","evidenceId","providerEvidenceId")) {
            if(response.containsKey(key)) reference.put(key,response.get(key));
        }
        reference.put("outputMode","MCP_EVIDENCE_REFERENCE");
        var structured = new LinkedHashMap<>(reference);
        structured.put("normalizedContent",response.get("normalizedContent"));
        return new Projection(CanonicalJson.stringifyPreservingOrder(reference),structured);
    }
    private void invalid() { throw new IllegalArgumentException("DIRECT_MCP_EVIDENCE_REFERENCE_INVALID"); }
    record Projection(String display,Map<String,Object> structured) {}
}
