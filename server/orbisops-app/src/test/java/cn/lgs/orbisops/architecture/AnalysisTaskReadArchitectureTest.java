package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisTaskReadArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/analysis/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/analysis/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String TRIGGER_ANALYSIS = TRIGGER + "application/analysis/";

    @Test
    void domainOwnsTypedTaskFeedbackIncidentAndPresentationPolicy() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/AnalysisTaskPresentationPolicy.java");

        assertAll(
                () -> assertTrue(domain.contains("record AnalysisTaskSnapshot")),
                () -> assertTrue(domain.contains("record AnalysisTaskView")),
                () -> assertTrue(domain.contains("record AnalysisTaskIncident")),
                () -> assertTrue(domain.contains("record AnalysisTaskFeedback")),
                () -> assertTrue(domain.contains("interface IAnalysisTaskReadRepository")),
                () -> assertTrue(domain.contains("interface IAnalysisFeedbackRepository")),
                () -> assertTrue(policy.contains("HTTP_ERROR_ENVELOPE")),
                () -> assertTrue(policy.contains("ALERTMANAGER")),
                () -> assertTrue(policy.contains("模型工具协议校验失败")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("ai_ops_agent_run")),
                () -> assertFalse(domain.contains("ai_ops_analysis_feedback")));
    }

    @Test
    void applicationOwnsQueryDetailAndFeedbackUseCasesThroughPorts() throws IOException {
        String application = readJavaTree(APPLICATION);
        String query = read(APPLICATION + "AnalysisTaskQueryApplicationService.java");
        String feedback = read(APPLICATION + "AnalysisTaskFeedbackApplicationService.java");

        assertAll(
                () -> assertTrue(query.contains("IAnalysisTaskReadRepository")),
                () -> assertTrue(query.contains("IAnalysisFeedbackRepository")),
                () -> assertTrue(query.contains("AnalysisTaskSupplementPort")),
                () -> assertTrue(query.contains("public List<AnalysisTaskView> list")),
                () -> assertTrue(query.contains("public AnalysisTaskDetail detail")),
                () -> assertTrue(feedback.contains("AnalysisTaskOutcomePort")),
                () -> assertTrue(feedback.contains("AnalysisTaskAuditPort")),
                () -> assertTrue(feedback.contains("Supplier<String> feedbackIdSupplier")),
                () -> assertTrue(feedback.contains("Clock clock")),
                () -> assertFalse(application.contains("AnalysisTaskIdentityPort")),
                () -> assertTrue(feedback.contains("AnalysisTaskTransactionPort")),
                () -> assertTrue(feedback.contains("transactions.required")),
                () -> assertTrue(feedback.contains("feedback.save(item)")),
                () -> assertFalse(application.contains("AnalysisTaskPort")),
                () -> assertFalse(application.contains("AnalysisTaskApplicationService")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("ai_ops_agent_run")),
                () -> assertFalse(application.contains("ai_ops_analysis_feedback")));
    }

    @Test
    void infrastructureExclusivelyOwnsSharedRunIncidentFeedbackSqlAndJson() throws IOException {
        String readRepository = read(INFRASTRUCTURE + "JdbcAnalysisTaskReadRepository.java");
        String feedbackRepository = read(INFRASTRUCTURE + "JdbcAnalysisFeedbackRepository.java");
        String transactionAdapter = read(INFRASTRUCTURE + "SpringAnalysisTaskTransactionAdapter.java");

        assertAll(
                () -> assertTrue(readRepository.contains("implements IAnalysisTaskReadRepository")),
                () -> assertTrue(readRepository.contains("ai_ops_agent_run")),
                () -> assertTrue(readRepository.contains("ai_ops_incident_run")),
                () -> assertTrue(readRepository.contains("ai_ops_incident")),
                () -> assertTrue(readRepository.contains("JdbcTemplate")),
                () -> assertTrue(readRepository.contains("com.alibaba.fastjson")),
                () -> assertTrue(feedbackRepository.contains("implements IAnalysisFeedbackRepository")),
                () -> assertTrue(feedbackRepository.contains("@Transactional(transactionManager = \"mysqlTransactionManager\")")),
                () -> assertTrue(feedbackRepository.contains("ai_ops_analysis_feedback")),
                () -> assertTrue(feedbackRepository.contains("JdbcTemplate")),
                () -> assertTrue(transactionAdapter.contains("implements AnalysisTaskTransactionPort")),
                () -> assertTrue(transactionAdapter.contains("TransactionTemplate")),
                () -> assertTrue(transactionAdapter.contains("mysqlTransactionManager")),
                () -> assertFalse(readRepository.contains("模型工具协议校验失败")),
                () -> assertFalse(feedbackRepository.contains("ALERTMANAGER")));
    }

    @Test
    void triggerOnlyMapsAndAdaptsWithoutAnalysisTaskSqlOrLegacyService() throws IOException {
        String trigger = readJavaTree(TRIGGER);
        String analysis = readJavaTree(TRIGGER_ANALYSIS);
        String admin = read(TRIGGER + "http/admin/OpsAnalysisTaskAdminController.java");
        String user = read(TRIGGER + "http/agent/OpsUserAnalysisTaskController.java");
        String analysisSurface = analysis + admin + user;

        assertAll(
                () -> assertTrue(analysis.contains("class OpsAnalysisTaskMapper")),
                () -> assertTrue(analysis.contains("class OpsAnalysisTaskSupplementAdapter")),
                () -> assertTrue(analysis.contains("class OpsAnalysisTaskOutcomeAdapter")),
                () -> assertTrue(analysis.contains("class OpsAnalysisTaskAuditAdapter")),
                () -> assertTrue(analysis.contains("UUID.randomUUID()")),
                () -> assertTrue(analysis.contains("Clock.systemUTC()")),
                () -> assertFalse(analysis.contains("OpsAnalysisTaskIdentityAdapter")),
                () -> assertTrue(admin.contains("AnalysisTaskQueryApplicationService")),
                () -> assertTrue(admin.contains("AnalysisTaskFeedbackApplicationService")),
                () -> assertTrue(user.contains("AnalysisTaskQueryApplicationService")),
                () -> assertFalse(trigger.contains("OpsAnalysisTaskReadService")),
                () -> assertFalse(trigger.contains("OpsAnalysisTaskAdapter")),
                () -> assertFalse(analysis.contains("JdbcTemplate")),
                () -> assertFalse(analysis.contains("@Transactional")),
                () -> assertFalse(analysis.contains("com.alibaba.fastjson")),
                () -> assertFalse(analysisSurface.contains("ai_ops_agent_run")),
                () -> assertFalse(analysisSurface.contains("ai_ops_incident_run")),
                () -> assertFalse(analysisSurface.contains("ai_ops_analysis_feedback")),
                () -> assertFalse(analysis.contains("HTTP_ERROR_ENVELOPE")),
                () -> assertFalse(analysis.contains("模型工具协议校验失败")));
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
