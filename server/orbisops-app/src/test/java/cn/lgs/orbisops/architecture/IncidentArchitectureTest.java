package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/incident/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/incident/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String TRIGGER_INCIDENT = TRIGGER + "application/incident/";

    @Test
    void domainOwnsIncidentTypedModelsFactProjectionPolicyAndRepository() throws IOException {
        String domain = readJavaTree(DOMAIN);

        assertAll(
                () -> assertTrue(domain.contains("class IncidentStatusProjectionPolicy")),
                () -> assertFalse(domain.contains("class IncidentAggregate")),
                () -> assertTrue(domain.contains("enum IncidentStatus")),
                () -> assertTrue(domain.contains("record IncidentSnapshot")),
                () -> assertTrue(domain.contains("record IncidentTimelineEntry")),
                () -> assertTrue(domain.contains("record IncidentRunSnapshot")),
                () -> assertTrue(domain.contains("record IncidentAlertSignal")),
                () -> assertTrue(domain.contains("class IncidentPolicy")),
                () -> assertTrue(domain.contains("interface IIncidentRepository")),
                () -> assertTrue(domain.contains("UUID.nameUUIDFromBytes")),
                () -> assertTrue(domain.contains("signal.projectId() + \":\" + signal.dedupKey()")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("SELECT ")),
                () -> assertFalse(domain.contains("ai_ops_incident")));
    }

    @Test
    void applicationOwnsTypedQueryCommandAlertAndUnitOfWork() throws IOException {
        String application = readJavaTree(APPLICATION);

        assertAll(
                () -> assertTrue(application.contains("class IncidentQueryApplicationService")),
                () -> assertTrue(application.contains("class IncidentCommandApplicationService")),
                () -> assertTrue(application.contains("class IncidentAlertApplicationService")),
                () -> assertTrue(application.contains("record CreateIncidentCommand")),
                () -> assertTrue(application.contains("record AppendIncidentTimelineCommand")),
                () -> assertTrue(application.contains("record IncidentAuditEvent")),
                () -> assertTrue(application.contains("Supplier<String> incidentIdSupplier")),
                () -> assertFalse(application.contains("IncidentIdentityPort")),
                () -> assertTrue(application.contains("interface IncidentTransactionPort")),
                () -> assertTrue(application.contains("transactions.required")),
                () -> assertTrue(application.contains("record IncidentDetailProjection")),
                () -> assertTrue(application.contains("IncidentStatusProjectionPolicy")),
                () -> assertFalse(application.contains("IncidentAggregate.rehydrate")),
                () -> assertFalse(application.contains("IncidentManagementUseCase")),
                () -> assertFalse(application.contains("interface IncidentPort")),
                () -> assertFalse(application.contains("OpsIncident")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("SELECT ")),
                () -> assertFalse(application.contains("ai_ops_incident")));
    }

    @Test
    void infrastructureOwnsSchemaSqlJsonAndAtomicAlertUpsert() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcIncidentRepository.java");
        String initializer = read(INFRASTRUCTURE + "JdbcIncidentSchemaInitializer.java");
        String transaction = read(INFRASTRUCTURE + "SpringIncidentTransactionAdapter.java");

        assertAll(
                () -> assertTrue(repository.contains("implements IIncidentRepository")),
                () -> assertTrue(repository.contains("JdbcTemplate")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(repository.contains("occurrence_count = occurrence_count + 1")),
                () -> assertTrue(repository.contains("project_id=? AND status=?")),
                () -> assertTrue(repository.contains("ai_ops_agent_run")),
                () -> assertTrue(initializer.contains("CREATE TABLE IF NOT EXISTS ai_ops_incident")),
                () -> assertTrue(initializer.contains("CREATE TABLE IF NOT EXISTS ai_ops_incident_timeline")),
                () -> assertTrue(initializer.contains("CREATE TABLE IF NOT EXISTS ai_ops_incident_run")),
                () -> assertTrue(transaction.contains("TransactionTemplate")),
                () -> assertTrue(transaction.contains("mysqlTransactionManager")),
                () -> assertFalse(repository.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void triggerOnlyMapsWiresAuditsAndAdaptsHttpAndAlertEntryPoints() throws IOException {
        String triggerIncident = readJavaTree(TRIGGER_INCIDENT);
        String controller = read(TRIGGER + "http/admin/OpsIncidentAdminController.java");
        String alert = read(TRIGGER + "ops/OpsAlertTriggerService.java");
        String alertIncidentAdapter = read(TRIGGER
                + "application/alert/OpsAlertEventIncidentAdapter.java");

        assertAll(
                () -> assertTrue(triggerIncident.contains("class OpsIncidentMapper")),
                () -> assertTrue(triggerIncident.contains("UUID.randomUUID()")),
                () -> assertFalse(triggerIncident.contains("OpsIncidentIdentityAdapter")),
                () -> assertTrue(triggerIncident.contains("class OpsIncidentAuditAdapter")),
                () -> assertTrue(triggerIncident.contains("class OpsIncidentApplicationConfiguration")),
                () -> assertTrue(controller.contains("IncidentQueryApplicationService")),
                () -> assertTrue(controller.contains("IncidentCommandApplicationService")),
                () -> assertTrue(controller.contains("OpsIncidentMapper")),
                () -> assertFalse(alert.contains("IncidentAlertApplicationService")),
                () -> assertTrue(alertIncidentAdapter.contains("implements AlertEventIncidentPort")),
                () -> assertTrue(alertIncidentAdapter.contains("IncidentAlertApplicationService")),
                () -> assertTrue(alertIncidentAdapter.contains(
                        "incidents.ingest(incidentMapper.alertSignal(eventMapper.view(event)))")),
                () -> assertFalse(triggerIncident.contains("JdbcTemplate")),
                () -> assertFalse(triggerIncident.contains("@PostConstruct")),
                () -> assertFalse(triggerIncident.contains("SELECT ")),
                () -> assertFalse(triggerIncident.contains("ai_ops_incident")),
                () -> assertFalse(controller.contains("IncidentManagementUseCase")),
                () -> assertFalse(alert.contains("OpsIncidentService")));
    }

    @Test
    void legacyIncidentFacadeAndJdbcServiceArePhysicallyAbsent() {
        assertAll(
                () -> assertFalse(exists(APPLICATION + "IncidentManagementUseCase.java")),
                () -> assertFalse(exists(APPLICATION + "IncidentPort.java")),
                () -> assertFalse(exists(TRIGGER_INCIDENT + "OpsIncidentAdapter.java")),
                () -> assertFalse(exists(TRIGGER + "ops/OpsIncidentService.java")));
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
