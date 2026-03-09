package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageVersionSnapshotBoundaryArchitectureTest {

    private static final String DOMAIN_MODEL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/model/";
    private static final String DOMAIN_SERVICE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/service/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/changepackage/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/changepackage/";

    @Test
    void domainVersionOwnsTypedHashSealedSnapshot() throws IOException {
        String version = read(DOMAIN_MODEL + "ChangePackageVersion.java");
        String snapshot = read(DOMAIN_MODEL + "ChangePackageSnapshot.java");
        String approval = read(DOMAIN_SERVICE + "ChangePackageApprovalAssessmentFactory.java");
        String landing = read(DOMAIN_SERVICE + "ChangePackageLandingPlanFactory.java");
        String validation = read(DOMAIN_SERVICE + "ChangePackageValidationWritebackFactory.java");

        assertAll(
                () -> assertTrue(version.contains("ChangePackageSnapshot snapshot")),
                () -> assertTrue(version.contains("snapshot.packageHash()")),
                () -> assertTrue(version.contains("new ChangePackageSnapshot(snapshot.toMap(), packageHash)")),
                () -> assertFalse(version.contains("String snapshotJson")),
                () -> assertFalse(version.contains("com.alibaba.fastjson")),
                () -> assertFalse(version.contains("CanonicalJson.parse")),
                () -> assertTrue(snapshot.contains("Immutable, hash-sealed ChangePackage version snapshot")),
                () -> assertTrue(approval.contains("version.snapshot()")),
                () -> assertTrue(landing.contains("approvedVersion.snapshot().toMap()")),
                () -> assertTrue(validation.contains("sourceVersion.snapshot().toMap()")),
                () -> assertFalse(approval.contains("snapshotJson()")),
                () -> assertFalse(landing.contains("snapshotJson()")),
                () -> assertFalse(validation.contains("snapshotJson()")));
    }

    @Test
    void applicationPassesSnapshotValuesWithoutPersistenceSerialization() throws IOException {
        String prepare = read(APPLICATION + "PrepareChangePackageUseCase.java");
        String approval = read(APPLICATION + "ChangePackageApprovalUseCase.java");
        String validation = read(APPLICATION + "ChangePackageValidationWritebackUseCase.java");

        assertAll(
                () -> assertTrue(prepare.contains("sourceVersion.snapshot().toMap()")),
                () -> assertTrue(prepare.contains("snapshot, \"initial\"")),
                () -> assertTrue(prepare.contains("snapshot, summary")),
                () -> assertTrue(approval.contains("targetVersion.snapshot(), command.actor()")),
                () -> assertTrue(validation.contains("writeback.snapshot(),")),
                () -> assertFalse(prepare.contains("snapshotJson()")),
                () -> assertFalse(approval.contains("snapshotJson()")),
                () -> assertFalse(validation.contains("snapshotJson()")),
                () -> assertFalse(prepare.contains("TypeReference<LinkedHashMap")));
    }

    @Test
    void infrastructureExclusivelyOwnsSnapshotJsonColumns() throws IOException {
        String codec = read(INFRASTRUCTURE + "ChangePackageSnapshotJsonCodec.java");
        String versions = read(INFRASTRUCTURE + "JdbcChangePackageVersionRepository.java");
        String pointers = read(INFRASTRUCTURE + "JdbcChangePackagePointerRepository.java");
        String query = read(INFRASTRUCTURE + "JdbcChangePackageQueryAdapter.java");
        String landingRuntime = read(TRIGGER + "OpsChangePackageLandingRuntimeAdapter.java");

        assertAll(
                () -> assertTrue(codec.contains("JSON.toJSONString(snapshot.toMap())")),
                () -> assertTrue(codec.contains("JSON.parseObject(snapshotJson)")),
                () -> assertTrue(codec.contains("new ChangePackageSnapshot")),
                () -> assertFalse(codec.contains("org.springframework")),
                () -> assertTrue(versions.contains("ChangePackageSnapshotJsonCodec.encode(version.snapshot())")),
                () -> assertTrue(versions.contains("ChangePackageSnapshotJsonCodec.decode")),
                () -> assertTrue(pointers.contains("ChangePackageSnapshot approvedSnapshot")),
                () -> assertTrue(pointers.contains("ChangePackageSnapshotJsonCodec.encode(approvedSnapshot)")),
                () -> assertTrue(query.contains("version.snapshot().toMap()")),
                () -> assertTrue(landingRuntime.contains("landingAgentRunCoordinator.execute(authorizationService.authorize(")),
                () -> assertFalse(landingRuntime.contains("JSON.toJSONString")),
                () -> assertFalse(landingRuntime.contains("versionView(")),
                () -> assertFalse(versions.contains("version.snapshotJson()")),
                () -> assertFalse(pointers.contains("String approvedSnapshotJson")));
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
