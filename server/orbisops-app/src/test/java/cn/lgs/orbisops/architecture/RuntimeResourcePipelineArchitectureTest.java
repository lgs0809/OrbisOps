package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeResourcePipelineArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void pipelineMustOwnTheSingleSupportedRuleOrder() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");

        assertAll(
                () -> assertTrue(index(pipeline, "rule(\"MODEL\"")
                        < index(pipeline, "rule(\"MCP\"")),
                () -> assertTrue(index(pipeline, "rule(\"MCP\"")
                        < index(pipeline, "rule(\"SKILL\"")),
                () -> assertTrue(index(pipeline, "rule(\"SKILL\"")
                        < index(pipeline, "rule(\"KNOWLEDGE_BASE\"")),
                () -> assertTrue(index(pipeline, "rule(\"KNOWLEDGE_BASE\"")
                        < index(pipeline, "rule(\"BUILT_IN_TOOLS\"")),
                () -> assertTrue(index(pipeline, "rule(\"BUILT_IN_TOOLS\"")
                        < index(pipeline, "rule(\"SUB_AGENT_TOOL_BOUNDARY\"")),
                () -> assertTrue(index(pipeline, "rule(\"SUB_AGENT_TOOL_BOUNDARY\"")
                        < index(pipeline, "rule(\"TOOL_TRACE\"")),
                () -> assertTrue(index(pipeline, "rule(\"TOOL_TRACE\"")
                        < index(pipeline, "rule(\"SUMMARY\"")),
                () -> assertTrue(pipeline.contains("return context.toBundle();")),
                () -> assertTrue(pipeline.contains("public List<String> ruleNames()")),
                () -> assertFalse(pipeline.contains("ObjectProvider")),
                () -> assertFalse(pipeline.contains("@Autowired")),
                () -> assertFalse(pipeline.contains("@Value")),
                () -> assertTrue(pipeline.lines().count() < 90));
    }

    @Test
    void pipelineMustDependOnlyOnTypedRuntimeBoundaries() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");

        assertAll(
                () -> assertTrue(pipeline.contains("OpsRuntimeModelResolver modelResolver")),
                () -> assertTrue(pipeline.contains("OpsRuntimeMcpResolver mcpResolver")),
                () -> assertTrue(pipeline.contains("OpsRuntimeSkillResolver skillResolver")),
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeBuiltInToolContributor builtInToolContributor")),
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeKnowledgeResolver knowledgeResolver")),
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeSubAgentToolBoundaryPolicy subAgentToolBoundaryPolicy")),
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeToolTraceDecorator toolTraceDecorator")),
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeResourceSummaryAuditor summaryAuditor")),
                () -> assertFalse(pipeline.contains("Repository")),
                () -> assertFalse(pipeline.contains("Provider<")),
                () -> assertFalse(pipeline.contains("ApplicationContext")));
    }

    @Test
    void assemblerMustBeAStableTwoDependencyFacade() throws IOException {
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(assembler.contains(
                        "private final OpsRuntimeResourceContextFactory contextFactory;")),
                () -> assertTrue(assembler.contains(
                        "private final OpsRuntimeResourcePipeline pipeline;")),
                () -> assertEquals(1, occurrences(
                        assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertEquals(3, occurrences(
                        assembler, "public OpsRuntimeResourceBundle assemble")),
                () -> assertEquals(3, occurrences(assembler, "pipeline.assemble(")),
                () -> assertFalse(assembler.contains("OpsRuntimeResourceRule")),
                () -> assertFalse(assembler.contains("ObjectProvider")),
                () -> assertFalse(assembler.contains("@Autowired")),
                () -> assertFalse(assembler.contains("@Value")),
                () -> assertFalse(assembler.contains("Repository")),
                () -> assertFalse(assembler.contains("ApplicationContext")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    private int index(String source, String token) {
        int index = source.indexOf(token);
        if (index < 0) {
            throw new AssertionError("Missing token: " + token);
        }
        return index;
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
