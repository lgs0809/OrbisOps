package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCatalogTypedBoundaryArchitectureTest {

    @Test
    void skillCatalogMustPublishTypedSnapshotsAndSeparateRoutingReadiness() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillCatalogPort.java");
        String readService = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillCatalogReadService.java");
        String assembler = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillRuntimeCandidateAssembler.java");
        String selection = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SelectRuntimeSkillsQuery.java");
        String candidate = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/skill/model/SkillRuntimeCandidate.java");
        String access = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillRuntimeCatalogAccess.java");

        assertAll(
                () -> assertTrue(port.contains("List<SkillCatalogSnapshot> listGlobalEntries()")),
                () -> assertTrue(port.contains("SkillCatalogSnapshot getGlobalEntry(String skillId)")),
                () -> assertTrue(port.contains("List<SkillCatalogSnapshot> listProjectEntries(String projectId)")),
                () -> assertTrue(port.contains("SkillCatalogSnapshot getProjectEntry(String projectId, String skillId)")),
                () -> assertFalse(port.contains("List<Map<String, Object>>")),
                () -> assertFalse(port.contains("Map<String, Object> getGlobal")),
                () -> assertFalse(port.contains("Map<String, Object> getProject")),
                () -> assertTrue(readService.contains("new SkillCatalogSnapshot(")),
                () -> assertTrue(assembler.contains("routingProfilePolicy.profile(")),
                () -> assertFalse(assembler.contains("requireCanonicalProfile(")),
                () -> assertTrue(candidate.contains("boolean routingReady()")),
                () -> assertTrue(access.contains("c.routingReady()")),
                () -> assertTrue(selection.contains("access.active(request.projectId())")),
                () -> assertTrue(selection.contains("SKILL_ROUTING_PROFILE_REQUIRED_AT_RUNTIME")),
                () -> assertFalse(selection.contains("skill.get(\"status\")")),
                () -> assertFalse(selection.contains("skill.get(\"updateMode\")")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
