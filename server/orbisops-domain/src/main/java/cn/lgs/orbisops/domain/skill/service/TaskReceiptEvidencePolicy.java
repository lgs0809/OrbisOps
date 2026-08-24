package cn.lgs.orbisops.domain.skill.service;

import java.util.*;

/** Business evidence inside the common MCP envelope, independent of any particular tool's fields. */
public final class TaskReceiptEvidencePolicy {
    public Optional<Map<String,Object>> content(String project, Map<String,Object> envelope) {
        if (!"1".equals(String.valueOf(envelope.get("orbisopsResultVersion")))
                || !Boolean.FALSE.equals(envelope.get("isError"))
                || !(envelope.get("normalizedContent") instanceof Map<?,?>)) return Optional.empty();
        Map<String,Object> body = object(envelope.get("normalizedContent"));
        if (body.isEmpty()) return Optional.empty();
        Map<String,Object> scope = object(body.get("scope"));
        // The database join establishes project/task ownership. A provider's explicit
        // project claim may contradict it, but can never grant another project's authority.
        if (scope.containsKey("projectId") && !project.equals(scope.get("projectId")))
            throw new SecurityException("TASK_ACCEPTANCE_EVIDENCE_SCOPE_FORBIDDEN");
        // Retain the original scoped-observation protocol, including unavailable results.
        // Generic MCP business payloads do not have to invent this protocol's status/scope.
        if (scopedObservation(body) && !"AVAILABLE".equals(body.get("status"))) return Optional.empty();
        return Optional.of(body);
    }

    public Map<String,Object> condition(String toolName, Map<String,Object> body) {
        if (scopedObservation(body)) {
            var scope = object(body.get("scope"));
            // Preserve existing acceptance ledger hashes and condition identities.
            return Map.of("environment", text(scope.get("environment")), "service", text(scope.get("serviceId")),
                    "resource", text(body.get("resourceIdentity")));
        }
        // Do not infer semantic conditions from request IDs, arbitrary body fields or
        // expected values. The durable tool identity is the only generic fallback.
        return Map.of("environment", "", "service", "", "resource", "mcp-tool:" + toolName);
    }

    public static boolean scopedObservation(Map<String,Object> body) {
        return Set.of("AVAILABLE", "UNAVAILABLE").contains(text(body.get("status")))
                && object(body.get("scope")).containsKey("projectId");
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {
        return value instanceof Map<?,?> map ? (Map<String,Object>)map : Map.of();
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
