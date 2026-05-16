package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Projects canonical investigation tasks, routes and graph handoff context. */
final class OpsAnalysisTaskProjectionPolicy {

    private final OpsAnalysisSourcePolicy sourcePolicy;

    OpsAnalysisTaskProjectionPolicy(OpsAnalysisSourcePolicy sourcePolicy) {
        this.sourcePolicy = sourcePolicy;
    }

    Optional<OpsAnalysisResponseDTO.InvestigationTaskDTO> selectedTask(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            String source) {
        if (plan == null || !StringUtils.hasText(source)) return Optional.empty();
        String expected = sourcePolicy.normalizeSource(source);
        return Optional.ofNullable(plan.getTasks()).orElse(List.of()).stream()
                .filter(task -> task != null
                        && expected.equals(sourcePolicy.normalizeSource(task.getSource())))
                .findFirst();
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO canonicalTask(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task) {
        if (task == null) return null;
        String source = sourcePolicy.normalizeSource(task.getSource());
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(StringUtils.hasText(task.getAgent())
                        ? task.getAgent()
                        : source + "-agent")
                .goal(task.getGoal())
                .reason(task.getReason())
                .priority(task.getPriority())
                .condition(task.getCondition())
                .build();
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO graphNodeTask(
            OpsWorkflowNode node,
            String type,
            String source) {
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException(
                    "分析子 Agent 节点缺少 routeKey：" + node.getNodeId());
        }
        String agent = StringUtils.hasText(node.getAgent())
                ? node.getAgent()
                : sourcePolicy.defaultAgent(source);
        String description = StringUtils.hasText(node.getDescription())
                ? node.getDescription()
                : agent + " 执行 " + type + " 节点查询。";
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .goal(description)
                .reason("Graph 编排已进入节点 " + node.getNodeId())
                .priority(1)
                .condition("graph.edge")
                .build();
    }

    List<String> selectedRoutes(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        if (plan == null) return List.of();
        return Optional.ofNullable(plan.getTasks()).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .filter(StringUtils::hasText)
                .map(sourcePolicy::normalizeSource)
                .distinct()
                .toList();
    }

    Set<String> plannedSources(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        if (plan == null) return Set.of();
        return Optional.ofNullable(plan.getTasks()).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .filter(StringUtils::hasText)
                .map(sourcePolicy::normalizeSource)
                .collect(Collectors.toSet());
    }

    Set<String> executedSources(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        return Optional.ofNullable(results).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getSource)
                .filter(StringUtils::hasText)
                .map(sourcePolicy::normalizeSource)
                .collect(Collectors.toSet());
    }

    List<String> nodeSkills(OpsAgentDefinition definition, OpsWorkflowNode node) {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        if (definition != null && definition.getSkills() != null) {
            skills.addAll(definition.getSkills());
        }
        if (node != null && node.getSkills() != null) {
            skills.addAll(node.getSkills());
        }
        return new ArrayList<>(skills);
    }

    List<OpsAnalysisResponseDTO.InvestigationTaskDTO> canonicalUnexecutedTasks(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults,
            OpsAgentDefinition definition) {
        Set<String> executed = executedSources(existingResults);
        return Optional.ofNullable(plan == null ? null : plan.getTasks())
                .orElse(List.of()).stream()
                .filter(task -> task != null && StringUtils.hasText(task.getSource()))
                .map(this::canonicalTask)
                .filter(task -> StringUtils.hasText(task.getSource()))
                .filter(task -> !executed.contains(task.getSource()))
                .map(task -> enrichWithFeedbackHandoff(task, definition))
                .sorted(Comparator.comparing(task ->
                        Optional.ofNullable(task.getPriority()).orElse(99)))
                .toList();
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO enrichWithGraphHandoff(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            Predicate<OpsGraphEdge> edgeActive) {
        if (task == null) return null;
        String handoff = OpsGraphEdgePromptContext.compactIncomingHandoff(
                definition,
                node,
                edge -> edgeActive != null && edgeActive.test(edge));
        return enrichTask(task, handoff, "画布边交接：", "edge_handoff=");
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO enrichWithFeedbackHandoff(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentDefinition definition) {
        if (task == null) return null;
        OpsWorkflowNode node = analysisNodeBySource(
                definition, task.getSource()).orElse(null);
        if (node == null) return task;
        String handoff = OpsGraphEdgePromptContext.compactIncomingHandoff(
                definition,
                node,
                edge -> Boolean.TRUE.equals(edge.getFeedback()));
        return enrichTask(task, handoff, "复盘回边交接：", "feedback_edge=");
    }

    private Optional<OpsWorkflowNode> analysisNodeBySource(
            OpsAgentDefinition definition,
            String source) {
        String expected = sourcePolicy.normalizeSource(source);
        if (!StringUtils.hasText(expected)) return Optional.empty();
        return Optional.ofNullable(definition == null ? null : definition.getNodes())
                .orElse(List.of()).stream()
                .filter(node -> expected.equals(sourcePolicy.normalizeSource(
                        sourcePolicy.incomingRouteKey(definition, node))))
                .findFirst();
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO enrichTask(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            String handoff,
            String reasonPrefix,
            String conditionPrefix) {
        if (!StringUtils.hasText(handoff)) return task;
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(task.getSource())
                .agent(task.getAgent())
                .goal(task.getGoal())
                .reason(appendSegment(task.getReason(), reasonPrefix + handoff, 600))
                .priority(task.getPriority())
                .condition(appendSegment(
                        task.getCondition(), conditionPrefix + handoff, 400))
                .build();
    }

    private String appendSegment(String original, String segment, int maxChars) {
        if (!StringUtils.hasText(segment)) return original;
        String current = value(original);
        if (current.contains(segment)) return current;
        String joined = StringUtils.hasText(current)
                ? current + "；" + segment
                : segment;
        if (joined.length() <= maxChars) return joined;
        return joined.substring(0, Math.max(0, maxChars - 6)) + "...";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
