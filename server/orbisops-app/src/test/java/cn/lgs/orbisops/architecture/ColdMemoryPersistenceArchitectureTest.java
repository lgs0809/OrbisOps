package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColdMemoryPersistenceArchitectureTest {

    private static final String TRIGGER_STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsJdbcColdMemoryStore.java";
    private static final String TRIGGER_MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsColdMemoryMapper.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcColdMemoryRepository.java";
    private static final String SCHEMA_INITIALIZER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcConversationMemorySchemaInitializer.java";
    private static final String CHAT_SESSION_INITIALIZER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChatSessionSchemaInitializer.java";

    @Test
    void triggerStoreIsOnlyAnApplicationCompatibilityAdapter() throws IOException {
        String store = read(TRIGGER_STORE);
        String mapper = read(TRIGGER_MAPPER);
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertFalse(store.contains("JdbcTemplate")),
                () -> assertFalse(store.contains("ObjectProvider")),
                () -> assertFalse(store.contains("DataAccessException")),
                () -> assertFalse(store.contains("com.alibaba.fastjson")),
                () -> assertFalse(store.contains("CREATE TABLE")),
                () -> assertFalse(store.contains("INSERT INTO")),
                () -> assertFalse(store.contains("DELETE FROM")),
                () -> assertFalse(store.contains("ai_ops_chat_message")),
                () -> assertFalse(store.contains("ai_ops_memory_item")),
                () -> assertFalse(store.contains("queryItems(")),
                () -> assertFalse(store.contains("IColdMemoryRepository")),
                () -> assertFalse(store.contains("repository.available")),
                () -> assertFalse(store.contains("catch (RuntimeException")),
                () -> assertTrue(store.contains("ColdMemoryStoreApplicationService")),
                () -> assertTrue(store.contains("OpsColdMemoryMapper")),
                () -> assertTrue(store.contains("coldMemoryStore.appendMessage")),
                () -> assertTrue(store.contains("coldMemoryStore.saveItems")),
                () -> assertTrue(store.contains("coldMemoryStore.listItems")),
                () -> assertTrue(mapper.contains("ColdMemoryMessageSnapshot snapshot")),
                () -> assertTrue(mapper.contains("ColdMemoryItemSnapshot snapshot")),
                () -> assertTrue(repository.contains("implements IColdMemoryRepository")));
    }

    @Test
    void messageAndMemoryItemMutationSqlBelongsOnlyToRepository() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            for (String line : Files.readAllLines(source)) {
                String normalized = line.trim().toUpperCase(Locale.ROOT);
                if (containsMutation(normalized, "AI_OPS_CHAT_MESSAGE")
                        || containsMutation(normalized, "AI_OPS_MEMORY_ITEM")) {
                    owners.add(relative(source));
                }
            }
        }

        assertEquals(Set.of(REPOSITORY, "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/JdbcConversationMemoryRepository.java"), owners,
                "Cold Memory mutation SQL must stay behind cold/conversation repository ports");
    }

    @Test
    void conversationMemoryDdlHasOneInfrastructureOwner() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            String content = Files.readString(source);
            boolean memoryTable = content.contains("ai_ops_chat_message")
                    || content.contains("ai_ops_memory_item");
            boolean ddl = content.contains("CREATE TABLE")
                    || content.contains("ALTER TABLE");
            if (memoryTable && ddl) owners.add(relative(source));
        }

        assertEquals(Set.of(SCHEMA_INITIALIZER), owners,
                "Conversation Memory compatibility DDL must have one Infrastructure owner");
        String chatSessionInitializer = read(CHAT_SESSION_INITIALIZER);
        String repository = read(REPOSITORY);
        assertAll(
                () -> assertFalse(chatSessionInitializer.contains("ai_ops_chat_message")),
                () -> assertFalse(repository.contains("CREATE TABLE")),
                () -> assertFalse(repository.contains("ALTER TABLE")),
                () -> assertFalse(repository.contains("information_schema")));
    }

    @Test
    void coldMemoryDomainIsFrameworkNeutral() throws IOException {
        Path domainRoot = projectRoot().resolve("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/memory");
        try (Stream<Path> sources = Files.walk(domainRoot)) {
            List<String> violations = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readLines(path).stream()
                            .filter(line -> line.contains("org.springframework")
                                    || line.contains("cn.lgs.orbisops.trigger")
                                    || line.contains("cn.lgs.orbisops.infrastructure")
                                    || line.contains("com.alibaba.fastjson")
                                    || line.contains("com.fasterxml.jackson")
                                    || line.contains("JdbcTemplate"))
                            .map(line -> relative(path) + ": " + line.trim()))
                    .toList();
            assertEquals(List.of(), violations,
                    "Cold Memory Domain must remain independent from framework and outer layers");
        }
    }

    private boolean containsMutation(String line, String table) {
        return contains(line, "INSERT INTO", table)
                || contains(line, "INSERT IGNORE INTO", table)
                || contains(line, "UPDATE", table)
                || contains(line, "DELETE FROM", table);
    }

    private boolean contains(String line, String operation, String table) {
        String token = operation + " " + table;
        int index = line.indexOf(token);
        if (index < 0) return false;
        int end = index + token.length();
        return end == line.length()
                || Character.isWhitespace(line.charAt(end))
                || line.charAt(end) == '(';
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read source " + path, error);
        }
    }

    private List<Path> productionJavaSources() throws IOException {
        Path root = projectRoot();
        try (Stream<Path> modules = Files.list(root)) {
            List<Path> sourceRoots = modules
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("orbisops-"))
                    .map(path -> path.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .toList();
            try (Stream<Path> sources = sourceRoots.stream().flatMap(this::walkUnchecked)) {
                return sources.filter(path -> path.toString().endsWith(".java")).toList();
            }
        }
    }

    private Stream<Path> walkUnchecked(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot inspect production source tree: " + root, error);
        }
    }

    private String relative(Path source) {
        return projectRoot().relativize(source).toString().replace('\\', '/');
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
