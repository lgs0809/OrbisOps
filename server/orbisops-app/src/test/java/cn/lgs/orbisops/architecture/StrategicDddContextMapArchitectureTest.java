package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicDddContextMapArchitectureTest {

    private static final String DOCUMENT = "docs/architecture/strategic-ddd-context-map.md";

    @Test
    void strategicDddContextMapMustRemainExplicitAndCoverCoreBoundaries() throws IOException {
        Path document = projectRoot().resolve(DOCUMENT);
        assertTrue(Files.isRegularFile(document), DOCUMENT + " must exist");
        String content = Files.readString(document);

        List<String> requiredSections = List.of(
                "## 3. 限界上下文目录",
                "## 4. Context Map",
                "## 5. 聚合与一致性边界",
                "## 6. 跨上下文集成规则",
                "### 6.3 可执行 Context Map 基线",
                "## 8. Security 技术边界");
        List<String> requiredPackages = List.of(
                "domain.project",
                "domain.agentdefinition",
                "domain.worksession",
                "domain.changepackage",
                "domain.execution",
                "domain.mcpexecution",
                "domain.evidence",
                "domain.knowledge",
                "domain.skill",
                "domain.memory",
                "domain.channel",
                "domain.repair",
                "domain.security");

        for (String section : requiredSections) {
            assertTrue(content.contains(section), () -> "Missing strategic DDD section: " + section);
        }
        for (String domainPackage : requiredPackages) {
            assertTrue(content.contains(domainPackage),
                    () -> "Missing bounded-context package: " + domainPackage);
        }
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
