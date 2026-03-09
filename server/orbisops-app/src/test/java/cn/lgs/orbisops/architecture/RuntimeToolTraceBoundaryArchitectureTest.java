package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeToolTraceBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void decoratorMustOwnSchemaCancellationLifecycleAndPayloadAudit() throws IOException {
        String decorator = read(RUNTIME + "OpsRuntimeToolTraceDecorator.java");

        assertAll(
                () -> assertTrue(decorator.contains("Supplier<OpsRunCancellationRegistry>")),
                () -> assertTrue(decorator.contains("OpsToolSchemaNormalizer.normalize")),
                () -> assertTrue(decorator.contains("TOOL_CALL_SKIPPED")),
                () -> assertTrue(decorator.contains("TOOL_CALL_STARTED")),
                () -> assertTrue(decorator.contains("TOOL_CALL_FINISHED")),
                () -> assertTrue(decorator.contains("TOOL_CALL_BLOCKED")),
                () -> assertTrue(decorator.contains("TOOL_CALL_PROPOSED")),
                () -> assertTrue(decorator.contains("TOOL_CALL_FAILED")),
                () -> assertTrue(decorator.contains("OpsRunCanceledException")),
                () -> assertTrue(decorator.contains("toolKind")),
                () -> assertTrue(decorator.contains("outputChars")),
                () -> assertTrue(decorator.contains("remoteCallExecuted")),
                () -> assertTrue(decorator.contains("durationMs")),
                () -> assertTrue(decorator.contains("abbreviate(input, 4000)")),
                () -> assertFalse(decorator.contains("ObjectProvider")),
                () -> assertFalse(decorator.contains("@Autowired")),
                () -> assertFalse(decorator.contains("@Value")),
                () -> assertTrue(decorator.contains("OpsBusinessResourceIdentityProjector.projectOpenApiResult(")),
                () -> assertTrue(decorator.lines().count() < 310));
    }

    @Test
    void pipelineMustDelegateAndAssemblerMustContainNoTraceInfrastructure() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeToolTraceDecorator toolTraceDecorator")),
                () -> assertTrue(pipeline.contains(
                        "rule(\"TOOL_TRACE\", toolTraceDecorator::decorate)")),
                () -> assertEquals(1, occurrences(
                        assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeToolTraceDecorator")),
                () -> assertFalse(assembler.contains("OpsRunCancellationRegistry")),
                () -> assertFalse(assembler.contains("OpsRunCanceledException")),
                () -> assertFalse(assembler.contains("OpsToolSchemaNormalizer")),
                () -> assertFalse(assembler.contains("TOOL_CALL_STARTED")),
                () -> assertFalse(assembler.contains("TOOL_CALL_FINISHED")),
                () -> assertFalse(assembler.contains("TOOL_CALL_FAILED")),
                () -> assertFalse(assembler.contains("TOOL_CALL_SKIPPED")),
                () -> assertFalse(assembler.contains("callWithTrace(")),
                () -> assertFalse(assembler.contains("toolPayload(")),
                () -> assertFalse(assembler.contains("toolKind(")),
                () -> assertFalse(assembler.contains("isCanceled(")),
                () -> assertFalse(assembler.contains("@Autowired")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void configurationMustBeTheOnlyOptionalCancellationRegistryOwner() throws IOException {
        String configuration = read(
                RUNTIME + "OpsRuntimeToolTraceDecoratorConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<OpsRunCancellationRegistry>")),
                () -> assertTrue(configuration.contains(
                        "new OpsRuntimeToolTraceDecorator(")),
                () -> assertTrue(configuration.contains(
                        "cancellationRegistryProvider::getIfAvailable")),
                () -> assertTrue(configuration.lines().count() < 30));
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
