package cn.lgs.orbisops.application.toolset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalRedisApplicationServiceTest {

    @Test
    void scanRequiresControlledPatternAndBoundsResults() {
        LocalRedisExecutionPort port = mock(LocalRedisExecutionPort.class);
        LocalRedisApplicationService service =
                new LocalRedisApplicationService(port);
        when(port.scan("demo:*", 2))
                .thenReturn(List.of("demo:a", "demo:b", "demo:c"));

        assertEquals(List.of("demo:a", "demo:b"),
                service.scan("demo:*", 2, 10));
        assertThrows(SecurityException.class,
                () -> service.scan("*", 10, 10));
    }
}
