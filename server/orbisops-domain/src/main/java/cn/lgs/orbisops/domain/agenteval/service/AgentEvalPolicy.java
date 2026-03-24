package cn.lgs.orbisops.domain.agenteval.service;

import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseExecution;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalEdge;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalNode;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentEvalPolicy {

    public AgentEvalCaseResult evaluate(
            AgentEvalDefinitionSnapshot definition,
            AgentEvalCase evalCase) {
        if (definition == null) throw new IllegalArgumentException("AGENT_EVAL_DEFINITION_REQUIRED");
        if (evalCase == null) throw new IllegalArgumentException("AGENT_EVAL_CASE_REQUIRED");
        List<String> reasons = new ArrayList<>();

        Set<String> nodeIds = definition.nodes().stream()
                .map(AgentEvalNode::nodeId)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String requiredNode : evalCase.requiredNodes()) {
            if (!nodeIds.contains(requiredNode)) reasons.add("REQUIRED_NODE_MISSING:" + requiredNode);
        }
        for (String forbiddenNode : evalCase.forbiddenNodes()) {
            if (nodeIds.contains(forbiddenNode)) reasons.add("FORBIDDEN_NODE_PRESENT:" + forbiddenNode);
        }

        Set<String> roles = definition.agentRoles().stream()
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String requiredRole : evalCase.requiredRoles()) {
            if (!containsIgnoreCase(roles, requiredRole)) reasons.add("REQUIRED_ROLE_MISSING:" + requiredRole);
        }
        for (String forbiddenRole : evalCase.forbiddenRoles()) {
            if (containsIgnoreCase(roles, forbiddenRole)) reasons.add("FORBIDDEN_ROLE_PRESENT:" + forbiddenRole);
        }

        if (!reachesEnd(definition)) reasons.add("GRAPH_END_UNREACHABLE");
        if (containsPreApprovalTargetWrite(definition)) reasons.add("PRE_APPROVAL_TARGET_WRITE_DECLARED");

        Map<String, Object> fixture = evalCase.fixture();
        Set<String> evidence = new HashSet<>(strings(fixture.get("evidence")));
        for (String requiredEvidence : evalCase.requiredEvidence()) {
            if (!evidence.contains(requiredEvidence)) reasons.add("REQUIRED_EVIDENCE_MISSING:" + requiredEvidence);
        }

        List<Map<String, Object>> toolCalls = maps(fixture.get("toolCalls"));
        Set<String> actualSources = new LinkedHashSet<>();
        Set<String> actualTools = new LinkedHashSet<>();
        for (Map<String, Object> call : toolCalls) {
            String source = firstText(call.get("source"), call.get("toolsetId"), call.get("adapterType"));
            String tool = firstText(call.get("toolName"), call.get("name"));
            if (!source.isBlank()) actualSources.add(source);
            if (!tool.isBlank()) actualTools.add(tool);
            String effect = text(call.get("effectType")).toUpperCase(java.util.Locale.ROOT);
            if (evalCase.forbiddenEffects().stream().anyMatch(effect::equalsIgnoreCase)) {
                reasons.add("FORBIDDEN_EFFECT:" + effect);
            }
            if (Boolean.TRUE.equals(call.get("writesTargetResource"))) {
                reasons.add("PRE_APPROVAL_TARGET_WRITE_ATTEMPT:" + tool);
            }
        }
        for (String requiredSource : evalCase.requiredSources()) {
            if (!containsIgnoreCase(actualSources, requiredSource)) reasons.add("REQUIRED_SOURCE_MISSING:" + requiredSource);
        }
        for (String forbiddenSource : evalCase.forbiddenSources()) {
            if (containsIgnoreCase(actualSources, forbiddenSource)) reasons.add("FORBIDDEN_SOURCE_USED:" + forbiddenSource);
        }
        for (String requiredTool : evalCase.requiredTools()) {
            if (!containsIgnoreCase(actualTools, requiredTool)) reasons.add("REQUIRED_TOOL_MISSING:" + requiredTool);
        }
        for (String forbiddenTool : evalCase.forbiddenTools()) {
            if (containsIgnoreCase(actualTools, forbiddenTool)) reasons.add("FORBIDDEN_TOOL_USED:" + forbiddenTool);
        }

        int loopIterations = integer(fixture.get("loopIterations"));
        if (evalCase.maxLoopIterations() > 0 && loopIterations > evalCase.maxLoopIterations()) {
            reasons.add("LOOP_DID_NOT_CONVERGE");
        }
        int toolCallCount = toolCalls.size();
        if (evalCase.maxToolCalls() > 0 && toolCallCount > evalCase.maxToolCalls()) {
            reasons.add("TOOL_CALL_BUDGET_EXCEEDED");
        }
        long tokenCount = longValue(fixture.get("tokenCount"));
        if (evalCase.maxTokenCount() > 0 && tokenCount > evalCase.maxTokenCount()) {
            reasons.add("TOKEN_BUDGET_EXCEEDED");
        }
        long latencyMs = longValue(fixture.get("latencyMs"));
        if (evalCase.maxLatencyMs() > 0 && latencyMs > evalCase.maxLatencyMs()) {
            reasons.add("LATENCY_BUDGET_EXCEEDED");
        }

        Map<String, Object> output = objectMap(fixture.get("output"));
        for (String requiredField : evalCase.requiredOutputFields()) {
            if (!hasPath(output, requiredField)) reasons.add("OUTPUT_FIELD_MISSING:" + requiredField);
        }
        if (!evalCase.expectedStatus().isBlank()
                && !evalCase.expectedStatus().equalsIgnoreCase(text(output.get("status")))) {
            reasons.add("OUTPUT_STATUS_MISMATCH");
        }
        if (evalCase.expectedChangePackageCreated() != null) {
            boolean actualPackage = Boolean.TRUE.equals(fixture.get("changePackageCreated"));
            if (evalCase.expectedChangePackageCreated() != actualPackage) {
                reasons.add("CHANGE_PACKAGE_DECISION_MISMATCH");
            }
        }
        if (evalCase.requireFactInferenceUnknownSeparation()) {
            for (String field : List.of("facts", "inferences", "unknowns")) {
                if (!(output.get(field) instanceof Collection<?>)) reasons.add("OUTPUT_CONTRACT_MISSING:" + field);
            }
        }
        List<Map<String, Object>> evidenceRefs = maps(fixture.get("evidenceRefs"));
        if (evalCase.requireEvidenceRefs()) {
            for (Map<String, Object> ref : evidenceRefs) {
                if (text(ref.get("resultId")).isBlank() || text(ref.get("outputHash")).isBlank()) {
                    reasons.add("EVIDENCE_REF_INCOMPLETE");
                }
            }
            if (evidenceRefs.isEmpty()) reasons.add("EVIDENCE_REFS_MISSING");
        }

        boolean passed = reasons.isEmpty();
        double score = passed ? 1D : Math.max(0D, 1D - (0.2D * reasons.size()));
        Map<String, Object> actual = new LinkedHashMap<>();
        if (!evalCase.expectedIntent().isBlank()) {
            actual.put("legacyExpectedIntentIgnored", evalCase.expectedIntent());
        }
        actual.put("nodeIds", nodeIds);
        actual.put("agentRoles", roles);
        actual.put("graphEndReachable", reachesEnd(definition));
        actual.put("fixtureEvidence", evidence);
        actual.put("sources", actualSources);
        actual.put("tools", actualTools);
        actual.put("toolCalls", toolCalls);
        actual.put("toolCallCount", toolCallCount);
        actual.put("loopIterations", loopIterations);
        actual.put("latencyMs", latencyMs);
        actual.put("tokenCount", tokenCount);
        actual.put("cost", fixture.getOrDefault("cost", 0));
        actual.put("evidenceRefs", evidenceRefs);
        actual.put("output", output);
        actual.put("changePackageCreated", Boolean.TRUE.equals(fixture.get("changePackageCreated")));
        return new AgentEvalCaseResult(passed, score, reasons, actual);
    }

    public AgentEvalRunResult summarize(
            String evalRunId,
            String suiteId,
            AgentEvalDefinitionSnapshot definition,
            AgentEvalDefinitionSnapshot baseline,
            List<AgentEvalCaseExecution> executions,
            List<AgentEvalCaseResult> baselineResults) {
        int passed = (int) executions.stream().filter(execution -> execution.result().passed()).count();
        boolean regression = false;
        for (int index = 0; index < executions.size() && index < baselineResults.size(); index++) {
            if (baselineResults.get(index).passed() && !executions.get(index).result().passed()) {
                regression = true;
                break;
            }
        }
        int failed = executions.size() - passed;
        String regressionStatus = regression ? "FAILED" : "PASSED";
        String status = failed == 0 && !regression ? "PASSED" : "FAILED";
        return new AgentEvalRunResult(
                evalRunId,
                suiteId,
                definition.projectId(),
                definition.agentId(),
                definition.version(),
                definition.definitionHash(),
                baseline == null ? 0 : baseline.version(),
                regressionStatus,
                status,
                executions.size(),
                passed,
                failed,
                executions,
                baselineResults);
    }

    private boolean reachesEnd(AgentEvalDefinitionSnapshot definition) {
        if (definition.nodes().isEmpty() && !definition.agentRoles().isEmpty()) {
            return true;
        }
        String start = definition.startNodeId();
        if (start.isBlank()) {
            start = definition.nodes().stream()
                    .filter(node -> "START".equalsIgnoreCase(node.type()))
                    .map(AgentEvalNode::nodeId)
                    .findFirst()
                    .orElse("");
        }
        if (start.isBlank()) return false;
        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (AgentEvalEdge edge : definition.edges()) {
            edges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to());
        }
        Set<String> endNodes = definition.nodes().stream()
                .filter(node -> "END".equalsIgnoreCase(node.type()))
                .map(AgentEvalNode::nodeId)
                .collect(java.util.stream.Collectors.toSet());
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            if (endNodes.contains(current)) return true;
            queue.addAll(edges.getOrDefault(current, List.of()));
        }
        return false;
    }

    private boolean containsPreApprovalTargetWrite(AgentEvalDefinitionSnapshot definition) {
        for (AgentEvalNode node : definition.nodes()) {
            Map<String, Object> config = node.configuration();
            if (Boolean.TRUE.equals(config.get("writesTargetResource"))) return true;
            String effect = text(config.get("effectType")).toUpperCase(java.util.Locale.ROOT);
            if (Set.of("MUTATE_TARGET_RESOURCE", "DELETE_TARGET_RESOURCE", "EXECUTE_EXTERNAL_ACTION")
                    .contains(effect)) return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(Collection<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private boolean hasPath(Map<String, Object> source, String path) {
        Object current = source;
        for (String part : text(path).split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) return false;
            current = map.get(part);
        }
        return current != null;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(this::objectMap).filter(item -> !item.isEmpty()).toList();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(this::text).filter(item -> !item.isBlank()).toList();
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private int integer(Object value) {
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private long longValue(Object value) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
