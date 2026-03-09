package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.application.agenteval.AgentEvalCreateSuiteCommand;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentMicrokernelPolicy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Application process manager for ensuring and releasing one project's default operations Agent. */
public final class ProjectDefaultAgentBootstrapUseCase<D> {

    private final AgentDefinitionLifecycleUseCase<D> lifecycleUseCase;
    private final ProjectDefaultAgentDefinitionPort<D> definitionPort;
    private final ProjectDefaultAgentEvalPort evalPort;
    private final ProjectDefaultAgentAuditPort<D> auditPort;
    private final AgentMicrokernelPolicy microkernelPolicy;
    private final ProjectDefaultAgentEvalSuiteFactory suiteFactory;

    public ProjectDefaultAgentBootstrapUseCase(
            AgentDefinitionLifecycleUseCase<D> lifecycleUseCase,
            ProjectDefaultAgentDefinitionPort<D> definitionPort,
            ProjectDefaultAgentEvalPort evalPort,
            ProjectDefaultAgentAuditPort<D> auditPort) {
        this(
                lifecycleUseCase,
                definitionPort,
                evalPort,
                auditPort,
                new AgentMicrokernelPolicy(),
                new ProjectDefaultAgentEvalSuiteFactory());
    }

    ProjectDefaultAgentBootstrapUseCase(
            AgentDefinitionLifecycleUseCase<D> lifecycleUseCase,
            ProjectDefaultAgentDefinitionPort<D> definitionPort,
            ProjectDefaultAgentEvalPort evalPort,
            ProjectDefaultAgentAuditPort<D> auditPort,
            AgentMicrokernelPolicy microkernelPolicy,
            ProjectDefaultAgentEvalSuiteFactory suiteFactory) {
        if (lifecycleUseCase == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_LIFECYCLE_REQUIRED");
        }
        if (definitionPort == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_DEFINITION_PORT_REQUIRED");
        }
        if (evalPort == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_EVAL_PORT_REQUIRED");
        }
        if (auditPort == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_AUDIT_PORT_REQUIRED");
        }
        if (microkernelPolicy == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_MICROKERNEL_POLICY_REQUIRED");
        }
        if (suiteFactory == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_EVAL_SUITE_FACTORY_REQUIRED");
        }
        this.lifecycleUseCase = lifecycleUseCase;
        this.definitionPort = definitionPort;
        this.evalPort = evalPort;
        this.auditPort = auditPort;
        this.microkernelPolicy = microkernelPolicy;
        this.suiteFactory = suiteFactory;
    }

    public ProjectDefaultAgentBootstrapResult<D> ensure(
            ProjectDefaultAgentBootstrapCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_COMMAND_REQUIRED");
        }
        String projectId = definitionPort.requireExistingProject(command.projectId());
        String agentId = projectId + "-ops-agent";
        Optional<D> published = latestPublished(projectId, agentId);
        if (published.isPresent()
                && microkernelPolicy.supportsBuiltInMicrokernel(
                        definitionPort.facts(published.get()).agentScopeRoles())) {
            return new ProjectDefaultAgentBootstrapResult<>(
                    published.get(),
                    true,
                    "reuse-default",
                    "",
                    "");
        }

        D template = definitionPort.loadDefaultTemplate();
        if (template == null) {
            throw new IllegalStateException(
                    "DEFAULT_AGENT_TEMPLATE_MISSING：平台默认 Agent 模板不可用");
        }
        D sanitized = definitionPort.sanitizeForProject(template, projectId);
        D prepared = definitionPort.prepareDraft(
                sanitized,
                projectId,
                agentId,
                command.agentName());
        D normalized = definitionPort.normalizeExecutionShape(prepared);
        D draft = lifecycleUseCase.saveDraft(normalized);
        int version = definitionPort.facts(draft).requireVersion();
        D validated = lifecycleUseCase.validate(agentId, version);
        ProjectDefaultAgentDefinitionFacts validatedFacts = definitionPort.facts(validated);

        AgentEvalCreateSuiteCommand suiteCommand = suiteFactory.create(
                projectId,
                agentId,
                command.actor());
        AgentEvalSuite suite = evalPort.createReleaseSuite(suiteCommand);
        AgentEvalRunResult evaluation = evalPort.runReleaseEvaluation(
                projectId,
                agentId,
                version,
                suite.suiteId(),
                command.actor());
        if (!evaluation.passed()) {
            throw new IllegalStateException(
                    "AGENT_EVAL_GATE_NOT_PASSED：默认 Agent 行为测评未通过");
        }
        evalPort.assertReleaseAllowed(
                projectId,
                agentId,
                validatedFacts.requireVersion(),
                validatedFacts.definitionHash());
        D released = lifecycleUseCase.publish(agentId, version);
        String action = published.isPresent() ? "upgrade-default" : "create-default";
        auditPort.record(
                action,
                agentId,
                published.orElse(null),
                released,
                suite.suiteId(),
                evaluation.evalRunId());
        return new ProjectDefaultAgentBootstrapResult<>(
                released,
                false,
                action,
                suite.suiteId(),
                evaluation.evalRunId());
    }

    private Optional<D> latestPublished(String projectId, String agentId) {
        List<D> versions = definitionPort.versions(agentId);
        return (versions == null ? List.<D>of() : versions).stream()
                .filter(java.util.Objects::nonNull)
                .filter(definition -> definitionPort.facts(definition).publishedFor(projectId))
                .max(Comparator.comparingInt(definition ->
                        Optional.ofNullable(definitionPort.facts(definition).version())
                                .orElse(0)));
    }
}
