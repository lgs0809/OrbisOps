package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuntimeWorkflowBindingPipeline {

    private final List<RuntimeWorkflowBindingStage> stages;
    private final BoundWorkflowPlanPolicy policy;

    public RuntimeWorkflowBindingPipeline(
            List<RuntimeWorkflowBindingStage> stages,
            BoundWorkflowPlanPolicy policy) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_STAGES_REQUIRED");
        }
        if (policy == null) throw new IllegalArgumentException("BOUND_WORKFLOW_PLAN_POLICY_REQUIRED");
        this.stages = stages.stream()
                .sorted(Comparator.comparingInt(RuntimeWorkflowBindingStage::order)
                        .thenComparing(RuntimeWorkflowBindingStage::stageId))
                .toList();
        assertUniqueStages(this.stages);
        this.policy = policy;
    }

    public BoundWorkflowExecutionPlan bind(RuntimeWorkflowBindingInput input) {
        RuntimeWorkflowBindingContext context = new RuntimeWorkflowBindingContext(input, policy);
        for (RuntimeWorkflowBindingStage stage : stages) {
            stage.apply(context);
            context.completed(stage.stageId());
        }
        return context.output();
    }

    public List<String> stageIds() {
        return stages.stream().map(RuntimeWorkflowBindingStage::stageId).toList();
    }

    private void assertUniqueStages(List<RuntimeWorkflowBindingStage> ordered) {
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (RuntimeWorkflowBindingStage stage : ordered) {
            if (stage == null) throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_STAGE_REQUIRED");
            if (stage.stageId() == null || stage.stageId().isBlank()) {
                throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_STAGE_ID_REQUIRED");
            }
            if (!ids.add(stage.stageId())) {
                throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_STAGE_DUPLICATE:" + stage.stageId());
            }
            if (!orders.add(stage.order())) {
                throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_STAGE_ORDER_DUPLICATE:" + stage.order());
            }
        }
    }
}
