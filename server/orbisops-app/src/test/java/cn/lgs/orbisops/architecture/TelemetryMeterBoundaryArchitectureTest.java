package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryMeterBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void telemetryServiceMustDelegateConstructorBoundMicrometerAdapter() throws IOException {
        String service = read(OPS + "OpsTelemetryService.java");
        String meters = read(OPS + "OpsTelemetryMeters.java");

        assertAll(
                () -> assertTrue(service.contains("private final OpsTelemetryMeters meters")),
                () -> assertTrue(service.contains("ObjectProvider<MeterRegistry> meterRegistryProvider")),
                () -> assertTrue(service.contains("new OpsTelemetryMeters(meterRegistry, runningRuns)")),
                () -> assertTrue(service.contains("meters.runtimeCompleted(")),
                () -> assertTrue(service.contains("meters.runtimeNode(")),
                () -> assertFalse(service.contains("@Autowired(required = false)")),
                () -> assertFalse(service.contains("void bindMeterRegistry(")),
                () -> assertFalse(service.contains("volatile Counter")),
                () -> assertFalse(service.contains("Counter.builder(")),
                () -> assertFalse(service.contains("Timer.builder(")),
                () -> assertTrue(service.lines().count() <= 130),
                () -> assertTrue(meters.contains("Counter.builder(\"ops_ai_runs_submitted_total\")")),
                () -> assertTrue(meters.contains("Gauge.builder(\"ops_ai_runs_running\"")),
                () -> assertTrue(meters.contains("ops_agent_runtime_nodes_total")),
                () -> assertTrue(meters.contains("boolean percentileHistogram")),
                () -> assertTrue(meters.contains("if (percentileHistogram)")),
                () -> assertFalse(meters.contains("@Service")));
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
