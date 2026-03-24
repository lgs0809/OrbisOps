package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable ordered structural compilation pipeline. */
public final class AgentWorkflowCompilationPipeline {

    private final List<AgentWorkflowCompilationStage> stages;

    public AgentWorkflowCompilationPipeline(List<AgentWorkflowCompilationStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGES_REQUIRED");
        }
        this.stages = validate(stages);
    }

    public AgentWorkflowCompilationResult compile(
            AgentWorkflowDefinition definition,
            AgentWorkflowCompilationHooks hooks) {
        AgentWorkflowCompilationContext context =
                new AgentWorkflowCompilationContext(definition, hooks);
        for (AgentWorkflowCompilationStage stage : stages) {
            try {
                stage.compile(context);
                context.completeStage(stage.stageId());
            } catch (WorkflowCompilationException error) {
                throw error;
            } catch (RuntimeException error) {
                WorkflowCompilationFailure failure = new WorkflowCompilationFailure(
                        stage.failureCode(),
                        stage.stageId(),
                        definition == null || definition.graph() == null
                                ? ""
                                : definition.graph().agentId(),
                        summary(error));
                throw new WorkflowCompilationException(
                        WorkflowCompilationReport.failure(context.completedStages(), failure),
                        error);
            }
        }
        WorkflowCompilationReport report =
                WorkflowCompilationReport.success(context.completedStages());
        return new AgentWorkflowCompilationResult(context.output(), report);
    }

    public List<String> stageOrder() {
        return stages.stream().map(AgentWorkflowCompilationStage::stageId).toList();
    }

    private List<AgentWorkflowCompilationStage> validate(
            List<AgentWorkflowCompilationStage> candidates) {
        List<AgentWorkflowCompilationStage> ordered = candidates.stream()
                .filter(stage -> stage != null)
                .sorted(Comparator.comparingInt(AgentWorkflowCompilationStage::order)
                        .thenComparing(AgentWorkflowCompilationStage::stageId))
                .toList();
        if (ordered.isEmpty()) throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGES_REQUIRED");
        Set<String> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (AgentWorkflowCompilationStage stage : ordered) {
            String id = stage.stageId() == null ? "" : stage.stageId().trim();
            if (id.isBlank()) throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGE_ID_REQUIRED");
            if (stage.failureCode() == null) {
                throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGE_ERROR_CODE_REQUIRED:" + id);
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGE_DUPLICATE:" + id);
            }
            if (!orders.add(stage.order())) {
                throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGE_ORDER_DUPLICATE:" + stage.order());
            }
        }
        return List.copyOf(ordered);
    }

    private String summary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
