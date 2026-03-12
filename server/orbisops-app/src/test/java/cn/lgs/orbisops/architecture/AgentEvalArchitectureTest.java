package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvalArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agenteval/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/agenteval/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcAgentEvalRepository.java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String TRIGGER_EVAL = TRIGGER + "application/agenteval/";

    @Test
    void domainOwnsTypedDefinitionCaseSuiteRunPolicyAndRepository() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/AgentEvalPolicy.java");
        String repository = read(DOMAIN + "adapter/repository/IAgentEvalRepository.java");

        assertAll(
                () -> assertTrue(domain.contains("record AgentEvalDefinitionSnapshot")),
                () -> assertTrue(domain.contains("record AgentEvalCase")),
                () -> assertTrue(domain.contains("record AgentEvalSuite")),
                () -> assertTrue(domain.contains("record AgentEvalRunStart")),
                () -> assertTrue(domain.contains("record AgentEvalCaseExecution")),
                () -> assertTrue(domain.contains("record AgentEvalRunResult")),
                () -> assertTrue(domain.contains("class AgentEvalPolicy")),
                () -> assertTrue(repository.contains("AgentEvalSuite saveSuite")),
                () -> assertTrue(repository.contains("void saveRun(AgentEvalRunStart run, AgentEvalRunResult result")),
                () -> assertTrue(repository.contains("hasPassedReleaseGate")),
                () -> assertFalse(policy.contains("INTENT_MISMATCH")),
                () -> assertTrue(policy.contains("legacyExpectedIntentIgnored")),
                () -> assertTrue(policy.contains("GRAPH_END_UNREACHABLE")),
                () -> assertTrue(policy.contains("PRE_APPROVAL_TARGET_WRITE_DECLARED")),
                () -> assertTrue(policy.contains("TOOL_CALL_BUDGET_EXCEEDED")),
                () -> assertTrue(policy.contains("TOKEN_BUDGET_EXCEEDED")),
                () -> assertTrue(policy.contains("LATENCY_BUDGET_EXCEEDED")),
                () -> assertTrue(policy.contains("OUTPUT_CONTRACT_MISSING")),
                () -> assertTrue(policy.contains("regression")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("ai_ops_agent_eval_")));
    }

    @Test
    void applicationOwnsSuiteRunBaselineRegressionAndReleaseGateOrchestration() throws IOException {
        String application = readJavaTree(APPLICATION);
        String service = read(APPLICATION + "AgentEvalApplicationService.java");

        assertAll(
                () -> assertTrue(service.contains("IAgentEvalRepository")),
                () -> assertTrue(service.contains("AgentEvalPolicy")),
                () -> assertTrue(service.contains("AgentEvalDefinitionPort")),
                () -> assertFalse(service.contains("AgentEvalIntentPort")),
                () -> assertTrue(service.contains("AgentEvalAuditPort")),
                () -> assertTrue(service.contains("AgentEvalIdentityFactory")),
                () -> assertTrue(application.contains("class AgentEvalIdentityFactory")),
                () -> assertFalse(application.contains("AgentEvalIdentityPort")),
                () -> assertTrue(service.contains("public AgentEvalSuite createSuite")),
                () -> assertTrue(service.contains("public AgentEvalRunResult run")),
                () -> assertTrue(service.contains("publishedBaseline")),
                () -> assertTrue(service.contains("repository.saveRun(runStart, result")),
                () -> assertTrue(service.contains("repository.hasPassedReleaseGate")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("ai_ops_agent_eval_")));
    }

    @Test
    void infrastructureExclusivelyOwnsEvalTransactionsSqlAndJson() throws IOException {
        String infrastructure = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IAgentEvalRepository")),
                () -> assertTrue(infrastructure.contains("@Transactional(transactionManager = \"mysqlTransactionManager\")")),
                () -> assertTrue(infrastructure.contains("AgentEvalSuite saveSuite")),
                () -> assertTrue(infrastructure.contains("void saveRun")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_eval_suite")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_eval_case")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_eval_run")),
                () -> assertTrue(infrastructure.contains("ai_ops_agent_eval_case_run")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertFalse(infrastructure.contains("INTENT_MISMATCH")),
                () -> assertFalse(infrastructure.contains("GRAPH_END_UNREACHABLE")),
                () -> assertFalse(infrastructure.contains("PRE_APPROVAL_TARGET_WRITE_DECLARED")),
                () -> assertFalse(infrastructure.contains("TOKEN_BUDGET_EXCEEDED")));
    }

    @Test
    void triggerUsesTypedAdapterWithoutEvalSqlJsonTransactionsOrDomainReasons() throws IOException {
        String trigger = readJavaTree(TRIGGER);
        String evalAdapters = readJavaTree(TRIGGER_EVAL);
        String definitionLifecycle = read(TRIGGER + "application/agentdefinition/OpsAgentDefinitionLifecycleAdapter.java");
        String definitionAssembly = read(TRIGGER + "application/agentdefinition/OpsAgentDefinitionManagementAssembly.java");
        String definitionApplication = read(TRIGGER + "application/ops/OpsAgentDefinitionApplicationService.java");

        assertAll(
                () -> assertTrue(evalAdapters.contains("class OpsAgentEvalAdapter")),
                () -> assertTrue(evalAdapters.contains("AgentEvalApplicationService")),
                () -> assertFalse(evalAdapters.contains("OpsIntentRuleClassifier")),
                () -> assertTrue(definitionLifecycle.contains("OpsAgentEvalAdapter")),
                () -> assertTrue(definitionAssembly.contains("OpsAgentEvalAdapter evalAdapter")),
                () -> assertTrue(definitionAssembly.contains("new OpsAgentDefinitionEvalAdapter(capabilityService, evalAdapter)")),
                () -> assertTrue(definitionApplication.contains("AgentDefinitionEvalUseCase")),
                () -> assertTrue(definitionApplication.contains("this.evalUseCase = assembly.evalUseCase()")),
                () -> assertFalse(definitionApplication.contains("OpsAgentEvalAdapter")),
                () -> assertFalse(trigger.contains("OpsAgentEvalIdentityAdapter")),
                () -> assertFalse(trigger.contains("OpsAgentEvalService")),
                () -> assertFalse(trigger.contains("ai_ops_agent_eval_suite")),
                () -> assertFalse(trigger.contains("ai_ops_agent_eval_case")),
                () -> assertFalse(trigger.contains("ai_ops_agent_eval_run")),
                () -> assertFalse(trigger.contains("ai_ops_agent_eval_case_run")),
                () -> assertFalse(evalAdapters.contains("JdbcTemplate")),
                () -> assertFalse(evalAdapters.contains("@Transactional")),
                () -> assertFalse(evalAdapters.contains("com.alibaba.fastjson")),
                () -> assertFalse(evalAdapters.contains("INTENT_MISMATCH")),
                () -> assertFalse(evalAdapters.contains("GRAPH_END_UNREACHABLE")),
                () -> assertFalse(evalAdapters.contains("PRE_APPROVAL_TARGET_WRITE_DECLARED")),
                () -> assertFalse(evalAdapters.contains("TOKEN_BUDGET_EXCEEDED")));
    }

    private String readJavaTree(String relativeRoot) throws IOException {
        Path root = projectRoot().resolve(relativeRoot);
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
