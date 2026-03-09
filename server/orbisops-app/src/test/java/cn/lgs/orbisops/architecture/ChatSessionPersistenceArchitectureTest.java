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

class ChatSessionPersistenceArchitectureTest {

    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsChatSessionService.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChatSessionRepository.java";
    private static final String SCHEMA_INITIALIZER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChatSessionSchemaInitializer.java";
    private static final String BINDING_MODE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/chatsession/model/ChatSessionAgentBindingMode.java";
    private static final String BINDING_POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/chatsession/service/ChatSessionAgentBindingPolicy.java";
    private static final String PARTICIPANT_POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/chatsession/service/ChatSessionParticipantPolicy.java";
    private static final String SNAPSHOT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/chatsession/model/ChatSessionSnapshot.java";
    private static final String MEMORY_CATALOG = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/chatsession/ChatSessionMemoryCatalog.java";
    private static final String STORE_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/chatsession/ChatSessionStoreApplicationService.java";
    private static final String TRIGGER_MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/chatsession/OpsChatSessionMapper.java";
    private static final String AGENT_BINDER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsChatSessionAgentBinder.java";
    private static final String PARTICIPANT_COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsChatSessionParticipantCoordinator.java";

    @Test
    void triggerServiceDelegatesPersistenceToTypedRepository() throws IOException {
        String service = read(SERVICE);
        String storeService = read(STORE_SERVICE);
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("DataAccessException")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("CREATE TABLE")),
                () -> assertFalse(service.contains("ALTER TABLE")),
                () -> assertFalse(service.contains("information_schema")),
                () -> assertFalse(service.contains("ai_ops_chat_session")),
                () -> assertFalse(service.contains("sessionRepository")),
                () -> assertFalse(service.contains("private IChatSessionRepository repository(")),
                () -> assertFalse(service.contains("assertFallbackAllowed(")),
                () -> assertTrue(service.contains("ChatSessionStoreApplicationService")),
                () -> assertTrue(service.contains("sessionStore.insert(")),
                () -> assertTrue(service.contains("sessionStore.search(")),
                () -> assertTrue(service.contains("participantCoordinator.replace(")),
                () -> assertTrue(storeService.contains("repository.insert(")),
                () -> assertTrue(storeService.contains("repository.search(")),
                () -> assertTrue(storeService.contains("repository.replaceParticipants(")),
                () -> assertTrue(storeService.contains("fallbackCatalog")),
                () -> assertTrue(repository.contains("implements IChatSessionRepository")));
    }

    @Test
    void favoriteSemanticMustBelongToDomainSnapshot() throws IOException {
        String snapshot = read(SNAPSHOT);
        String catalog = read(MEMORY_CATALOG);

        assertAll(
                () -> assertTrue(snapshot.contains("public boolean favorite()")),
                () -> assertTrue(catalog.contains("session.favorite()")),
                () -> assertFalse(catalog.contains("metadata().get(\"favorite\")")));
    }

    @Test
    void sessionAndParticipantMutationSqlBelongsOnlyToRepository() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            for (String line : Files.readAllLines(source)) {
                String normalized = line.trim().toUpperCase(Locale.ROOT);
                if (containsMutation(normalized, "AI_OPS_CHAT_SESSION")
                        || containsMutation(normalized, "AI_OPS_CHAT_SESSION_PARTICIPANT")) {
                    owners.add(relative(source));
                }
            }
        }

        assertEquals(Set.of(REPOSITORY), owners,
                "Chat Session and participant mutation SQL must stay behind IChatSessionRepository");
    }

    @Test
    void sessionAndParticipantDdlHasOneInfrastructureOwner() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            String content = Files.readString(source);
            boolean sessionTable = content.contains("ai_ops_chat_session")
                    || content.contains("ai_ops_chat_session_participant");
            boolean ddl = content.contains("CREATE TABLE")
                    || content.contains("ALTER TABLE")
                    || content.contains("information_schema");
            if (sessionTable && ddl) {
                owners.add(relative(source));
            }
        }

        assertEquals(Set.of(SCHEMA_INITIALIZER), owners,
                "Chat Session compatibility DDL must have one Infrastructure owner");
        String repository = read(REPOSITORY);
        assertAll(
                () -> assertFalse(repository.contains("CREATE TABLE")),
                () -> assertFalse(repository.contains("ALTER TABLE")),
                () -> assertFalse(repository.contains("information_schema")));
    }

    @Test
    void bindingAndParticipantRulesBelongToDomainPolicies() throws IOException {
        String service = read(SERVICE);
        String bindingMode = read(BINDING_MODE);
        String bindingPolicy = read(BINDING_POLICY);
        String participantPolicy = read(PARTICIPANT_POLICY);
        String binder = read(AGENT_BINDER);
        String participantCoordinator = read(PARTICIPANT_COORDINATOR);

        assertAll(
                () -> assertFalse(service.contains("private String agentBindingMode(")),
                () -> assertFalse(service.contains("participant role 只允许 OBSERVER/EDITOR")),
                () -> assertFalse(service.contains("CHAT_SESSION_AGENT_DEFINITION_HASH_MISMATCH")),
                () -> assertFalse(service.contains("ChatSessionAgentBindingMode.resolve")),
                () -> assertFalse(service.contains("participantPolicy.")),
                () -> assertTrue(binder.contains("ChatSessionAgentBindingMode.resolve")),
                () -> assertTrue(binder.contains("bindingPolicy.verifyPinnedHash")),
                () -> assertTrue(participantCoordinator.contains("participantPolicy.normalize")),
                () -> assertTrue(participantCoordinator.contains("participantPolicy.canRead")),
                () -> assertTrue(participantCoordinator.contains("participantPolicy.canWrite")),
                () -> assertTrue(bindingMode.contains("PINNED_VERSION 会话必须选择 Agent 版本")),
                () -> assertTrue(bindingPolicy.contains("CHAT_SESSION_AGENT_DEFINITION_HASH_MISMATCH")),
                () -> assertTrue(participantPolicy.contains("ChatSessionParticipantRole.requireManaged")));
    }

    @Test
    void fallbackStateAndTriggerMappingHaveDedicatedOwners() throws IOException {
        String service = read(SERVICE);
        String catalog = read(MEMORY_CATALOG);
        String mapper = read(TRIGGER_MAPPER);

        assertAll(
                () -> assertFalse(service.contains("ConcurrentHashMap")),
                () -> assertFalse(service.contains("fallbackSessions")),
                () -> assertFalse(service.contains("fallbackParticipants")),
                () -> assertFalse(service.contains("private ChatSessionSnapshot snapshot(")),
                () -> assertFalse(service.contains("private OpsChatSession view(ChatSessionSnapshot")),
                () -> assertFalse(service.contains("private OpsChatMessageView view(ChatMessageSnapshot")),
                () -> assertFalse(service.contains("updateFallback(")),
                () -> assertFalse(service.contains("replaceFallbackParticipants(")),
                () -> assertFalse(service.contains("matchesKeyword(")),
                () -> assertFalse(service.contains("ChatSessionMemoryCatalog")),
                () -> assertTrue(service.contains("OpsChatSessionMapper")),
                () -> assertTrue(catalog.contains("Map<String, ChatSessionSnapshot> sessions")),
                () -> assertTrue(catalog.contains("replaceParticipants(")),
                () -> assertTrue(catalog.contains("stateVersion() != replacement.expectedSessionVersion()")),
                () -> assertTrue(mapper.contains("ChatSessionSnapshot snapshot(OpsChatSession")),
                () -> assertTrue(mapper.contains("OpsChatSession view(ChatSessionSnapshot")),
                () -> assertTrue(mapper.contains("OpsChatMessageView view(ChatMessageSnapshot")));
    }

    @Test
    void chatSessionApplicationIsFrameworkAndOuterLayerNeutral() throws IOException {
        Path applicationRoot = projectRoot().resolve("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/chatsession");
        try (Stream<Path> sources = Files.walk(applicationRoot)) {
            List<String> violations = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readLines(path).stream()
                            .filter(line -> line.contains("org.springframework")
                                    || line.contains("cn.lgs.orbisops.trigger")
                                    || line.contains("cn.lgs.orbisops.infrastructure")
                                    || line.contains("com.alibaba.fastjson")
                                    || line.contains("JdbcTemplate"))
                            .map(line -> relative(path) + ": " + line.trim()))
                    .toList();
            assertEquals(List.of(), violations,
                    "Chat Session Application must remain independent from framework and outer layers");
        }
    }

    @Test
    void chatSessionDomainIsFrameworkNeutral() throws IOException {
        Path domainRoot = projectRoot().resolve("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/chatsession");
        try (Stream<Path> sources = Files.walk(domainRoot)) {
            List<String> violations = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readLines(path).stream()
                            .filter(line -> line.contains("org.springframework")
                                    || line.contains("com.alibaba.fastjson")
                                    || line.contains("com.fasterxml.jackson")
                                    || line.contains("JdbcTemplate"))
                            .map(line -> relative(path) + ": " + line.trim()))
                    .toList();
            assertEquals(List.of(), violations,
                    "Chat Session Domain must remain independent from framework and persistence libraries");
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
