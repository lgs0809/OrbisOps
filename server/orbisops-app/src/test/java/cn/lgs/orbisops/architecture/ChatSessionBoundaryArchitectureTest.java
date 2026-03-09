package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSessionBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void configurationMustOwnRepositoryFallbackAndStoreComposition() throws IOException {
        String settings = read(RUNTIME + "OpsChatSessionSettings.java");
        String configuration = read(RUNTIME + "OpsChatSessionConfiguration.java");
        String reporter = read(RUNTIME + "OpsChatSessionFallbackReporter.java");

        assertAll(
                () -> assertTrue(settings.contains("public record OpsChatSessionSettings(")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(settings.lines().count() < 15),
                () -> assertTrue(configuration.contains("Environment environment")),
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<IChatSessionRepository>")),
                () -> assertTrue(configuration.contains(
                        "new ChatSessionStoreApplicationService(")),
                () -> assertTrue(configuration.contains("new ChatSessionMemoryCatalog()")),
                () -> assertTrue(configuration.contains("settings::allowInMemoryFallback")),
                () -> assertTrue(configuration.contains("fallbackReporter::report")),
                () -> assertTrue(configuration.lines().count() < 50),
                () -> assertTrue(reporter.contains("AtomicBoolean")),
                () -> assertTrue(reporter.contains("compareAndSet(false, true)")),
                () -> assertTrue(reporter.lines().count() < 35));
    }

    @Test
    void binderMustOwnAgentResolutionVersionBindingAndPinnedHash() throws IOException {
        String binder = read(RUNTIME + "OpsChatSessionAgentBinder.java");

        assertAll(
                () -> assertTrue(binder.contains("OpsAgentDefinitionQueryGateway")),
                () -> assertTrue(binder.contains("ChatSessionAgentBindingPolicy")),
                () -> assertTrue(binder.contains("resolveForProject(")),
                () -> assertTrue(binder.contains("selectedVersion(requestedVersion)")),
                () -> assertTrue(binder.contains("verifyPinnedHash(")),
                () -> assertTrue(binder.contains("agentBindingMode")),
                () -> assertTrue(binder.contains("agentDefinitionHash")),
                () -> assertFalse(binder.contains("IChatSessionRepository")),
                () -> assertFalse(binder.contains("ObjectProvider")),
                () -> assertFalse(binder.contains("@Value")),
                () -> assertTrue(binder.lines().count() < 150));
    }

    @Test
    void participantCoordinatorMustOwnAclNormalizationAndReplacementCas() throws IOException {
        String coordinator = read(
                RUNTIME + "OpsChatSessionParticipantCoordinator.java");

        assertAll(
                () -> assertTrue(coordinator.contains("ChatSessionParticipantPolicy")),
                () -> assertTrue(coordinator.contains("activeParticipantRole(")),
                () -> assertTrue(coordinator.contains("participantPolicy.canRead(")),
                () -> assertTrue(coordinator.contains("participantPolicy.canWrite(")),
                () -> assertTrue(coordinator.contains("participantPolicy.normalize(")),
                () -> assertTrue(coordinator.contains("ChatSessionParticipantReplacement")),
                () -> assertTrue(coordinator.contains("SESSION_VERSION_CONFLICT")),
                () -> assertFalse(coordinator.contains("ObjectProvider")),
                () -> assertFalse(coordinator.contains("@Value")),
                () -> assertTrue(coordinator.lines().count() < 110));
    }

    @Test
    void factoryMustOwnSessionConstructionTitlesAndMessageSummaries() throws IOException {
        String factory = read(RUNTIME + "OpsChatSessionFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("UUID.randomUUID()")),
                () -> assertTrue(factory.contains("public OpsChatSession create(")),
                () -> assertTrue(factory.contains("public OpsChatSession restoreMissing(")),
                () -> assertTrue(factory.contains("public OpsChatSessionCreateRequest createRequest(")),
                () -> assertTrue(factory.contains("defaultTitle(")),
                () -> assertTrue(factory.contains("messageSummary(")),
                () -> assertTrue(factory.contains("新会话")),
                () -> assertFalse(factory.contains("ChatSessionStoreApplicationService")),
                () -> assertFalse(factory.contains("OpsAgentDefinitionQueryGateway")),
                () -> assertFalse(factory.contains("ObjectProvider")),
                () -> assertTrue(factory.lines().count() < 160));
    }

    @Test
    void serviceMustBeTypedLifecycleFacadeWithoutInfrastructureConstruction() throws IOException {
        String service = read(RUNTIME + "OpsChatSessionService.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "private final ChatSessionStoreApplicationService sessionStore;")),
                () -> assertTrue(service.contains(
                        "private final OpsChatSessionAgentBinder agentBinder;")),
                () -> assertTrue(service.contains(
                        "private final OpsChatSessionFactory sessionFactory;")),
                () -> assertTrue(service.contains(
                        "private final OpsChatSessionParticipantCoordinator participantCoordinator;")),
                () -> assertEquals(1, occurrences(
                        service, "public OpsChatSessionService(")),
                () -> assertFalse(service.contains("ObjectProvider")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("IChatSessionRepository")),
                () -> assertFalse(service.contains("ChatSessionMemoryCatalog")),
                () -> assertFalse(service.contains("new ChatSessionStoreApplicationService(")),
                () -> assertFalse(service.contains("OpsAgentDefinitionQueryGateway")),
                () -> assertFalse(service.contains("ChatSessionAgentBindingPolicy")),
                () -> assertFalse(service.contains("ChatSessionParticipantPolicy")),
                () -> assertFalse(service.contains("UUID.randomUUID()")),
                () -> assertFalse(service.contains("defaultTitle(")),
                () -> assertFalse(service.contains("logFallback(")),
                () -> assertTrue(service.lines().count() < 280));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
