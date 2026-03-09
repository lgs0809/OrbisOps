package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageToolProviderBoundaryArchitectureTest {

    private static final String CHANGE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void springAiProviderDelegatesEvidenceRequestExecutionAndEncoding() throws IOException {
        String provider = read(CHANGE + "OpsChangePackageToolProvider.java");
        String input = read(CHANGE + "OpsChangePackageToolInput.java");
        String evidence = read(CHANGE + "OpsChangePackageRunEvidenceCollector.java");
        String request = read(CHANGE + "OpsChangePackageToolRequestFactory.java");
        String execution = read(CHANGE + "OpsChangePackageToolExecutionGateway.java");
        String renderer = read(CHANGE + "OpsChangePackageToolResultRenderer.java");

        assertAll(
                () -> assertTrue(provider.contains("FunctionToolCallback.builder")),
                () -> assertTrue(provider.contains("OpsChangePackageToolInput.class")),
                () -> assertTrue(provider.contains("EVIDENCE_COLLECTOR.collect(")),
                () -> assertTrue(provider.contains("REQUEST_FACTORY.create(")),
                () -> assertTrue(provider.contains("executionGateway.execute(")),
                () -> assertTrue(provider.contains("RESULT_RENDERER.success(")),
                () -> assertTrue(provider.contains("ObjectProvider<OpsToolExecutionService>")),
                () -> assertFalse(provider.contains("@Autowired(required = false)")),
                () -> assertFalse(provider.contains("OpsEvidenceReferenceDTO")),
                () -> assertFalse(provider.contains("MessageDigest")),
                () -> assertFalse(provider.contains("JSON.toJSONString")),
                () -> assertFalse(provider.contains("OpsToolExecutionScope")),
                () -> assertFalse(provider.contains("executePackageCreate(")),
                () -> assertFalse(provider.contains("class ChangePackageInput")),
                () -> assertTrue(input.contains("public final class OpsChangePackageToolInput")),
                () -> assertTrue(evidence.contains("MAX_EVIDENCE = 20")),
                () -> assertTrue(evidence.contains("PREPARE_TOOL_NAME")),
                () -> assertTrue(evidence.contains("MessageDigest.getInstance(\"SHA-256\")")),
                () -> assertTrue(request.contains("当前 Run 尚无成功的 MCP、RAG、工具或权威数据源证据")),
                () -> assertTrue(request.contains("contextBundleHash")),
                () -> assertTrue(execution.contains("OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW")),
                () -> assertTrue(execution.contains("change_package_create")),
                () -> assertTrue(renderer.contains("JSON.toJSONString")),
                () -> assertPlain(evidence),
                () -> assertPlain(request),
                () -> assertPlain(execution),
                () -> assertPlain(renderer));
    }

    private void assertPlain(String source) {
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("ObjectProvider"));
        assertFalse(source.contains("org.springframework"));
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
