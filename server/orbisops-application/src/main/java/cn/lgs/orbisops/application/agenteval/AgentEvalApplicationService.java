package cn.lgs.orbisops.application.agenteval;

import cn.lgs.orbisops.domain.agenteval.adapter.repository.IAgentEvalRepository;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseExecution;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunStart;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.domain.agenteval.service.AgentEvalPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Static/fixture-based Agent definition eval. It does not run a business Intent classifier. */
public final class AgentEvalApplicationService {

    private final IAgentEvalRepository repository;
    private final AgentEvalDefinitionPort definitions;
    private final AgentEvalAuditPort audit;
    private final AgentEvalIdentityFactory identities;
    private final AgentEvalPolicy policy;

    public AgentEvalApplicationService(
            IAgentEvalRepository repository,
            AgentEvalDefinitionPort definitions,
            AgentEvalAuditPort audit,
            AgentEvalIdentityFactory identities) {
        this(repository, definitions, audit, identities, new AgentEvalPolicy());
    }

    AgentEvalApplicationService(
            IAgentEvalRepository repository,
            AgentEvalDefinitionPort definitions,
            AgentEvalAuditPort audit,
            AgentEvalIdentityFactory identities,
            AgentEvalPolicy policy) {
        if (repository == null) throw new IllegalArgumentException("AGENT_EVAL_REPOSITORY_REQUIRED");
        if (definitions == null) throw new IllegalArgumentException("AGENT_EVAL_DEFINITIONS_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("AGENT_EVAL_AUDIT_REQUIRED");
        if (identities == null) throw new IllegalArgumentException("AGENT_EVAL_IDENTITY_FACTORY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("AGENT_EVAL_POLICY_REQUIRED");
        this.repository = repository;
        this.definitions = definitions;
        this.audit = audit;
        this.identities = identities;
        this.policy = policy;
    }

    public AgentEvalSuite createSuite(AgentEvalCreateSuiteCommand command) {
        if (command == null) throw new IllegalArgumentException("AGENT_EVAL_SUITE_COMMAND_REQUIRED");
        String suiteId = text(command.suiteId()).isBlank() ? identities.newSuiteId() : text(command.suiteId());
        String agentId = required(command.agentId(), "agentId");
        String name = text(command.name()).isBlank() ? agentId + " 发布门禁" : text(command.name());
        List<AgentEvalCase> cases = new ArrayList<>();
        int order = 0;
        for (AgentEvalCase source : command.cases()) {
            order++;
            String caseId = source.caseId().isBlank() ? suiteId + "-case-" + order : source.caseId();
            cases.add(source.withCaseId(caseId));
        }
        AgentEvalSuite suite = new AgentEvalSuite(
                suiteId,
                required(command.projectId(), "projectId"),
                agentId,
                name,
                1,
                cases,
                text(command.actor()));
        AgentEvalSuite saved = repository.saveSuite(suite);
        audit.recordSuiteCreated(saved);
        return saved;
    }

    public AgentEvalRunResult run(
            String projectId,
            String agentId,
            int version,
            String suiteId,
            String actor) {
        String project = required(projectId, "projectId");
        String agent = required(agentId, "agentId");
        if (version <= 0) throw new IllegalArgumentException("version 必须大于 0");
        AgentEvalDefinitionSnapshot definition = definitions.resolve(agent, version);
        if (!project.equals(definition.projectId())) {
            throw new IllegalArgumentException("Agent Eval 不能跨项目运行");
        }
        AgentEvalSuite suite = repository.findSuite(required(suiteId, "suiteId"), project)
                .orElseThrow(() -> new IllegalArgumentException("Agent Eval Suite 不存在或不属于当前项目"));
        if (!agent.equals(suite.agentId())) {
            throw new IllegalArgumentException("Eval Suite 不属于当前 Agent");
        }
        if (suite.cases().isEmpty()) throw new IllegalStateException("AGENT_EVAL_CASES_MISSING");

        AgentEvalDefinitionSnapshot baseline = definitions.publishedBaseline(project, agent, version).orElse(null);
        String evalRunId = identities.newRunId();
        Instant startedAt = identities.now();
        AgentEvalRunStart runStart = new AgentEvalRunStart(
                evalRunId,
                suite.suiteId(),
                project,
                agent,
                version,
                definition.definitionHash(),
                baseline == null ? 0 : baseline.version(),
                baseline == null ? "" : baseline.definitionHash(),
                suite.cases().size(),
                text(actor),
                startedAt);

        List<AgentEvalCaseExecution> executions = new ArrayList<>();
        List<AgentEvalCaseResult> baselineResults = new ArrayList<>();
        for (AgentEvalCase evalCase : suite.cases()) {
            Instant caseStarted = identities.now();
            AgentEvalCaseResult result = policy.evaluate(definition, evalCase);
            AgentEvalCaseResult baselineResult = baseline == null
                    ? null
                    : policy.evaluate(baseline, evalCase);
            AgentEvalCaseExecution execution = new AgentEvalCaseExecution(
                    identities.newCaseRunId(),
                    evalRunId,
                    evalCase.caseId(),
                    project,
                    agent,
                    version,
                    result,
                    caseStarted,
                    identities.now());
            executions.add(execution);
            if (baselineResult != null) baselineResults.add(baselineResult);
        }

        AgentEvalRunResult result = policy.summarize(
                evalRunId, suite.suiteId(), definition, baseline, executions, baselineResults);
        repository.saveRun(runStart, result, identities.now());
        audit.recordRunCompleted(result, text(actor));
        return result;
    }

    public void assertReleaseAllowed(
            String projectId,
            String agentId,
            Integer version,
            String definitionHash) {
        if (version == null || version <= 0 || text(definitionHash).isBlank()) {
            throw new IllegalStateException("AGENT_RELEASE_DEFINITION_NOT_PINNED");
        }
        if (!repository.hasPassedReleaseGate(
                required(projectId, "projectId"),
                required(agentId, "agentId"),
                version,
                definitionHash)) {
            throw new IllegalStateException(
                    "AGENT_EVAL_GATE_NOT_PASSED：发布前必须完成同一 version/definitionHash 的 Eval");
        }
    }

    private String required(String value, String field) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
