package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Selects Router routes from graph state under explicit request-scoped analysis constraints. */
final class OpsAnalysisRouterSelectionPolicy {

    private final OpsAnalysisSourcePolicy sourcePolicy;
    private final OpsAnalysisTaskProjectionPolicy taskProjectionPolicy;

    OpsAnalysisRouterSelectionPolicy(
            OpsAnalysisSourcePolicy sourcePolicy,
            OpsAnalysisTaskProjectionPolicy taskProjectionPolicy) {
        this.sourcePolicy = sourcePolicy;
        this.taskProjectionPolicy = taskProjectionPolicy;
    }

    List<String> selectedRoutesForRouter(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            OverAllState graphState,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsAgentChatRequest request) {
        String inputKey = firstText(configText(node, "inputKey"), "plan");
        Object input = graphState == null
                ? null
                : graphState.value(inputKey).orElse(null);
        Set<String> allowedRoutes = routerRouteKeys(definition, node);
        List<String> routes = routesFromRouterInput(input, allowedRoutes);
        if (routes.isEmpty()
                && ("plan".equals(inputKey) || "selectedRoutes".equals(inputKey))) {
            routes = taskProjectionPolicy.selectedRoutes(plan);
        }
        if (!allowedRoutes.isEmpty()) {
            routes = filterAllowedRoutes(routes, allowedRoutes);
        }
        Set<String> explicitlyAllowed = routeConstraint(
                request, "allowedInvestigationSources");
        if (!explicitlyAllowed.isEmpty()) {
            routes = routes.stream()
                    .map(sourcePolicy::normalizeSource)
                    .filter(explicitlyAllowed::contains)
                    .distinct()
                    .toList();
        }
        Set<String> excluded = routeConstraint(
                request, "excludedCapabilities");
        if (!excluded.isEmpty()) {
            routes = routes.stream()
                    .map(sourcePolicy::normalizeSource)
                    .filter(route -> !excluded.contains(route))
                    .distinct()
                    .toList();
        }
        if (!routes.isEmpty()
                && "single".equalsIgnoreCase(
                value(configText(node, "routeMode")).trim())) {
            return List.of(routes.get(0));
        }
        return routes;
    }

    Set<String> routeConstraint(OpsAgentChatRequest request, String key) {
        if (request == null || request.getMetadata() == null) return Set.of();
        String directKey = "allowedInvestigationSources".equals(key)
                ? "allowedInvestigationSources"
                : "excludedCapabilities";
        Object raw = request.getMetadata().get(directKey);
        if (!(raw instanceof Collection<?> values)) return Set.of();
        return values.stream()
                .map(String::valueOf)
                .map(sourcePolicy::normalizeSource)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    boolean allowsSource(String source,
                               Set<String> allowedByIntent,
                               Set<String> excludedByIntent) {
        String normalizedSource = sourcePolicy.normalizeSource(source);
        if (!sourcePolicy.isInvestigationRoute(normalizedSource)) return true;
        Set<String> allowed = Optional.ofNullable(allowedByIntent).orElse(Set.of());
        Set<String> excluded = Optional.ofNullable(excludedByIntent).orElse(Set.of());
        return !excluded.contains(normalizedSource)
                && (allowed.isEmpty() || allowed.contains(normalizedSource));
    }

    boolean allowsEdge(OpsGraphEdge edge,
                             Set<String> allowedByIntent,
                             Set<String> excludedByIntent,
                             String normalizedConditionType) {
        if (!Set.of("route_match", "review_decision")
                .contains(normalizedConditionType)) {
            return true;
        }
        return allowsSource(
                sourcePolicy.routeConditionSource(edge),
                allowedByIntent,
                excludedByIntent);
    }

    List<String> routesFromRouterInput(Object input, Set<String> allowedRoutes) {
        if (input == null) return List.of();
        Set<String> allowed = Optional.ofNullable(allowedRoutes).orElse(Set.of()).stream()
                .map(sourcePolicy::normalizeSource)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (input instanceof OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
            return filterAllowedRoutes(
                    taskProjectionPolicy.selectedRoutes(plan), allowed);
        }
        if (input instanceof Collection<?> values) {
            List<String> routes = values.stream()
                    .map(String::valueOf)
                    .map(sourcePolicy::normalizeSource)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            return filterAllowedRoutes(routes, allowed);
        }
        String text = String.valueOf(input).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> routes = new LinkedHashSet<>();
        if (!allowed.isEmpty()) {
            for (String route : allowed) {
                if (routeTextContains(route, text)) routes.add(route);
            }
            return new ArrayList<>(routes);
        }
        if (text.contains("needs:rag") || text.contains("rag")) {
            routes.add(OpsMainAgentPlanner.SOURCE_RAG);
        }
        if (text.contains("needs:elasticsearch")
                || text.contains("elasticsearch")
                || text.contains("traceid")
                || text.contains("日志")) {
            routes.add(OpsMainAgentPlanner.SOURCE_ES);
        }
        if (text.contains("needs:prometheus")
                || text.contains("prometheus")
                || text.contains("指标")) {
            routes.add(OpsMainAgentPlanner.SOURCE_PROM);
        }
        if (text.contains("needs:mysql_slow_sql")
                || text.contains("mysql_slow_sql")
                || text.contains("slow sql")
                || text.contains("慢 sql")) {
            routes.add(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL);
        }
        if (text.contains("replan_required")) routes.add("replan_required");
        return new ArrayList<>(routes);
    }

    boolean routeTextContains(String expected, String text) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(text)) {
            return false;
        }
        String normalizedExpected = sourcePolicy.normalizeSource(expected);
        String normalizedText = text.toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalizedText.contains("needs:" + normalizedExpected)) return true;
        for (String routePart : normalizedText.split("[^a-z0-9_]+")) {
            if (normalizedExpected.equals(sourcePolicy.normalizeSource(routePart))) {
                return true;
            }
        }
        return false;
    }

    private List<String> filterAllowedRoutes(
            List<String> routes,
            Set<String> allowedRoutes) {
        if (routes == null
                || routes.isEmpty()
                || allowedRoutes == null
                || allowedRoutes.isEmpty()) {
            return routes == null ? List.of() : routes;
        }
        return routes.stream()
                .map(sourcePolicy::normalizeSource)
                .filter(allowedRoutes::contains)
                .distinct()
                .toList();
    }

    private Set<String> routerRouteKeys(
            OpsAgentDefinition definition,
            OpsWorkflowNode router) {
        if (definition == null
                || router == null
                || !StringUtils.hasText(router.getNodeId())) {
            return Set.of();
        }
        return Optional.ofNullable(definition.getEdges()).orElse(List.of()).stream()
                .filter(edge -> router.getNodeId().equals(edge.getFrom()))
                .map(sourcePolicy::routeConditionSource)
                .map(sourcePolicy::normalizeSource)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null
                || node.getConfig() == null
                || !node.getConfig().containsKey(key)) {
            return null;
        }
        Object configured = node.getConfig().get(key);
        return configured == null ? null : String.valueOf(configured);
    }

    private String firstText(String... values) {
        for (String candidate : values) {
            if (StringUtils.hasText(candidate)) return candidate;
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
