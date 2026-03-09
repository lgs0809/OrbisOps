package cn.lgs.orbisops.application.chatsession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionAccessUseCaseTest {

    @Test
    void trustedEntryWithoutActorOrSessionBypassesParticipantLookup() {
        ChatSessionAccessPort port = mock(ChatSessionAccessPort.class);
        ChatSessionAccessUseCase useCase = new ChatSessionAccessUseCase(port);

        assertDoesNotThrow(() -> useCase.assertRead("session-1", ""));
        assertDoesNotThrow(() -> useCase.assertWrite("", "alice"));
        assertDoesNotThrow(() -> useCase.assertOwner(null, "alice"));

        verify(port, never()).facts("session-1", "");
    }

    @Test
    void readRequiresReadableSession() {
        ChatSessionAccessPort port = mock(ChatSessionAccessPort.class);
        ChatSessionAccessUseCase useCase = new ChatSessionAccessUseCase(port);
        when(port.facts("session-1", "alice")).thenReturn(
                new ChatSessionAccessFacts(true, "owner", false, false));

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> useCase.assertRead("session-1", "alice"));

        assertEquals("SESSION_READ_FORBIDDEN", error.getMessage());
    }

    @Test
    void firstWriteMayCreateMissingCanonicalSession() {
        ChatSessionAccessPort port = mock(ChatSessionAccessPort.class);
        ChatSessionAccessUseCase useCase = new ChatSessionAccessUseCase(port);
        when(port.facts("session-1", "alice")).thenReturn(ChatSessionAccessFacts.missing());

        assertDoesNotThrow(() -> useCase.assertWrite("session-1", "alice"));
    }

    @Test
    void existingSessionRequiresWritePermission() {
        ChatSessionAccessPort port = mock(ChatSessionAccessPort.class);
        ChatSessionAccessUseCase useCase = new ChatSessionAccessUseCase(port);
        when(port.facts("session-1", "alice")).thenReturn(
                new ChatSessionAccessFacts(true, "owner", true, false));

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> useCase.assertWrite("session-1", "alice"));

        assertEquals("SESSION_WRITE_FORBIDDEN", error.getMessage());
    }

    @Test
    void ownerCheckDistinguishesMissingAndNonOwner() {
        ChatSessionAccessPort port = mock(ChatSessionAccessPort.class);
        ChatSessionAccessUseCase useCase = new ChatSessionAccessUseCase(port);
        when(port.facts("missing", "alice")).thenReturn(ChatSessionAccessFacts.missing());
        when(port.facts("session-1", "alice")).thenReturn(
                new ChatSessionAccessFacts(true, "bob", true, true));

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.assertOwner("missing", "alice"));
        SecurityException forbidden = assertThrows(
                SecurityException.class,
                () -> useCase.assertOwner("session-1", "alice"));

        assertEquals("会话不存在", missing.getMessage());
        assertEquals("SESSION_OWNER_REQUIRED", forbidden.getMessage());
    }
}
