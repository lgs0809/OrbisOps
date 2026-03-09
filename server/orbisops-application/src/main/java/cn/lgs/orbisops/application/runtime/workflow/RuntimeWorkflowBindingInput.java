package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;

import java.time.Instant;
import java.util.List;

public record RuntimeWorkflowBindingInput(
        int schemaVersion,
        int definitionVersion,
        String definitionHash,
        String agentId,
        String sessionId,
        String runId,
        String projectId,
        String contextBundleId,
        String contextBundleHash,
        String startNodeId,
        List<NodeInput> nodes,
        List<RouteInput> routes,
        List<BoundWorkflowResourceSnapshot> sharedResources,
        Instant boundAt
) {

    public RuntimeWorkflowBindingInput {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        routes = routes == null ? List.of() : List.copyOf(routes);
        sharedResources = sharedResources == null ? List.of() : List.copyOf(sharedResources);
        if (boundAt == null) throw new IllegalArgumentException("RUNTIME_WORKFLOW_BOUND_AT_REQUIRED");
    }

    public record NodeInput(
            String nodeId,
            String nodeType,
            String publishedType,
            String compilerId,
            String configHash,
            List<BoundWorkflowResourceSnapshot> resources
    ) {
        public NodeInput {
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }

    public record RouteInput(
            String edgeId,
            String fromNodeId,
            String toNodeId,
            String routeMode,
            String ruleHash,
            String dataMappingHash,
            int priority,
            boolean defaultEdge,
            boolean feedbackEdge
    ) {
    }
}
