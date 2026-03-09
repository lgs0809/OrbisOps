package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsPreparationRepairContextHydratorTest {

    private static final String ARTIFACT_SHA = "a".repeat(64);
    private static final String DIFF_HASH = "b".repeat(64);

    @Test
    void authoritativeWorkspaceOverridesCallerSuppliedRepairIdentity() {
        OpsRepairWorkspaceService workspaces = mock(OpsRepairWorkspaceService.class);
        when(workspaces.get("repair-1")).thenReturn(Optional.of(workspace("VERIFIED")));
        when(workspaces.computeRepairDiff("repair-1")).thenReturn(Map.of(
                "diffHash", DIFF_HASH,
                "changedFiles", List.of("demo-project/App.java")));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("repairWorkspaceId", "repair-1");
        request.put("repairCommit", "caller-controlled");
        request.put("diffHash", "caller-controlled");
        request.put("artifactDigest", "sha256:" + "f".repeat(64));

        new OpsPreparationRepairContextHydrator(workspaces).hydrate(request, "demo-project");

        assertEquals("service-1", request.get("serviceId"));
        assertEquals("repo-1", request.get("repositoryId"));
        assertEquals("base-commit", request.get("baseCommit"));
        assertEquals("verified-commit", request.get("repairCommit"));
        assertEquals(DIFF_HASH, request.get("diffHash"));
        assertEquals(List.of("demo-project/App.java"), request.get("changedFiles"));
        assertEquals("mvn -q test package", request.get("testCommand"));
        assertEquals("sha256:" + ARTIFACT_SHA, request.get("artifactDigest"));
    }

    @Test
    void nonVerifiedOrCrossProjectWorkspaceFailsClosed() {
        OpsRepairWorkspaceService workspaces = mock(OpsRepairWorkspaceService.class);
        when(workspaces.get("repair-1")).thenReturn(Optional.of(workspace("TEST_FAILED")));
        OpsPreparationRepairContextHydrator hydrator = new OpsPreparationRepairContextHydrator(workspaces);

        assertThrows(IllegalStateException.class,
                () -> hydrator.hydrate(new LinkedHashMap<>(Map.of("repairWorkspaceId", "repair-1")), "demo-project"));

        when(workspaces.get("repair-1")).thenReturn(Optional.of(
                OpsRepairWorkspaceDTO.builder()
                        .workspaceId("repair-1")
                        .projectId("other-project")
                        .status("VERIFIED")
                        .verifiedCommit("verified-commit")
                        .build()));
        assertThrows(SecurityException.class,
                () -> hydrator.hydrate(new LinkedHashMap<>(Map.of("repairWorkspaceId", "repair-1")), "demo-project"));
    }

    private OpsRepairWorkspaceDTO workspace(String status) {
        return OpsRepairWorkspaceDTO.builder()
                .workspaceId("repair-1")
                .projectId("demo-project")
                .serviceId("service-1")
                .repositoryId("repo-1")
                .baseCommit("base-commit")
                .verifiedCommit("verified-commit")
                .status(status)
                .testCommand("mvn -q test package")
                .artifactSha256(ARTIFACT_SHA)
                .build();
    }
}
