package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.WorkflowResourceDriftPolicy;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeWorkflowBindingApplicationServiceTest {

    @Test
    void pipelineMustBindImmutablePlanInTheRequiredOrder() {
        RuntimeWorkflowBindingApplicationService service = new RuntimeWorkflowBindingApplicationService();

        BoundWorkflowExecutionPlan plan = service.bind(input(sharedResources()));

        assertEquals(List.of(
                "ACCESS_VALIDATION", "MODEL_BINDING", "TOOL_BINDING", "MCP_BINDING",
                "SKILL_BINDING", "KNOWLEDGE_BINDING", "MEMORY_BINDING",
                "POLICY_BINDING", "BOUND_PLAN_ASSEMBLY"), service.stageIds());
        assertEquals(service.stageIds(), plan.completedBindingStages());
        assertEquals("model-1", plan.nodes().get(0).resources().get(0).resourceId());
        new BoundWorkflowPlanPolicy().assertPlanHash(plan);
        assertThrows(UnsupportedOperationException.class, () -> plan.nodes().add(plan.nodes().get(0)));
    }

    @Test
    void contextBundleMustBeFrozenAndPlanHashMustChangeWithRunIdentity() {
        RuntimeWorkflowBindingApplicationService service = new RuntimeWorkflowBindingApplicationService();
        BoundWorkflowExecutionPlan first = service.bind(input(sharedResources()));
        RuntimeWorkflowBindingInput otherRun = input(sharedResources(), "run-2");
        BoundWorkflowExecutionPlan second = service.bind(otherRun);

        assertNotEquals(first.planHash(), second.planHash());
        assertThrows(IllegalArgumentException.class, () -> service.bind(input(List.of(policyResource()))));
    }

    @Test
    void driftPolicyMustNeverPermitVersionHashOrPermissionDrift() {
        BoundWorkflowExecutionPlan plan = new RuntimeWorkflowBindingApplicationService()
                .bind(input(sharedResources()));
        BoundWorkflowPlanPolicy policy = new BoundWorkflowPlanPolicy();
        List<BoundWorkflowResourceSnapshot> equivalentSource = plan.allResources().stream()
                .map(resource -> new BoundWorkflowResourceSnapshot(
                        resource.kind(), resource.resourceId(), resource.resourceVersion(),
                        resource.resourceHash(), "RECOVERED_SNAPSHOT",
                        resource.required(), resource.readOnly()))
                .toList();

        policy.assertResourceCompatibility(
                plan, equivalentSource, WorkflowResourceDriftPolicy.ALLOW_EQUIVALENT_SOURCE_CHANGE);
        assertThrows(IllegalStateException.class, () -> policy.assertResourceCompatibility(
                plan, equivalentSource, WorkflowResourceDriftPolicy.REJECT_ANY_DRIFT));

        List<BoundWorkflowResourceSnapshot> changedHash = equivalentSource.stream()
                .map(resource -> resource.kind() == BoundWorkflowResourceKind.MODEL
                        ? new BoundWorkflowResourceSnapshot(
                        resource.kind(), resource.resourceId(), resource.resourceVersion(),
                        "changed", resource.bindingSource(), resource.required(), resource.readOnly())
                        : resource)
                .toList();
        assertThrows(IllegalStateException.class, () -> policy.assertResourceCompatibility(
                plan, changedHash, WorkflowResourceDriftPolicy.ALLOW_EQUIVALENT_SOURCE_CHANGE));
    }

    private RuntimeWorkflowBindingInput input(List<BoundWorkflowResourceSnapshot> shared) {
        return input(shared, "run-1");
    }

    private RuntimeWorkflowBindingInput input(
            List<BoundWorkflowResourceSnapshot> shared,
            String runId) {
        BoundWorkflowResourceSnapshot model = new BoundWorkflowResourceSnapshot(
                BoundWorkflowResourceKind.MODEL, "model-1", 3, "model-hash",
                "RUNTIME_RESOURCE_PIPELINE", true, true);
        return new RuntimeWorkflowBindingInput(
                1, 7, "definition-hash", "agent-1", "session-1", runId, "project-1",
                "bundle-1", "bundle-hash", "start",
                List.of(new RuntimeWorkflowBindingInput.NodeInput(
                        "start", "LLM", "LLM", "llm-node", "config-hash", List.of(model))),
                List.of(), shared, Instant.parse("2026-08-02T00:00:00Z"));
    }

    private List<BoundWorkflowResourceSnapshot> sharedResources() {
        return List.of(memoryResource(), policyResource());
    }

    private BoundWorkflowResourceSnapshot memoryResource() {
        return new BoundWorkflowResourceSnapshot(
                BoundWorkflowResourceKind.MEMORY_CONTEXT, "bundle-1", 0, "bundle-hash",
                "RUNTIME_CONTEXT_BUNDLE", true, true);
    }

    private BoundWorkflowResourceSnapshot policyResource() {
        return new BoundWorkflowResourceSnapshot(
                BoundWorkflowResourceKind.RUNTIME_POLICY, "runtime-policy:agent-1", 7,
                "policy-hash", "RUNTIME_CONTEXT_BUNDLE", true, true);
    }
}
