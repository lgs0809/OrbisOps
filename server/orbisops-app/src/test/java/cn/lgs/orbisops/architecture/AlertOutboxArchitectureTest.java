package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertOutboxArchitectureTest {

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
    void domainOwnsTypedOutboxStateQuotaRetryAndRepositoryContract() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/AlertOutboxPolicy.java");

        assertAll(
                () -> assertTrue(domain.contains("enum AlertOutboxStatus")),
                () -> assertTrue(domain.contains("record AlertRunRequest")),
                () -> assertTrue(domain.contains("record AlertOutboxDraft")),
                () -> assertTrue(domain.contains("record AlertOutboxEntry")),
                () -> assertTrue(domain.contains("record AlertOutboxFailurePlan")),
                () -> assertTrue(domain.contains("interface IAlertOutboxRepository")),
                () -> assertTrue(policy.contains("PREEMPT_LOWER_PRIORITY")),
                () -> assertTrue(policy.contains("exponential * 30L")),
                () -> assertTrue(policy.contains("Math.min(3600L")),
                () -> assertFalse(domain.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("ai_ops_alert_trigger_outbox")),
                () -> assertFalse(domain.contains("POW(")));
    }

    @Test
    void applicationOwnsEnqueueDispatchRecoveryAndFailureProcess() throws IOException {
        String application = readJavaTree(APPLICATION);
        String service = read(APPLICATION + "AlertOutboxApplicationService.java");

        assertAll(
                () -> assertTrue(service.contains("transactions.required")),
                () -> assertTrue(service.contains("outbox.preemptOneLowerPriority")),
                () -> assertTrue(service.contains("dispatchIfCapacity")),
                () -> assertTrue(service.contains("outbox.recoverStale")),
                () -> assertTrue(service.contains("outbox.deadLetterExhausted")),
                () -> assertTrue(service.contains("outbox.claim")),
                () -> assertTrue(service.contains("outbox.markSucceeded")),
                () -> assertTrue(service.contains("outbox.markFailed")),
                () -> assertTrue(service.contains("ALERT_OUTBOX_SUCCESS_CONFLICT")),
                () -> assertTrue(application.contains("interface AlertOutboxSubmissionPort")),
                () -> assertTrue(application.contains("interface AlertOutboxProjectCapacityPort")),
                () -> assertFalse(application.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("ai_ops_alert_trigger_outbox")),
                () -> assertFalse(application.contains("POW(")));
    }

    @Test
    void infrastructureOwnsJsonSqlSchemaLeaseCasAndRetryPersistence() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcAlertOutboxRepository.java");
        String initializer = read(INFRASTRUCTURE + "JdbcAlertOutboxSchemaInitializer.java");
        String transaction = read(INFRASTRUCTURE + "SpringAlertOutboxTransactionAdapter.java");

        assertAll(
                () -> assertTrue(repository.contains("implements IAlertOutboxRepository")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_alert_trigger_outbox")),
                () -> assertTrue(repository.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(repository.contains("ORDER BY priority DESC, id ASC")),
                () -> assertTrue(repository.contains("locked_token=? AND status='RUNNING'")),
                () -> assertTrue(repository.contains("INTERVAL ? SECOND")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(initializer.contains("CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_outbox")),
                () -> assertTrue(initializer.contains("information_schema.columns")),
                () -> assertTrue(transaction.contains("TransactionTemplate")),
                () -> assertTrue(transaction.contains("mysqlTransactionManager")),
                () -> assertFalse(repository.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(repository.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(repository.contains("POW(")));
    }

    @Test
    void triggerUsesTypedOutboxApplicationAndNoLongerOwnsOutboxOrDedupSql() throws IOException {
        String triggerAlert = readJavaTree(TRIGGER_ALERT);
        String triggerService = read(TRIGGER + "ops/OpsAlertTriggerService.java");
        String submissions = read(TRIGGER + "ops/OpsAlertRunSubmissionCoordinator.java");

        assertAll(
                () -> assertTrue(triggerAlert.contains("class OpsAlertOutboxMapper")),
                () -> assertTrue(triggerAlert.contains("class OpsAlertTriggerExecutionAdapter")),
                () -> assertTrue(triggerAlert.contains("class OpsAlertOutboxProjectCapacityAdapter")),
                () -> assertTrue(triggerAlert.contains("AlertOutboxApplicationService")),
                () -> assertTrue(triggerService.contains("OpsAlertRunSubmissionCoordinator submissions")),
                () -> assertTrue(submissions.contains("alertOutbox.enqueue(")),
                () -> assertTrue(submissions.contains("alertOutbox.dispatchIfCapacity(")),
                () -> assertTrue(submissions.contains("alertOutbox.processPending(")),
                () -> assertTrue(submissions.contains("mapper.batchResult(")),
                () -> assertFalse(triggerService.contains("ai_ops_alert_trigger_outbox")),
                () -> assertFalse(triggerService.contains("ai_ops_alert_trigger_dedup")),
                () -> assertFalse(triggerService.contains("acquireDedup")),
                () -> assertFalse(triggerService.contains("markDedupRun")),
                () -> assertFalse(triggerService.contains("releaseDedup")),
                () -> assertFalse(triggerService.contains("OutboxRecord")),
                () -> assertFalse(triggerService.contains("POW(")),
                () -> assertFalse(triggerService.contains("requestJson()")));
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
