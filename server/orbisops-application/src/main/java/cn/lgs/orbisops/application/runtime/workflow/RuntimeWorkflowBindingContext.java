package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowRoute;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RuntimeWorkflowBindingContext {

    private final RuntimeWorkflowBindingInput input;
    private final BoundWorkflowPlanPolicy policy;
    private final List<String> completedStages = new ArrayList<>();
    private BoundWorkflowExecutionPlan output;

    RuntimeWorkflowBindingContext(
            RuntimeWorkflowBindingInput input,
            BoundWorkflowPlanPolicy policy) {
        if (input == null) throw new IllegalArgumentException("RUNTIME_WORKFLOW_BINDING_INPUT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("BOUND_WORKFLOW_PLAN_POLICY_REQUIRED");
        this.input = input;
        this.policy = policy;
    }

    void validateAccess() {
        if (input.schemaVersion() <= 0) throw new IllegalArgumentException("RUNTIME_WORKFLOW_SCHEMA_VERSION_INVALID");
        if (input.definitionVersion() <= 0) throw new IllegalArgumentException("RUNTIME_WORKFLOW_DEFINITION_VERSION_REQUIRED");
        require(input.definitionHash(), "RUNTIME_WORKFLOW_DEFINITION_HASH_REQUIRED");
        require(input.agentId(), "RUNTIME_WORKFLOW_AGENT_ID_REQUIRED");
        require(input.sessionId(), "RUNTIME_WORKFLOW_SESSION_ID_REQUIRED");
        require(input.runId(), "RUNTIME_WORKFLOW_RUN_ID_REQUIRED");
        require(input.contextBundleId(), "RUNTIME_WORKFLOW_CONTEXT_BUNDLE_ID_REQUIRED");
        require(input.contextBundleHash(), "RUNTIME_WORKFLOW_CONTEXT_BUNDLE_HASH_REQUIRED");
        require(input.startNodeId(), "RUNTIME_WORKFLOW_START_NODE_REQUIRED");
        if (input.nodes().isEmpty()) throw new IllegalArgumentException("RUNTIME_WORKFLOW_NODES_REQUIRED");
        Set<String> nodeIds = new HashSet<>();
        input.nodes().forEach(node -> {
            require(node.nodeId(), "RUNTIME_WORKFLOW_NODE_ID_REQUIRED");
            if (!nodeIds.add(node.nodeId())) {
                throw new IllegalArgumentException("RUNTIME_WORKFLOW_NODE_DUPLICATE:" + node.nodeId());
            }
        });
        if (!nodeIds.contains(input.startNodeId())) {
            throw new IllegalArgumentException("RUNTIME_WORKFLOW_START_NODE_UNKNOWN:" + input.startNodeId());
        }
        input.routes().forEach(route -> {
            if (!nodeIds.contains(route.fromNodeId()) || !nodeIds.contains(route.toNodeId())) {
                throw new IllegalArgumentException("RUNTIME_WORKFLOW_ROUTE_NODE_UNKNOWN:" + route.edgeId());
            }
        });
    }

    void validateResources(BoundWorkflowResourceKind... kinds) {
        Set<BoundWorkflowResourceKind> accepted = Set.of(kinds);
        resources().stream()
                .filter(resource -> accepted.contains(resource.kind()))
                .forEach(resource -> {
                    require(resource.resourceId(), "RUNTIME_WORKFLOW_RESOURCE_ID_REQUIRED");
                    require(resource.resourceHash(), "RUNTIME_WORKFLOW_RESOURCE_HASH_REQUIRED");
                });
    }

    void validateMemory() {
        BoundWorkflowResourceSnapshot memory = input.sharedResources().stream()
                .filter(resource -> resource.kind() == BoundWorkflowResourceKind.MEMORY_CONTEXT)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("RUNTIME_WORKFLOW_MEMORY_BINDING_REQUIRED"));
        if (!memory.resourceId().equals(input.contextBundleId())
                || !memory.resourceHash().equals(input.contextBundleHash())) {
            throw new IllegalArgumentException("RUNTIME_WORKFLOW_MEMORY_BINDING_MISMATCH");
        }
    }

    void validatePolicy() {
        input.sharedResources().stream()
                .filter(resource -> resource.kind() == BoundWorkflowResourceKind.RUNTIME_POLICY)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("RUNTIME_WORKFLOW_POLICY_BINDING_REQUIRED"));
    }

    void assemble() {
        List<BoundWorkflowNode> nodes = input.nodes().stream()
                .map(node -> new BoundWorkflowNode(
                        node.nodeId(), node.nodeType(), node.publishedType(), node.compilerId(),
                        node.configHash(), node.resources()))
                .toList();
        List<BoundWorkflowRoute> routes = input.routes().stream()
                .map(route -> new BoundWorkflowRoute(
                        route.edgeId(), route.fromNodeId(), route.toNodeId(), route.routeMode(),
                        route.ruleHash(), route.dataMappingHash(), route.priority(),
                        route.defaultEdge(), route.feedbackEdge()))
                .toList();
        List<String> stages = new ArrayList<>(completedStages);
        stages.add("BOUND_PLAN_ASSEMBLY");
        String planHash = policy.calculatePlanHash(
                input.schemaVersion(), input.definitionVersion(), input.definitionHash(), input.agentId(),
                input.sessionId(), input.runId(), input.projectId(), input.contextBundleId(),
                input.contextBundleHash(), input.startNodeId(), nodes, routes,
                input.sharedResources(), stages);
        output = new BoundWorkflowExecutionPlan(
                input.schemaVersion(), input.definitionVersion(), input.definitionHash(), input.agentId(),
                input.sessionId(), input.runId(), input.projectId(), input.contextBundleId(),
                input.contextBundleHash(), input.startNodeId(), nodes, routes, input.sharedResources(),
                planHash, input.boundAt(), stages);
        policy.assertPlanHash(output);
    }

    void completed(String stageId) {
        if (!"BOUND_PLAN_ASSEMBLY".equals(stageId)) completedStages.add(stageId);
    }

    BoundWorkflowExecutionPlan output() {
        if (output == null) throw new IllegalStateException("RUNTIME_WORKFLOW_BOUND_PLAN_MISSING");
        return output;
    }

    private List<BoundWorkflowResourceSnapshot> resources() {
        return java.util.stream.Stream.concat(
                        input.sharedResources().stream(),
                        input.nodes().stream().flatMap(node -> node.resources().stream()))
                .toList();
    }

    private void require(String value, String reasonCode) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(reasonCode);
    }
}
