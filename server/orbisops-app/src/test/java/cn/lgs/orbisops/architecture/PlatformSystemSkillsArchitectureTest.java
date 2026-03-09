package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSystemSkillsArchitectureTest {

    private static final String ROOT = "orbisops-app/src/main/resources/skills/";

    @Test
    void complexPlatformWorkflowsAreSkillsBackedByAtomicGovernedTools() throws IOException {
        Map<String, String> skills = Map.of(
                "skill-creator", read("skill-creator"),
                "skill-installer", read("skill-installer"),
                "mcp-onboarding", read("mcp-onboarding"),
                "change-package-preparer", read("change-package-preparer"));

        assertAll(
                () -> assertTrue(skills.get("skill-creator").contains("name: skill-creator")),
                () -> assertTrue(skills.get("skill-creator").contains("skill_project_create")),
                () -> assertTrue(skills.get("skill-installer").contains("name: skill-installer")),
                () -> assertTrue(skills.get("skill-installer").contains("skill_package_import")),
                () -> assertTrue(skills.get("mcp-onboarding").contains("name: mcp-onboarding")),
                () -> assertTrue(skills.get("mcp-onboarding").contains("mcp_server_import")),
                () -> assertTrue(skills.get("change-package-preparer").contains("name: change-package-preparer")),
                () -> assertTrue(skills.get("change-package-preparer").contains("change_package_create")),
                () -> assertTrue(skills.values().stream().allMatch(text -> text.contains("Runtime")
                        || text.contains("runtime")
                        || text.contains("Tool")
                        || text.contains("tool"))),
                () -> assertFalse(skills.containsKey("landing")));
    }

    private String read(String skillName) throws IOException {
        return Files.readString(projectRoot().resolve(ROOT + skillName + "/SKILL.md"));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-app"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-app"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-app"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
