package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertAggregationArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/alert/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/alert/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String TRIGGER_ALERT = TRIGGER + "application/alert/";

    @Test
    void domainOwnsAggregationStateEventsPolicyAndRepositoryContract() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/AlertAggregationPolicy.java");

        assertAll(
                () -> assertTrue(domain.contains("enum AlertAggregateState")),
                () -> assertTrue(domain.contains("enum AlertAggregateEventType")),
                () -> assertTrue(domain.contains("record AlertAggregationSignal")),
                () -> assertTrue(domain.contains("record AlertAggregateSnapshot")),
                () -> assertTrue(domain.contains("record AlertAggregationPlan")),
                () -> assertTrue(domain.contains("record AlertAggregationDecision")),
                () -> assertTrue(domain.contains("record AlertSummaryClaim")),
                () -> assertTrue(domain.contains("interface IAlertAggregationRepository")),
                () -> assertTrue(policy.contains("AlertAggregateEventType.FIRST")),
                () -> assertTrue(policy.contains("AlertAggregateEventType.RECURRENCE")),
                () -> assertTrue(policy.contains("AlertAggregateEventType.ESCALATION")),
                () -> assertTrue(policy.contains("AlertAggregateEventType.RECOVERY")),
                () -> assertTrue(policy.contains("AlertAggregateEventType.DUPLICATE")),
                () -> assertTrue(policy.contains("AlertAggregateEventType.IGNORED_RECOVERY")),
                () -> assertTrue(policy.contains("MessageDigest.getInstance(\"MD5\")")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("ai_ops_alert_aggregate")),
                () -> assertFalse(domain.contains("FOR UPDATE")));
    }

    @Test
    void applicationOwnsTransactionalRecordAndSummaryClaimProcess() throws IOException {
        String application = readJavaTree(APPLICATION);
        String service = read(APPLICATION + "AlertAggregationApplicationService.java");

        assertAll(
                () -> assertTrue(service.contains("transactions.required")),
                () -> assertTrue(service.contains("aggregates.insertIfAbsent")),
                () -> assertTrue(service.contains("aggregates.lock")),
                () -> assertTrue(service.contains("aggregates.apply")),
                () -> assertTrue(service.contains("aggregates.claimSummary")),
                () -> assertTrue(service.contains("aggregates.acknowledgeSummary")),
                () -> assertTrue(service.contains("Supplier<String> claimIdSupplier")),
                () -> assertFalse(application.contains("AlertAggregationIdentityPort")),
                () -> assertTrue(application.contains("interface AlertAggregationTransactionPort")),
                () -> assertFalse(application.contains("OpsAlertAggregationService")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("ai_ops_alert_aggregate")),
                () -> assertFalse(application.contains("SELECT ")));
    }

    @Test
    void infrastructureOwnsSqlJsonSchemaRowLocksAndCas() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcAlertAggregationRepository.java");
        String initializer = read(INFRASTRUCTURE + "JdbcAlertAggregationSchemaInitializer.java");
        String transaction = read(INFRASTRUCTURE + "SpringAlertAggregationTransactionAdapter.java");

        assertAll(
                () -> assertTrue(repository.contains("implements IAlertAggregationRepository")),
                () -> assertTrue(repository.contains("INSERT IGNORE INTO ai_ops_alert_aggregate")),
                () -> assertTrue(repository.contains("FOR UPDATE SKIP LOCKED")),
                () -> assertTrue(repository.contains("WHERE aggregate_key=? AND version=?")),
                () -> assertTrue(repository.contains("pending_summary_count=pending_summary_count+1")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(initializer.contains("CREATE TABLE IF NOT EXISTS ai_ops_alert_aggregate")),
                () -> assertTrue(initializer.contains("information_schema.columns")),
                () -> assertTrue(transaction.contains("TransactionTemplate")),
                () -> assertTrue(transaction.contains("mysqlTransactionManager")),
                () -> assertFalse(repository.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void triggerUsesTypedApplicationServiceAndNoLongerOwnsAggregateSql() throws IOException {
        String triggerAlert = readJavaTree(TRIGGER_ALERT);
        String triggerService = read(TRIGGER + "ops/OpsAlertTriggerService.java");
        String summaryCoordinator = read(TRIGGER + "ops/OpsAlertSummaryCoordinator.java");

        assertAll(
                () -> assertTrue(triggerAlert.contains("UUID.randomUUID()")),
                () -> assertFalse(triggerAlert.contains("OpsAlertAggregationIdentityAdapter")),
                () -> assertTrue(triggerAlert.contains("AlertAggregationApplicationService")),
                () -> assertTrue(triggerService.contains("AlertAggregationApplicationService")),
                () -> assertTrue(triggerService.contains("AlertAggregationDecision")),
                () -> assertTrue(triggerService.contains("aggregation.record(")),
                () -> assertTrue(summaryCoordinator.contains("AlertSummaryClaim")),
                () -> assertTrue(summaryCoordinator.contains("aggregation.claimDueSummaries(")),
                () -> assertTrue(summaryCoordinator.contains("aggregation.acknowledgeSummary(")),
                () -> assertTrue(summaryCoordinator.contains("aggregation.releaseSummaryClaim(")),
                () -> assertFalse(triggerService.contains("OpsAlertAggregationService")),
                () -> assertFalse(triggerService.contains("CREATE TABLE IF NOT EXISTS ai_ops_alert_aggregate")),
                () -> assertFalse(triggerService.contains("FROM ai_ops_alert_aggregate")),
                () -> assertFalse(triggerService.contains("UPDATE ai_ops_alert_aggregate")),
                () -> assertFalse(triggerService.contains("summary_claim_token")));
    }

    @Test
    void legacyTriggerAggregationServiceIsPhysicallyAbsent() {
        assertFalse(exists(TRIGGER + "ops/OpsAlertAggregationService.java"));
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

    private boolean exists(String relativePath) {
        return Files.exists(projectRoot().resolve(relativePath));
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
