package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageLandingCompletionBoundaryArchitectureTest {

    private static final String DOMAIN_MODEL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/model/ChangePackageLandingCompletion.java";
    private static final String POINTER_PORT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/adapter/repository/IChangePackagePointerRepository.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/changepackage/ChangePackageLandingProcessManager.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChangePackagePointerRepository.java";

    @Test
    void domainCompletionOwnsLandingResultAndFailureInvariants() throws IOException {
        String completion = read(DOMAIN_MODEL);

        assertAll(
                () -> assertTrue(completion.contains("Map<String, Object> result")),
                () -> assertTrue(completion.contains("Map<String, Object> failureSummary")),
                () -> assertTrue(completion.contains("Collections.unmodifiableMap")),
                () -> assertTrue(completion.contains("ChangePackageStatus.LANDED")),
                () -> assertTrue(completion.contains("ChangePackageStatus.NEEDS_REPLAN")),
                () -> assertTrue(completion.contains("ChangePackageStatus.LANDING_FAILED")),
                () -> assertTrue(completion.contains("CHANGE_PACKAGE_LANDED_FAILURE_SUMMARY_FORBIDDEN")),
                () -> assertTrue(completion.contains("CHANGE_PACKAGE_LANDING_FAILURE_SUMMARY_REQUIRED")),
                () -> assertFalse(completion.contains("landingResultJson")),
                () -> assertFalse(completion.contains("failureSummaryJson")),
                () -> assertFalse(completion.contains("com.alibaba.fastjson")));
    }

    @Test
    void applicationAndPortPassOneTypedCompletion() throws IOException {
        String port = read(POINTER_PORT);
        String application = read(APPLICATION);

        assertAll(
                () -> assertTrue(port.contains("ChangePackageLandingCompletion completion")),
                () -> assertFalse(port.contains("String landingResultJson")),
                () -> assertFalse(port.contains("String failureSummaryJson")),
                () -> assertTrue(application.contains("new ChangePackageLandingCompletion(")),
                () -> assertTrue(application.contains("compareAndSetLandingResult(current.pointer(), completion)")),
                () -> assertTrue(application.contains("compareAndSetLandingResult(runningPointer, completion)")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("JSON.toJSONString(result)")));
    }

    @Test
    void repositoryExclusivelyOwnsLandingJsonColumns() throws IOException {
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("ChangePackageLandingCompletion completion")),
                () -> assertTrue(repository.contains("ChangePackageJsonMapCodec.encode(completion.result())")),
                () -> assertTrue(repository.contains("ChangePackageJsonMapCodec.encode(completion.failureSummary())")),
                () -> assertTrue(repository.contains("completion.landingRunId()")),
                () -> assertTrue(repository.contains("landing_result_json")),
                () -> assertTrue(repository.contains("failure_summary_json")),
                () -> assertFalse(repository.contains("String landingResultJson")),
                () -> assertFalse(repository.contains("String failureSummaryJson")));
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
