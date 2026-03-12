package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphEventArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/graph/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/runtime/graph/GraphEventApplicationService.java";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcGraphEventRepository.java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String LEGACY_MODEL = TRIGGER + "ops/runtime/OpsGraphEvent.java";

    @Test
    void domainOwnsEventDraftAndRepositoryContractWithoutFrameworkLeakage() throws IOException {
        String event = read(DOMAIN + "model/GraphEvent.java");
        String draft = read(DOMAIN + "model/GraphEventDraft.java");
        String node = read(DOMAIN + "model/GraphEventNodeDescriptor.java");
        String repository = read(DOMAIN + "adapter/repository/IGraphEventRepository.java");
        String domain = event + draft + node + repository;

        assertAll(
                () -> assertTrue(event.contains("record GraphEvent")),
                () -> assertTrue(draft.contains("record GraphEventDraft")),
                () -> assertTrue(draft.contains("GraphEvent materialize")),
                () -> assertTrue(repository.contains("Optional<GraphEvent> append")),
                () -> assertTrue(repository.contains("List<GraphEvent> list")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("CREATE TABLE")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")));
    }

    @Test
    void applicationPublishesOnlyAfterRepositoryAppendAndOwnsRuntimeFanout() throws IOException {
        String application = read(APPLICATION);

        assertAll(
                () -> assertTrue(application.contains("IGraphEventRepository")),
                () -> assertTrue(application.contains("repository.append(draft)")),
                () -> assertTrue(application.indexOf("repository.append(draft)")
                        < application.indexOf("appendMemory(event)")),
                () -> assertTrue(application.indexOf("appendMemory(event)")
                        < application.indexOf("notifySubscribers(event)")),
                () -> assertTrue(application.contains("CopyOnWriteArrayList<Consumer<GraphEvent>>")),
                () -> assertTrue(application.contains("ConcurrentHashMap")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("CREATE TABLE")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")));
    }

    @Test
    void infrastructureOwnsAtomicSequenceDdlSqlAndJsonCodec() throws IOException {
        String infrastructure = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IGraphEventRepository")),
                () -> assertTrue(infrastructure.contains("@Transactional(transactionManager = \"mysqlTransactionManager\")")),
                () -> assertTrue(infrastructure.contains("INSERT INTO ai_ops_graph_event_sequence")),
                () -> assertTrue(infrastructure.contains("LAST_INSERT_ID(next_sequence + 1)")),
                () -> assertTrue(infrastructure.contains("INSERT INTO ai_ops_agent_node_trace")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_graph_event_sequence")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_node_trace")),
                () -> assertTrue(infrastructure.contains("event_type VARCHAR(128) NOT NULL")),
                () -> assertTrue(infrastructure.contains("MODIFY COLUMN event_type VARCHAR(128) NOT NULL")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertTrue(infrastructure.contains("Never convert an in-transaction write failure")));
    }

    @Test
    void triggerUsesApplicationServiceAndLegacyTypesCannotReturnToProduction() throws IOException {
        String production = readJavaTree(TRIGGER);

        assertAll(
                () -> assertTrue(production.contains("GraphEventApplicationService")),
                () -> assertTrue(production.contains("cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent")),
                () -> assertFalse(production.contains("OpsGraphEventService")),
                () -> assertFalse(production.contains("ai_ops_graph_event_sequence")),
                () -> assertFalse(production.contains("ai_ops_agent_node_trace")),
                () -> assertFalse(production.contains("LAST_INSERT_ID(next_sequence + 1)")),
                () -> assertFalse(Files.exists(projectRoot().resolve(
                        TRIGGER + "ops/runtime/OpsGraphEventService.java"))),
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY_MODEL))));
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
