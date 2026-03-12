package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAlertOutboxJobBoundaryArchitectureTest {

    private static final String JOB = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/job/";
    private static final String ALERT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/alert/";

    @Test
    void scheduledOutboxJobUsesTypedPolicyAndKeepsOverlapGuard() throws IOException {
        String job = read(JOB + "OpsAlertTriggerOutboxJob.java");
        String settings = read(JOB + "OpsAlertOutboxJobSettings.java");
        String configuration = read(ALERT + "OpsAlertOutboxJobConfiguration.java");

        assertAll(
                () -> assertTrue(job.contains("OpsAlertOutboxJobSettings settings")),
                () -> assertTrue(job.contains("AtomicBoolean running")),
                () -> assertTrue(job.contains("running.compareAndSet(false, true)")),
                () -> assertTrue(job.contains("settings.batchSize()")),
                () -> assertTrue(job.contains("@Scheduled")),
                () -> assertTrue(job.contains("@Autowired")),
                () -> assertFalse(job.contains("@Value")),
                () -> assertFalse(job.contains("private boolean enabled")),
                () -> assertFalse(job.contains("private int batchSize")),
                () -> assertFalse(job.contains("Number.class.cast")),
                () -> assertTrue(job.lines().count() <= 85),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(configuration.contains("orbisops.alert-triggers.outbox.enabled")),
                () -> assertTrue(configuration.contains("orbisops.alert-triggers.outbox.batch-size")));
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
