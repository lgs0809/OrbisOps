package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;

import java.util.List;
import java.util.function.Consumer;

public final class RuntimeWorkflowBindingApplicationService {

    private final RuntimeWorkflowBindingPipeline pipeline;

    public RuntimeWorkflowBindingApplicationService() {
        this(new BoundWorkflowPlanPolicy());
    }

    RuntimeWorkflowBindingApplicationService(BoundWorkflowPlanPolicy policy) {
        this.pipeline = new RuntimeWorkflowBindingPipeline(List.of(
                stage("ACCESS_VALIDATION", 100, RuntimeWorkflowBindingContext::validateAccess),
                stage("MODEL_BINDING", 200, context -> context.validateResources(
                        BoundWorkflowResourceKind.MODEL)),
                stage("TOOL_BINDING", 300, context -> context.validateResources(
                        BoundWorkflowResourceKind.TOOL)),
                stage("MCP_BINDING", 400, context -> context.validateResources(
                        BoundWorkflowResourceKind.MCP)),
                stage("SKILL_BINDING", 500, context -> context.validateResources(
                        BoundWorkflowResourceKind.SKILL)),
                stage("KNOWLEDGE_BINDING", 600, context -> context.validateResources(
                        BoundWorkflowResourceKind.KNOWLEDGE_BASE)),
                stage("MEMORY_BINDING", 700, RuntimeWorkflowBindingContext::validateMemory),
                stage("POLICY_BINDING", 800, RuntimeWorkflowBindingContext::validatePolicy),
                stage("BOUND_PLAN_ASSEMBLY", 900, RuntimeWorkflowBindingContext::assemble)
        ), policy);
    }

    public BoundWorkflowExecutionPlan bind(RuntimeWorkflowBindingInput input) {
        return pipeline.bind(input);
    }

    public List<String> stageIds() {
        return pipeline.stageIds();
    }

    private RuntimeWorkflowBindingStage stage(
            String stageId,
            int order,
            Consumer<RuntimeWorkflowBindingContext> action) {
        return new RuntimeWorkflowBindingStage() {
            @Override
            public String stageId() {
                return stageId;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void apply(RuntimeWorkflowBindingContext context) {
                action.accept(context);
            }
        };
    }
}
