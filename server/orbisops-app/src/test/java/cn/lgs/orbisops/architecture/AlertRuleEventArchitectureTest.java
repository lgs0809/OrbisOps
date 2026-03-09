package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleEventArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/alert/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/alert/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedRuleEventOutcomeRepositoriesAndPolicies() throws IOException {
        String domain = readJavaTree(DOMAIN);

        assertAll(
                () -> assertTrue(domain.contains("record AlertRuleCandidate")),
                () -> assertTrue(domain.contains("record AlertRuleDefinition")),
                () -> assertTrue(domain.contains("record AlertAgentResolution")),
                () -> assertTrue(domain.contains("record AlertEventDraft")),
                () -> assertTrue(domain.contains("record AlertEventSnapshot")),
                () -> assertTrue(domain.contains("record AlertRunOutcome")),
                () -> assertTrue(domain.contains("interface IAlertRuleRepository")),
                () -> assertTrue(domain.contains("interface IAlertEventRepository")),
                () -> assertTrue(domain.contains("class AlertRuleDefinitionPolicy")),
                () -> assertTrue(domain.contains("class AlertEventPolicy")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(domain.contains("ai_ops_alert_trigger_rule")),
                () -> assertFalse(domain.contains("ai_ops_alert_trigger_event")));
    }

    @Test
    void applicationOwnsTypedRuleManagementEventLifecycleAuditAndTransactions() throws IOException {
        String application = readJavaTree(APPLICATION);
        String rules = read(APPLICATION + "AlertRuleManagementApplicationService.java");
        String events = read(APPLICATION + "AlertEventApplicationService.java");

        assertAll(
                () -> assertTrue(application.contains("record AlertRuleAuditEvent")),
                () -> assertTrue(application.contains("interface AlertRuleAgentResolverPort")),
                () -> assertTrue(application.contains("interface AlertRuleTransactionPort")),
                () -> assertTrue(application.contains("interface AlertEventIncidentPort")),
                () -> assertTrue(rules.contains("transactions.required")),
                () -> assertTrue(rules.contains("new AlertRuleAuditEvent")),
                () -> assertTrue(events.contains("events.updateRunOutcome")),
                () -> assertTrue(events.contains("incidents.ingest")),
                () -> assertFalse(application.contains("OpsAlertTriggerRule")),
                () -> assertFalse(application.contains("OpsAlertTriggerEvent")),
                () -> assertFalse(application.contains("Map<String, Object> request")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("ai_ops_alert_trigger_rule")),
                () -> assertFalse(application.contains("ai_ops_alert_trigger_event")));
    }

    @Test
    void infrastructureExclusivelyOwnsRuleEventSqlJsonSchemaAndGeneratedKeys() throws IOException {
        String rules = read(INFRASTRUCTURE + "JdbcAlertRuleRepository.java");
        String events = read(INFRASTRUCTURE + "JdbcAlertEventRepository.java");
        String schema = read(INFRASTRUCTURE + "JdbcAlertTriggerSchemaInitializer.java");
        String transaction = read(INFRASTRUCTURE + "SpringAlertRuleTransactionAdapter.java");

        assertAll(
                () -> assertTrue(rules.contains("implements IAlertRuleRepository")),
                () -> assertTrue(rules.contains("ai_ops_alert_trigger_rule")),
                () -> assertTrue(rules.contains("GeneratedKeyHolder")),
                () -> assertTrue(rules.contains("RowMapper<AlertRuleDefinition>")),
                () -> assertTrue(rules.contains("JSON.toJSONString")),
                () -> assertTrue(events.contains("implements IAlertEventRepository")),
                () -> assertTrue(events.contains("ai_ops_alert_trigger_event")),
                () -> assertTrue(events.contains("GeneratedKeyHolder")),
                () -> assertTrue(events.contains("RowMapper<AlertEventSnapshot>")),
                () -> assertTrue(events.contains("CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_rule")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_event")),
                () -> assertTrue(schema.contains("information_schema.columns")),
                () -> assertTrue(transaction.contains("TransactionTemplate")),
                () -> assertTrue(transaction.contains("mysqlTransactionManager")));
    }

    @Test
    void triggerOnlyCoordinatesTypedApplicationsAndCompatibilityMappers() throws IOException {
        String triggerService = read(TRIGGER + "ops/OpsAlertTriggerService.java");
        String ruleCatalog = read(TRIGGER + "ops/OpsAlertRuleCatalog.java");
        String eventRecorder = read(TRIGGER + "ops/OpsAlertEventRecorder.java");
        String analysisRun = read(TRIGGER + "ops/OpsAnalysisRunService.java");
        String outcomeAdapter = read(TRIGGER + "ops/OpsAsyncAnalysisOutcomeAdapter.java");
        String outcomeReporter = read(TRIGGER + "ops/OpsAnalysisRunAlertOutcomeReporter.java");
        String triggerAlert = readJavaTree(TRIGGER + "application/alert/");

        assertAll(
                () -> assertTrue(triggerService.contains("OpsAlertRuleCatalog rules")),
                () -> assertTrue(triggerService.contains("OpsAlertEventRecorder events")),
                () -> assertTrue(ruleCatalog.contains("AlertRuleManagementApplicationService")),
                () -> assertTrue(ruleCatalog.contains("mapper.views(alertRules.list())")),
                () -> assertTrue(eventRecorder.contains("AlertEventApplicationService")),
                () -> assertTrue(eventRecorder.contains("alertEvents.append(mapper.draft(")),
                () -> assertTrue(outcomeAdapter.contains("OpsAnalysisRunAlertOutcomeReporter alertReporter")),
                () -> assertTrue(outcomeAdapter.contains("alertReporter.report(")),
                () -> assertFalse(analysisRun.contains("new AlertRunOutcome")),
                () -> assertFalse(analysisRun.contains("alertEvents.updateRunOutcome")),
                () -> assertTrue(outcomeReporter.contains("new AlertRunOutcome(")),
                () -> assertTrue(outcomeReporter.contains("alertEvents.updateRunOutcome")),
                () -> assertFalse(outcomeReporter.contains("@Service")),
                () -> assertTrue(triggerAlert.contains("class OpsAlertRuleMapper")),
                () -> assertTrue(triggerAlert.contains("class OpsAlertEventMapper")),
                () -> assertTrue(triggerAlert.contains("class OpsAlertTriggerExecutionAdapter")),
                () -> assertFalse(triggerService.contains("JdbcTemplate")),
                () -> assertFalse(triggerService.contains("RowMapper")),
                () -> assertFalse(triggerService.contains("PreparedStatement")),
                () -> assertFalse(triggerService.contains("GeneratedKeyHolder")),
                () -> assertFalse(triggerService.contains("ai_ops_alert_trigger_rule")),
                () -> assertFalse(triggerService.contains("ai_ops_alert_trigger_event")),
                () -> assertFalse(triggerService.contains("CREATE TABLE")),
                () -> assertFalse(analysisRun.contains("OpsAlertTriggerService")));
    }

    @Test
    void obsoleteGenericRuleEventChainIsPhysicallyAbsent() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/alert/AlertTriggerManagementUseCase.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/alert/AlertTriggerPort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/alert/OpsAlertTriggerAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/alert/service/AlertRulePolicy.java"))));
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
