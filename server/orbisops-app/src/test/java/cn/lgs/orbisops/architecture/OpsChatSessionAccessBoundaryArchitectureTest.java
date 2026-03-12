package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChatSessionAccessBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/chatsession/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/chatsession/";
    private static final String CHAT_SERVICE =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsChatApplicationService.java";

    @Test
    void sessionAccessMustRemainInApplicationAndChatServiceMustDelegate() throws IOException {
        String useCase = read(APPLICATION + "ChatSessionAccessUseCase.java");
        String facts = read(APPLICATION + "ChatSessionAccessFacts.java");
        String port = read(APPLICATION + "ChatSessionAccessPort.java");
        String adapter = read(TRIGGER + "OpsChatSessionAccessAdapter.java");
        String facade = read(TRIGGER + "OpsChatSessionApplicationFacade.java");
        String requestPreparation = read(
                "orbisops-trigger/src/main/java/"
                        + "cn/lgs/orbisops/trigger/application/chat/"
                        + "OpsChatRequestPreparationFacade.java");
        String chat = read(CHAT_SERVICE);

        assertAll(
                () -> assertTrue(useCase.contains("public final class ChatSessionAccessUseCase")),
                () -> assertTrue(useCase.contains("SESSION_READ_FORBIDDEN")),
                () -> assertTrue(useCase.contains("SESSION_WRITE_FORBIDDEN")),
                () -> assertTrue(useCase.contains("SESSION_OWNER_REQUIRED")),
                () -> assertTrue(useCase.contains("if (!facts.exists()) return;")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(facts.contains("record ChatSessionAccessFacts")),
                () -> assertTrue(port.contains("interface ChatSessionAccessPort")),
                () -> assertTrue(adapter.contains("implements ChatSessionAccessPort")),
                () -> assertTrue(facade.contains("new ChatSessionAccessUseCase")),
                () -> assertTrue(facade.contains("applyProjectDefaults")),
                () -> assertTrue(requestPreparation.contains("sessions.assertWrite")),
                () -> assertTrue(chat.contains("requestPreparationFacade.prepare")),
                () -> assertFalse(chat.contains("chatSessionFacade.assertWrite")),
                () -> assertTrue(chat.contains("chatSessionFacade.ensureForChat")),
                () -> assertTrue(chat.contains("chatSessionFacade.touch")),
                () -> assertFalse(chat.contains("private void assertSessionRead")),
                () -> assertFalse(chat.contains("private void assertSessionWrite")),
                () -> assertFalse(chat.contains("private void assertSessionOwner")),
                () -> assertFalse(chat.contains("private void applyProjectSessionDefaults")),
                () -> assertFalse(chat.contains("chatSessionService.canRead")),
                () -> assertFalse(chat.contains("chatSessionService.canWrite")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
