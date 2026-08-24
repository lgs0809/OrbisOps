package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.alibaba.fastjson2.JSON;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Rechecks trusted definition, resource authorization and output before checkpointing or replaying. */
final class OpsWorkflowNodeOutputBoundary {
    private final BoundWorkflowExecutionPlan plan;
    private final OpsAgentDefinition definition;
    private final OpsAgentDefinitionValidator validator;
    private final WorkflowApprovalApplicationService approvals;
    private final String definitionFingerprint;
    private final Map<String, String> nodeFingerprints = new LinkedHashMap<>();
    private final Map<String, OpsWorkflowNodeOutputContract> contracts = new LinkedHashMap<>();

    OpsWorkflowNodeOutputBoundary(BoundWorkflowExecutionPlan plan, OpsAgentDefinition definition,
                                  OpsAgentDefinitionValidator validator, WorkflowApprovalApplicationService approvals) {
        this.plan = plan;
        this.definition = definition;
        this.validator = validator;
        this.approvals = approvals;
        definitionFingerprint = fingerprint(definition);
        for (OpsWorkflowNode node : definition.getNodes()) {
            nodeFingerprints.put(node.getNodeId(), fingerprint(node));
            contracts.put(node.getNodeId(), OpsWorkflowNodeOutputContract.compile(node));
        }
    }

    void validateNode(OpsAgentChatRequest request, OpsWorkflowNode node) {
        require(Objects.equals(plan.runId(), request.getRunId()), "RUN_MISMATCH");
        require(Objects.equals(plan.projectId(), request.getProjectId()), "PROJECT_MISMATCH");
        require(Objects.equals(plan.sessionId(), request.getSessionId()), "SESSION_MISMATCH");
        require(Objects.equals(plan.contextBundleId(), request.getMetadata().get("contextBundleId")), "CONTEXT_ID_MISMATCH");
        require(Objects.equals(plan.contextBundleHash(), request.getMetadata().get("contextBundleHash")), "CONTEXT_HASH_MISMATCH");
        require(node != null && Objects.equals(nodeFingerprints.get(node.getNodeId()), fingerprint(node)), "NODE_DRIFT");
        require(definitionFingerprint.equals(fingerprint(definition)), "DEFINITION_DRIFT");
        // Compilation reuses current project resource grants; a frozen definition never freezes permission revocation.
        var current = validator.compile(definition);
        require(Objects.equals(plan.agentId(), current.agentId())
                && plan.definitionVersion() == current.definitionVersion()
                && Objects.equals(plan.definitionHash(), current.definitionHash()), "DEFINITION_VERSION_MISMATCH");
    }

    void validate(OpsAgentChatRequest request, OpsWorkflowNode node, Map<String, Object> output) {
        validateNode(request, node);
        contracts.get(node.getNodeId()).validate(plan, node.getNodeId(), output);
        if ("HUMAN_APPROVAL".equalsIgnoreCase(node.getType())) {
            WorkflowApprovalRecord approval = approvals.find(plan.runId(), node.getNodeId()).orElseThrow(
                    () -> new SecurityException("WORKFLOW_NODE_APPROVAL_MISSING"));
            require(approval.projectId().equals(plan.projectId())
                    && (approval.status() == WorkflowApprovalRecord.Status.APPROVED
                    || approval.status() == WorkflowApprovalRecord.Status.REJECTED)
                    && approval.approvalId().equals(output.get("approvalId"))
                    && approval.status().name().equals(output.get("decision"))
                    && Objects.equals(approval.status() == WorkflowApprovalRecord.Status.APPROVED, output.get("approved"))
                    && approval.decidedBy().equals(output.get("decidedBy")), "APPROVAL_MISMATCH");
        }
    }

    private String fingerprint(Object value) {
        return CanonicalObjectHasher.sha256(JSON.parse(JSON.toJSONString(value)));
    }

    private void require(boolean condition, String reason) {
        if (!condition) throw new SecurityException("WORKFLOW_NODE_BOUNDARY_" + reason);
    }
}
