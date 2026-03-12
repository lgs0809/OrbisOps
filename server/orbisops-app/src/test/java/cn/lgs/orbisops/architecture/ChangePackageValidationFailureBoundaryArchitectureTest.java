package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageValidationFailureBoundaryArchitectureTest {

    private static final String DOMAIN_MODEL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/model/ChangePackageValidationFailure.java";
    private static final String POINTER_PORT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/adapter/repository/IChangePackagePointerRepository.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/changepackage/ChangePackageValidationWritebackUseCase.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChangePackagePointerRepository.java";

    @Test
    void domainFailureOwnsValidationFailureInvariants() throws IOException {
        String failure = read(DOMAIN_MODEL);

        assertAll(
                () -> assertTrue(failure.contains("String assessment")),
                () -> assertTrue(failure.contains("String reasonCode")),
                () -> assertTrue(failure.contains("Map<String, Object> failureSummary")),
                () -> assertTrue(failure.contains("Collections.unmodifiableMap")),
                () -> assertTrue(failure.contains("ChangePackageStatus.VALIDATION_FAILED")),
                () -> assertTrue(failure.contains("CHANGE_PACKAGE_VALIDATION_ASSESSMENT_REQUIRED")),
                () -> assertTrue(failure.contains("CHANGE_PACKAGE_VALIDATION_REASON_CODE_REQUIRED")),
                () -> assertTrue(failure.contains("CHANGE_PACKAGE_VALIDATION_FAILURE_SUMMARY_REQUIRED")),
                () -> assertFalse(failure.contains("failureSummaryJson")),
                () -> assertFalse(failure.contains("com.alibaba.fastjson")));
    }

    @Test
    void applicationAndPortPassOneTypedFailure() throws IOException {
        String port = read(POINTER_PORT);
        String application = read(APPLICATION);

        assertAll(
                () -> assertTrue(port.contains("ChangePackageValidationFailure failure")),
                () -> assertFalse(port.contains("String validationAssessment")),
                () -> assertFalse(port.contains("String failureSummaryJson")),
                () -> assertTrue(application.contains("new ChangePackageValidationFailure(")),
                () -> assertTrue(application.contains("compareAndSetValidationFailure(current.pointer(), failure)")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("JSON.toJSONString(report)")));
    }

    @Test
    void repositoryExclusivelyOwnsFailureSummaryJsonColumn() throws IOException {
        String repository = read(REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("ChangePackageValidationFailure failure")),
                () -> assertTrue(repository.contains("failure.status().name()")),
                () -> assertTrue(repository.contains("failure.assessment()")),
                () -> assertTrue(repository.contains("failure.reasonCode()")),
                () -> assertTrue(repository.contains("ChangePackageJsonMapCodec.encode(failure.failureSummary())")),
                () -> assertTrue(repository.contains("failure_summary_json")),
                () -> assertFalse(repository.contains("String validationAssessment")),
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
