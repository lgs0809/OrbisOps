package cn.lgs.orbisops.application.toolset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalMySqlApplicationServiceTest {

    @Test
    void readonlyQueryDelegatesBoundedSelectAndRejectsWrites() {
        LocalMySqlExecutionPort port = mock(LocalMySqlExecutionPort.class);
        LocalMySqlApplicationService service =
                new LocalMySqlApplicationService(port);
        LocalMySqlExecutionTarget target =
                new LocalMySqlExecutionTarget("", "", 2, 5);
        when(port.query(eq(target), eq("SELECT id FROM orders"), eq(List.of())))
                .thenReturn(List.of(
                        Map.of("id", 1),
                        Map.of("id", 2),
                        Map.of("id", 3)));

        assertEquals(2, service.readonlyQuery(
                target, "", "SELECT id FROM orders").size());
        assertThrows(SecurityException.class, () ->
                service.readonlyQuery(
                        target, "", "DELETE FROM orders"));
    }
}
