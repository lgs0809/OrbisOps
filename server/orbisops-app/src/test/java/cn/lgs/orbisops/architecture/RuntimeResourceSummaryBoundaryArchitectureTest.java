package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeResourceSummaryBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void auditorMustOwnFinalMetadataAndRuntimeResourceEvent() throws IOException {
        String auditor = read(RUNTIME + "OpsRuntimeResourceSummaryAuditor.java");

        assertAll(
                () -> assertTrue(auditor.contains("\"owner\"")),
                () -> assertTrue(auditor.contains("\"projectId\"")),
                () -> assertTrue(auditor.contains("\"modelId\"")),
                () -> assertTrue(auditor.contains("\"mcpIds\"")),
                () -> assertTrue(auditor.contains("\"mcpServerCount\"")),
                () -> assertTrue(auditor.contains("\"skillNames\"")),
                () -> assertTrue(auditor.contains("RUNTIME_RESOURCES")),
                () -> assertTrue(auditor.contains("运行资源装配完成")),
                () -> assertFalse(auditor.contains("ObjectProvider")),
                () -> assertFalse(auditor.contains("@Autowired")),
                () -> assertFalse(auditor.contains("@Value")),
                () -> assertTrue(auditor.lines().count() < 50));
    }

    @Test
    void pipelineMustDelegateAndAssemblerMustContainNoSummaryPayloadConstruction() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeResourceSummaryAuditor summaryAuditor")),
                () -> assertTrue(pipeline.contains(
                        "rule(\"SUMMARY\", summaryAuditor::summarize)")),
                () -> assertEquals(1, occurrences(
                        assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeResourceSummaryAuditor")),
                () -> assertFalse(assembler.contains("RUNTIME_RESOURCES")),
                () -> assertFalse(assembler.contains("运行资源装配完成")),
                () -> assertFalse(assembler.contains("mcpServerCount")),
                () -> assertFalse(assembler.contains("private String value(")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void auditorMustBeAConcreteStatelessComponent() throws IOException {
        String auditor = read(RUNTIME + "OpsRuntimeResourceSummaryAuditor.java");

        assertAll(
                () -> assertTrue(auditor.contains("@Component")),
                () -> assertEquals(0, occurrences(
                        auditor, "public OpsRuntimeResourceSummaryAuditor(")),
                () -> assertEquals(1, occurrences(auditor, "public void summarize(")));
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
