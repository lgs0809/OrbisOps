package cn.lgs.orbisops.application.toolset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalHostApplicationServiceTest {

    @Test
    void commandOutputIsMaskedAndFailureIsNotDowngraded() {
        LocalHostCommandPort commandPort = mock(LocalHostCommandPort.class);
        LocalLogFilePort logPort = mock(LocalLogFilePort.class);
        LocalHostApplicationService service =
                new LocalHostApplicationService(commandPort, logPort);
        when(commandPort.execute(
                anyList(), anyString(), eq(5)))
                .thenReturn(new LocalHostCommandResult(
                        0, "token=secret-value\nstatus=ok"));

        String output = service.run(
                List.of("docker", "ps"), ".", "", false, 5, 4096);
        assertTrue(output.contains("token=***"));
        assertFalse(output.contains("secret-value"));

        when(commandPort.execute(
                anyList(), anyString(), eq(5)))
                .thenReturn(new LocalHostCommandResult(2, "failed"));
        assertThrows(IllegalStateException.class, () ->
                service.run(
                        List.of("docker", "inspect", "missing"),
                        ".", "", false, 5, 4096));
    }

    @Test
    void logResultsAreMaskedAndBounded() {
        LocalHostCommandPort commandPort = mock(LocalHostCommandPort.class);
        LocalLogFilePort logPort = mock(LocalLogFilePort.class);
        LocalHostApplicationService service =
                new LocalHostApplicationService(commandPort, logPort);
        when(logPort.tail("/tmp/demo.log", 1))
                .thenReturn(List.of(
                        "password=secret-value",
                        "second line"));

        List<String> lines = service.tail(
                "/tmp/demo.log", "/tmp", 1, 10);
        assertTrue(lines.get(0).contains("password=***"));
        assertFalse(lines.get(0).contains("secret-value"));
    }
}
