package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeResourceContextFactoryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void factoryMustOwnAllThreeContextConstructionPathsAndMergePolicies() throws IOException {
        String factory = read(RUNTIME + "OpsRuntimeResourceContextFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("public OpsRuntimeResourceContext agent(")),
                () -> assertTrue(factory.contains("public OpsRuntimeResourceContext node(")),
                () -> assertTrue(factory.contains("public OpsRuntimeResourceContext agentScope(")),
                () -> assertEquals(3, occurrences(factory, "OpsRuntimeResourceContext.builder()")),
                () -> assertTrue(factory.contains("firstText(")),
                () -> assertTrue(factory.contains("firstNonNull(")),
                () -> assertTrue(factory.contains("booleanConfig(")),
                () -> assertTrue(factory.contains("inheritProjectCapabilities(")),
                () -> assertTrue(factory.contains("skillResolver.enabledProjectSkillIds(projectId)")),
                () -> assertTrue(factory.contains("mcpResolver.enabledProjectMcpIds(projectId)")),
                () -> assertTrue(factory.contains("executionTargetIds.addAll")),
                () -> assertFalse(factory.contains("ObjectProvider")),
                () -> assertFalse(factory.contains("@Autowired")),
                () -> assertFalse(factory.contains("@Value")),
                () -> assertTrue(factory.lines().count() < 230));
    }

    @Test
    void assemblerMustDelegateEveryContextPathAndContainNoMergeLogic() throws IOException {
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(assembler.contains(
                        "private final OpsRuntimeResourceContextFactory contextFactory;")),
                () -> assertTrue(assembler.contains("contextFactory.agent(")),
                () -> assertTrue(assembler.contains("contextFactory.node(")),
                () -> assertTrue(assembler.contains("contextFactory.agentScope(")),
                () -> assertFalse(assembler.contains("OpsRuntimeResourceContext.builder()")),
                () -> assertFalse(assembler.contains("inheritProjectCapabilities")),
                () -> assertFalse(assembler.contains("firstText(")),
                () -> assertFalse(assembler.contains("firstNonNull(")),
                () -> assertFalse(assembler.contains("booleanConfig(")),
                () -> assertFalse(assembler.contains("LinkedHashSet")),
                () -> assertFalse(assembler.contains("Optional")),
                () -> assertFalse(assembler.contains("StringUtils")),
                () -> assertEquals(1, occurrences(
                        assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
