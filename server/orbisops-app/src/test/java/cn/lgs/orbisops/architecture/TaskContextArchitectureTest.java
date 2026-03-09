package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContextArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/taskcontext/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/runtime/taskcontext/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcTaskContextRepository.java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedTaskContextStateContentPolicyAndRepositoryContract() throws IOException {
        String state = read(DOMAIN + "model/TaskContextState.java");
        String content = read(DOMAIN + "model/TaskContextContent.java");
        String event = read(DOMAIN + "model/TaskContextEventFact.java");
        String snapshot = read(DOMAIN + "model/TaskContextSnapshot.java");
        String policy = read(DOMAIN + "service/TaskContextPolicy.java");
        String repository = read(DOMAIN + "adapter/repository/ITaskContextRepository.java");
        String domain = state + content + event + snapshot + policy + repository;

        assertAll(
                () -> assertTrue(state.contains("enum TaskContextState")),
                () -> assertTrue(content.contains("record TaskContextContent")),
                () -> assertTrue(event.contains("record TaskContextEventFact")),
                () -> assertTrue(snapshot.contains("record TaskContextSnapshot")),
                () -> assertTrue(policy.contains("TaskContextSnapshot start(")),
                () -> assertTrue(policy.contains("TaskContextSnapshot progress(")),
                () -> assertTrue(policy.contains("TaskContextSnapshot finish(")),
                () -> assertTrue(repository.contains("Optional<TaskContextSnapshot> trySave")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("CREATE TABLE")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")),
                () -> assertFalse(domain.contains("OpsRuntimeEvent")),
                () -> assertFalse(domain.contains("OpsAgentChatRequest")));
    }

    @Test
    void applicationOwnsBestEffortCommandsStrictQueriesCasRetryAndAuditOrdering() throws IOException {
        String command = read(APPLICATION + "TaskContextCommandApplicationService.java");
        String query = read(APPLICATION + "TaskContextQueryApplicationService.java");
        String application = readJavaTree(APPLICATION);

        assertAll(
                () -> assertTrue(command.contains("ITaskContextRepository")),
                () -> assertTrue(command.contains("TaskContextPolicy")),
                () -> assertTrue(command.contains("for (int attempt = 0; attempt < 2; attempt++)")),
                () -> assertTrue(command.contains("repository.find(runId)")),
                () -> assertTrue(command.contains("repository.trySave(candidate, expectedVersion)")),
                () -> assertTrue(command.indexOf("repository.trySave(candidate, expectedVersion)")
                        < command.indexOf("recordAudit(before, after)")),
                () -> assertTrue(command.contains("catch (RuntimeException unavailable)")),
                () -> assertTrue(query.contains("Task Context 不存在：")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("CREATE TABLE")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("OpsRuntimeEvent")),
                () -> assertFalse(application.contains("OpsAgentChatRequest")));
    }

    @Test
    void infrastructureExclusivelyOwnsTaskContextDdlJsonMappingAndVersionCas() throws IOException {
        String infrastructure = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements ITaskContextRepository")),
                () -> assertTrue(infrastructure.contains("@Transactional(transactionManager = \"mysqlTransactionManager\")")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_task_context")),
                () -> assertTrue(infrastructure.contains("INSERT INTO ai_ops_task_context")),
                () -> assertTrue(infrastructure.contains("WHERE run_id = ? AND version = ?")),
                () -> assertTrue(infrastructure.contains("version = version + 1")),
                () -> assertTrue(infrastructure.contains("TaskContextVersionConflictException")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertFalse(infrastructure.contains("ON DUPLICATE KEY UPDATE")));
    }

    @Test
    void triggerOnlyMapsProtocolsAndLegacyServiceIsDeleted() throws IOException {
        String adapter = read(TRIGGER + "application/runtime/OpsTaskContextAdapter.java");
        String mapper = read(TRIGGER + "application/runtime/OpsTaskContextMapper.java");
        Path legacyService = projectRoot().resolve(
                TRIGGER + "ops/runtime/OpsTaskContextService.java");

        assertAll(
                () -> assertTrue(adapter.contains("TaskContextCommandApplicationService")),
                () -> assertTrue(adapter.contains("TaskContextQueryApplicationService")),
                () -> assertTrue(mapper.contains("TaskContextEventFact")),
                () -> assertTrue(mapper.contains("OpsRuntimeEvent")),
                () -> assertFalse(Files.exists(legacyService)),
                () -> assertFalse(adapter.contains("ai_ops_task_context")),
                () -> assertFalse(adapter.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertFalse(mapper.contains("ai_ops_task_context")));
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
