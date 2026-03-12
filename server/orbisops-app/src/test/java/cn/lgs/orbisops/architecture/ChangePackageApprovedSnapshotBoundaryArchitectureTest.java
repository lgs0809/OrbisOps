package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageApprovedSnapshotBoundaryArchitectureTest {

    private static final String CURRENT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/model/ChangePackageCurrent.java";
    private static final String CURRENT_REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChangePackageCurrentRepository.java";
    private static final String QUERY_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChangePackageQueryAdapter.java";
    private static final String APPROVAL_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/changepackage/OpsChangePackageApprovalSupportAdapter.java";
    private static final String LANDING_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/changepackage/OpsChangePackageLandingRuntimeAdapter.java";

    @Test
    void currentAggregateEnforcesApprovedSnapshotInvariant() throws IOException {
        String current = read(CURRENT);

        assertAll(
                () -> assertTrue(current.contains("ChangePackageSnapshot approvedSnapshot")),
                () -> assertTrue(current.contains("if (pointer.approved())")),
                () -> assertTrue(current.contains("CHANGE_PACKAGE_APPROVED_SNAPSHOT_REQUIRED")),
                () -> assertTrue(current.contains("CHANGE_PACKAGE_APPROVED_SNAPSHOT_HASH_MISMATCH")),
                () -> assertTrue(current.contains("CHANGE_PACKAGE_UNAPPROVED_SNAPSHOT_FORBIDDEN")),
                () -> assertTrue(current.contains("approvedSnapshot.toMap(), pointer.approvedPackageHash()")),
                () -> assertTrue(current.contains("null, \"\", actor")),
                () -> assertFalse(current.contains("String approvedSnapshotJson")),
                () -> assertFalse(current.contains("approvedSnapshotJson")),
                () -> assertFalse(current.contains("com.alibaba.fastjson")),
                () -> assertFalse(current.contains("approved_snapshot_json")));
    }

    @Test
    void jdbcRepositoryOwnsApprovedSnapshotColumnDecoding() throws IOException {
        String repository = read(CURRENT_REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("row.get(\"approved_snapshot_json\")")),
                () -> assertTrue(repository.contains("CHANGE_PACKAGE_UNAPPROVED_SNAPSHOT_PERSISTED")),
                () -> assertTrue(repository.contains("ChangePackageSnapshotJsonCodec.decode(")),
                () -> assertTrue(repository.contains("pointer.approvedPackageHash()")),
                () -> assertTrue(repository.contains("ChangePackageSnapshot approvedSnapshot")),
                () -> assertFalse(repository.contains("ChangePackageCurrentState.fromPersistentValues(state), text(row.get(\"approved_snapshot_json\"))")));
    }

    @Test
    void readModelsAndLandingRuntimeUseTypedSnapshots() throws IOException {
        String query = read(QUERY_ADAPTER);
        String approval = read(APPROVAL_ADAPTER);
        String landing = read(LANDING_ADAPTER);

        assertAll(
                () -> assertTrue(query.contains("current.approvedSnapshot().toMap()")),
                () -> assertTrue(approval.contains("current.approvedSnapshot().toMap()")),
                () -> assertFalse(query.contains("current.approvedSnapshotJson()")),
                () -> assertFalse(approval.contains("current.approvedSnapshotJson()")),
                () -> assertTrue(landing.contains("landingAgentRunCoordinator.execute(authorizationService.authorize(")),
                () -> assertFalse(landing.contains("JSON.toJSONString")),
                () -> assertFalse(landing.contains("approved_snapshot_json")),
                () -> assertFalse(landing.contains("current.approvedSnapshotJson()")));
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
