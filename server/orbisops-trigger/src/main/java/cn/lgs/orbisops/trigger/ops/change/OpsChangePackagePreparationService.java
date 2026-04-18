package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationPlan;
import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationPlanPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageRevisionPlan;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationDecision;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackagePreparationPolicy;
import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PREPARE phase entry point.
 *
 * <p>This is intentionally conservative: it selects a Preparation Agent
 * definition and creates a structured ChangePackage with explicit preflight and,
 * when the package declares a validation operation, trusted validation evidence.
 * It does not execute target-environment writes and never fabricates validation success.</p>
 */
@Service
public class OpsChangePackagePreparationService implements ChangePackagePreparationPlanPort {

    private static final ChangePackagePreparationPolicy PREPARATION_POLICY =
            new ChangePackagePreparationPolicy();
    private static final OpsPreparationMethodReferenceFactory PREPARATION_METHOD_REFERENCE_FACTORY =
            new OpsPreparationMethodReferenceFactory();
    private static final OpsPreparationPlanEnvelopeFactory PREPARATION_PLAN_ENVELOPE_FACTORY =
            new OpsPreparationPlanEnvelopeFactory();
    private static final OpsPreparationProofBundleFactory PREPARATION_PROOF_BUNDLE_FACTORY =
            new OpsPreparationProofBundleFactory();
    private static final OpsPreparationOperationBindingFactory PREPARATION_OPERATION_BINDING_FACTORY =
            new OpsPreparationOperationBindingFactory();
    private static final OpsPreparationPackageDraftFactory PREPARATION_PACKAGE_DRAFT_FACTORY =
            new OpsPreparationPackageDraftFactory();
    private static final OpsPreparationAuditPayloadFactory PREPARATION_AUDIT_PAYLOAD_FACTORY =
            new OpsPreparationAuditPayloadFactory();

    private final OpsPreparationAgentResolver preparationAgentResolver;
    private final OpsPreparationContextBundleService contextBundleService;
    private final OpsPreparationEvidenceService preparationEvidenceService;
    private final OpsPreparationRepairContextHydrator repairContextHydrator;
    private final OpsChangePackageActionPolicyBinder actionPolicyBinder;
    private final ObjectProvider<OpsConfigAuditService> auditServiceProvider;

    public OpsChangePackagePreparationService(
            OpsAgentDefinitionQueryGateway agentDefinitionRegistry,
            ObjectProvider<ProjectDefinitionApplicationService> projectDefinitionProvider,
            ObjectProvider<OpsConfigAuditService> auditServiceProvider) {
        this(
                agentDefinitionRegistry,
                projectDefinitionProvider,
                auditServiceProvider,
                null,
                null,
                null,
                null);
    }

    public OpsChangePackagePreparationService(
            OpsAgentDefinitionQueryGateway agentDefinitionRegistry,
            ObjectProvider<ProjectDefinitionApplicationService> projectDefinitionProvider,
            ObjectProvider<OpsConfigAuditService> auditServiceProvider,
            ObjectProvider<OpsRuntimeContextBundleAdapter> runtimeContextBundleServiceProvider,
            ObjectProvider<OpsEvidenceStore> evidenceStoreProvider,
            OpsChangePackageActionPolicyBinder actionPolicyBinder) {
        this(agentDefinitionRegistry, projectDefinitionProvider, auditServiceProvider,
                runtimeContextBundleServiceProvider, evidenceStoreProvider, null, actionPolicyBinder);
    }

    @Autowired
    public OpsChangePackagePreparationService(
            OpsAgentDefinitionQueryGateway agentDefinitionRegistry,
            ObjectProvider<ProjectDefinitionApplicationService> projectDefinitionProvider,
            ObjectProvider<OpsConfigAuditService> auditServiceProvider,
            ObjectProvider<OpsRuntimeContextBundleAdapter> runtimeContextBundleServiceProvider,
            ObjectProvider<OpsEvidenceStore> evidenceStoreProvider,
            ObjectProvider<OpsRepairWorkspaceService> repairWorkspaceServiceProvider,
            OpsChangePackageActionPolicyBinder actionPolicyBinder) {
        this.preparationAgentResolver = new OpsPreparationAgentResolver(
                agentDefinitionRegistry,
                () -> available(projectDefinitionProvider));
        this.contextBundleService = new OpsPreparationContextBundleService(
                () -> available(runtimeContextBundleServiceProvider));
        // Tool execution happens inside the owning PREPARE Agent run. This service is a
        // deterministic package compiler/validator and never starts a second tool chain.
        this.preparationEvidenceService = new OpsPreparationEvidenceService(
                () -> available(evidenceStoreProvider));
        this.repairContextHydrator = new OpsPreparationRepairContextHydrator(
                available(repairWorkspaceServiceProvider));
        this.actionPolicyBinder = actionPolicyBinder;
        this.auditServiceProvider = auditServiceProvider;
    }

    public ChangePackagePreparationPlan prepareForSession(
            String sessionId,
            Map<String, Object> request,
            String actor) {
        return prepare(
                contextBundleService.bindLatestForSession(
                        sessionId,
                        request,
                        actor),
                actor);
    }

    @Override
    public ChangePackageRevisionPlan revise(
            String packageId,
            Map<String, Object> request,
            String actor) {
        Map<String, Object> safe = contextBundleService.validateRevision(request);
        new WorkflowChangeVerificationPolicy().validateDeclaredCriteria(safe.get("verificationCriteria"));
        return ChangePackageRevisionPlan.from(safe);
    }

    @Override
    public ChangePackagePreparationPlan prepare(
            Map<String, Object> request,
            String actor) {
        Map<String, Object> safe = new LinkedHashMap<>(
                request == null ? Map.of() : request);
        Map<String, Object> contextBundle = contextBundleService.requireForRequest(safe);
        contextBundleService.applyAuthoritative(safe, contextBundle);
        new WorkflowChangeVerificationPolicy().validateDeclaredCriteria(safe.get("verificationCriteria"));
        String projectId = requireText(
                safe.get("projectId"),
                "PREPARE 必须提供 projectId");
        repairContextHydrator.hydrate(safe, projectId);
        OpsAgentDefinition preparationAgent = preparationAgentResolver.resolve(
                projectId,
                safe);
        return ChangePackagePreparationPlan.from(buildPackageRequest(
                projectId,
                safe,
                preparationAgent,
                actor));
    }


    private Map<String, Object> buildPackageRequest(
            String projectId,
            Map<String, Object> request,
            OpsAgentDefinition agent,
            String actor) {
        String question = text(
                firstNonNull(
                        request.get("question"),
                        request.get("incident"),
                        request.get("objective")),
                "");
        String objective = text(
                request.get("objective"),
                StringUtils.hasText(question)
                        ? question
                        : "处理当前运维问题");
        int maxIterations = Math.max(
                1,
                Math.min(
                        intValue(request.get("maxPreparationIterations"), 2),
                        5));

        Object authoritativeSteps = actionPolicyBinder == null
                ? request.get("mcpSteps")
                : actionPolicyBinder.bind(projectId, request.get("mcpSteps"));
        OpsPreparationOperationBindingFactory.OperationBundle operationBundle =
                PREPARATION_OPERATION_BINDING_FACTORY.create(
                        authoritativeSteps,
                        request);
        List<Map<String, Object>> mcpSteps = operationBundle.operations();
        List<Map<String, Object>> toolBindings = operationBundle.toolBindings();
        Map<String, Object> activePrepare = verifiedPrepareResult(request);
        OpsPreparationEvidenceService.EvidenceBundle evidenceBundle =
                preparationEvidenceService.resolve(projectId, request);
        OpsPreparationProofBundleFactory.ProofBundle proofBundle =
                PREPARATION_PROOF_BUNDLE_FACTORY.create(
                        new OpsPreparationProofBundleFactory.Input(
                                request,
                                activePrepare,
                                mcpSteps,
                                toolBindings,
                                evidenceBundle.present()));
        ChangePackagePreparationDecision decision =
                PREPARATION_POLICY.decide(proofBundle.context());
        List<String> limitations = List.copyOf(decision.limitations());
        String assessment = decision.assessment().name();
        String riskLevel = decision.riskLevel();
        if (decision.requestedRiskEscalated()) {
            OpsPreparationAuditPayloadFactory.AuditPayload auditPayload =
                    PREPARATION_AUDIT_PAYLOAD_FACTORY.riskEscalated(
                            request,
                            riskLevel);
            audit(
                    projectId,
                    "change-package-prepare",
                    "risk-escalated",
                    auditPayload.targetId(),
                    auditPayload.before(),
                    auditPayload.after());
        }
        OpsPreparationPlanEnvelopeFactory.Envelope planEnvelope =
                PREPARATION_PLAN_ENVELOPE_FACTORY.create(
                        new OpsPreparationPlanEnvelopeFactory.Input(
                                objective,
                                maxIterations,
                                assessment,
                                request,
                                mcpSteps,
                                limitations,
                                riskLevel));
        Map<String, Object> contextBundle =
                contextBundleService.requireForRequest(request);
        Map<String, Object> preparationMethodRef =
                PREPARATION_METHOD_REFERENCE_FACTORY.create(request, agent);
        return PREPARATION_PACKAGE_DRAFT_FACTORY.create(
                new OpsPreparationPackageDraftFactory.Input(
                        projectId,
                        actor,
                        question,
                        objective,
                        request,
                        agent,
                        decision,
                        operationBundle,
                        evidenceBundle,
                        proofBundle,
                        planEnvelope,
                        contextBundle,
                        preparationMethodRef));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifiedPrepareResult(Map<String, Object> request) {
        Object value = firstNonNull(
                request.get("prepareExecution"),
                request.get("activePrepare"),
                request.get("verifiedToolResults"));
        if (value instanceof Map<?, ?> map) {
            return Map.copyOf((Map<String, Object>) map);
        }
        return Map.of(
                "status", "NOT_EXECUTED_BY_PACKAGE_COMPILER",
                "source", "OWNING_PREPARE_AGENT_RUN_REQUIRED",
                "verified", false);
    }

    private void audit(
            String projectId,
            String module,
            String action,
            String targetId,
            Object before,
            Object after) {
        OpsConfigAuditService auditService = available(auditServiceProvider);
        if (auditService != null) {
            auditService.record(
                    projectId,
                    module,
                    action,
                    targetId,
                    before,
                    after);
        }
    }

    private static <T> T available(ObjectProvider<T> provider) {
        return provider == null ? null : provider.getIfAvailable();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null
                    ? fallback
                    : Integer.parseInt(String.valueOf(value));
        } catch (Exception error) {
            return fallback;
        }
    }

    private String requireText(Object value, String message) {
        String text = text(value, "");
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? fallback
                : String.valueOf(value).trim();
    }
}
