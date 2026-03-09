package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns transient Graph-run state and the Spring AI Graph state schema. */
final class OpsGraphRuntimeStateManager {

    static final String GRAPH_COMPLETED_KEY = "graphCompleted";
    static final String INVESTIGATION_EXECUTED_KEY = "investigationExecuted";
    static final String RUNTIME_INVESTIGATION_EXECUTED_KEY = "_runtimeInvestigationExecuted";
    static final String NOTIFICATION_HANDLED_KEY = "notificationHandled";
    static final String LOOP_ROUNDS_KEY = "loopRounds";

    private final Map<String, RunState> states = new ConcurrentHashMap<>();

    void initialize(OpsAgentChatRequest request) {
        if (request == null) return;
        if (request.getMetadata() != null) {
            request.getMetadata().put(RUNTIME_INVESTIGATION_EXECUTED_KEY, false);
        }
        states.put(runId(request), new RunState(
                new AtomicBoolean(false),
                ConcurrentHashMap.newKeySet(),
                new AtomicBoolean(false)));
    }

    void cleanup(OpsAgentChatRequest request) {
        if (request != null) states.remove(runId(request));
    }

    Map<String, Object> initialInput(OpsAgentChatRequest request,
                                     OpsAnalysisRuntimeStateManager.State analysisState,
                                     String originalQuery,
                                     String memoryContext,
                                     Set<String> allowedRoutes,
                                     Set<String> excludedRoutes) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", request == null ? "" : text(request.getQuery()));
        input.put("originalQuery", text(originalQuery));
        input.put("rewrittenQuery", request == null ? "" : text(request.getQuery()));
        input.put("memoryContext", text(memoryContext));
        input.put("output", request == null ? "" : text(request.getQuery()));
        input.put(INVESTIGATION_EXECUTED_KEY, false);
        input.put("intentAllowedRoutes", new ArrayList<>(safeSet(allowedRoutes)));
        input.put("intentExcludedRoutes", new ArrayList<>(safeSet(excludedRoutes)));
        if (analysisState != null) {
            input.put("response", analysisState.response());
            input.put("plan", analysisState.planRef().get());
            input.put("results", analysisState.resultRef().get());
        }
        return input;
    }

    KeyStrategyFactory keyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new LinkedHashMap<>();
            strategies.put("query", KeyStrategy.REPLACE);
            strategies.put("output", KeyStrategy.REPLACE);
            strategies.put("response", KeyStrategy.REPLACE);
            strategies.put("plan", KeyStrategy.REPLACE);
            strategies.put("results", KeyStrategy.REPLACE);
            strategies.put(INVESTIGATION_EXECUTED_KEY, KeyStrategy.REPLACE);
            strategies.put("selectedRoutes", KeyStrategy.REPLACE);
            strategies.put("intentAllowedRoutes", KeyStrategy.REPLACE);
            strategies.put("intentExcludedRoutes", KeyStrategy.REPLACE);
            strategies.put("review_decision", KeyStrategy.REPLACE);
            strategies.put("selectedReviewRoutes", KeyStrategy.REPLACE);
            strategies.put(LOOP_ROUNDS_KEY, KeyStrategy.REPLACE);
            strategies.put(GRAPH_COMPLETED_KEY, KeyStrategy.REPLACE);
            strategies.put(NOTIFICATION_HANDLED_KEY, KeyStrategy.REPLACE);
            strategies.put("latestObservation", KeyStrategy.REPLACE);
            return strategies;
        };
    }

    boolean claimEndExecution(OpsAgentChatRequest request) {
        String runId = runId(request);
        if (!StringUtils.hasText(runId)) return true;
        return state(request).endClaimed().compareAndSet(false, true);
    }

    boolean stateBoolean(OverAllState state, String key) {
        if (state == null || !StringUtils.hasText(key)) return false;
        Object value = state.value(key).orElse(null);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    void markInvestigationExecuted(OpsAgentChatRequest request, boolean executed) {
        if (request == null) return;
        state(request).investigationExecuted().set(executed);
        if (request.getMetadata() != null) {
            request.getMetadata().put(RUNTIME_INVESTIGATION_EXECUTED_KEY, executed);
        }
    }

    void markAgentScopeInvestigationCompleted(OpsAgentChatRequest request,
                                               Map<String, Object> result,
                                               OpsAnalysisRoutingPolicy routingPolicy) {
        if (result != null) result.put(INVESTIGATION_EXECUTED_KEY, true);
        markInvestigationExecuted(request, true);
        String source = observationSource(result);
        String runId = runId(request);
        if (StringUtils.hasText(runId) && StringUtils.hasText(source)) {
            state(request).executedSources().add(routingPolicy.normalizeSource(source));
        }
    }

    boolean investigationExecuted(OpsAgentChatRequest request) {
        if (request == null) return false;
        return state(request).investigationExecuted().get()
                || requestBoolean(request, RUNTIME_INVESTIGATION_EXECUTED_KEY);
    }

    boolean completedExplicitSingleSourceInvestigation(
            OpsAgentChatRequest request,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            OpsAnalysisRoutingPolicy routingPolicy) {
        return routingPolicy.routeConstraint(request, "allowedInvestigationSources").size() == 1
                && investigationExecuted(request)
                && results != null
                && !results.isEmpty();
    }

    boolean completedExplicitSingleSourceGraphInvestigation(
            OpsAgentChatRequest request,
            OverAllState state,
            OpsAnalysisRoutingPolicy routingPolicy) {
        Set<String> allowedSources = routingPolicy.routeConstraint(request, "allowedInvestigationSources");
        if (allowedSources.size() != 1) return false;
        Set<String> executedSources = graphStateExecutedSources(state, routingPolicy);
        executedSources.addAll(state(request).executedSources());
        return (stateBoolean(state, INVESTIGATION_EXECUTED_KEY) || investigationExecuted(request))
                && executedSources.contains(allowedSources.iterator().next());
    }

    Set<String> graphStateExecutedSources(OverAllState state, OpsAnalysisRoutingPolicy routingPolicy) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        Object rawResults = state == null ? null : state.value("results").orElse(null);
        if (rawResults instanceof Collection<?> results) {
            for (Object result : results) {
                String source = result instanceof OpsAnalysisResponseDTO.InvestigationResultDTO observation
                        ? observation.getSource()
                        : result instanceof Map<?, ?> map ? text(map.get("source")) : "";
                if (StringUtils.hasText(source)) sources.add(routingPolicy.normalizeSource(source));
            }
        }
        if (state != null) {
            state.value("response", OpsAnalysisResponseDTO.class)
                    .map(OpsAnalysisResponseDTO::getInvestigationResults)
                    .orElse(List.of()).stream()
                    .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getSource)
                    .filter(StringUtils::hasText)
                    .map(routingPolicy::normalizeSource)
                    .forEach(sources::add);
        }
        return sources;
    }

    List<OpsAnalysisResponseDTO.InvestigationResultDTO> analysisResultSnapshot(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            OverAllState graphState,
            OpsAnalysisRoutingPolicy routingPolicy) {
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> snapshot;
        synchronized (initialResults) {
            snapshot = new ArrayList<>(initialResults);
        }
        Object rawResults = graphState == null ? null : graphState.value("results").orElse(null);
        if (rawResults instanceof Collection<?> results) {
            results.stream()
                    .filter(OpsAnalysisResponseDTO.InvestigationResultDTO.class::isInstance)
                    .map(OpsAnalysisResponseDTO.InvestigationResultDTO.class::cast)
                    .filter(candidate -> snapshot.stream()
                            .noneMatch(existing -> sameObservation(existing, candidate, routingPolicy)))
                    .forEach(snapshot::add);
        }
        return snapshot;
    }

    private boolean sameObservation(OpsAnalysisResponseDTO.InvestigationResultDTO left,
                                    OpsAnalysisResponseDTO.InvestigationResultDTO right,
                                    OpsAnalysisRoutingPolicy routingPolicy) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return Objects.equals(routingPolicy.normalizeSource(left.getSource()), routingPolicy.normalizeSource(right.getSource()))
                && Objects.equals(text(left.getSummary()), text(right.getSummary()));
    }

    private String observationSource(Map<String, Object> result) {
        Object observation = result == null ? null : result.get("latestObservation");
        if (observation instanceof OpsAnalysisResponseDTO.InvestigationResultDTO typed) return typed.getSource();
        if (observation instanceof Map<?, ?> map) return text(map.get("source"));
        return "";
    }

    private boolean requestBoolean(OpsAgentChatRequest request, String key) {
        if (request == null || request.getMetadata() == null) return false;
        Object value = request.getMetadata().get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private RunState state(OpsAgentChatRequest request) {
        return states.computeIfAbsent(runId(request), ignored -> new RunState(
                new AtomicBoolean(false),
                ConcurrentHashMap.newKeySet(),
                new AtomicBoolean(false)));
    }

    private String runId(OpsAgentChatRequest request) {
        return request == null ? "" : text(request.getRunId());
    }

    private Set<String> safeSet(Set<String> values) {
        return values == null ? Set.of() : values;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record RunState(AtomicBoolean investigationExecuted,
                            Set<String> executedSources,
                            AtomicBoolean endClaimed) {
    }
}
