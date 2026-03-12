package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpJsonCodecBoundaryArchitectureTest {

    private static final String APPLICATION_ROOT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/mcp/";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/mcp/OpsProgressiveMcpApplicationConfiguration.java";
    private static final String REMOVED_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/mcp/OpsMcpJsonCodecAdapter.java";

    @Test
    void applicationOwnsPureTolerantCodecWithoutPortOrSpringBean() throws IOException {
        String codec = read(APPLICATION_ROOT + "McpJsonCodec.java");
        String application = readJavaTree(APPLICATION_ROOT);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(codec.contains("final class McpJsonCodec")),
                () -> assertTrue(codec.contains("CanonicalJson.parseArray")),
                () -> assertTrue(codec.contains("CanonicalJson.parseObject")),
                () -> assertTrue(codec.contains("CanonicalJson.stringifyPreservingOrder")),
                () -> assertFalse(codec.contains("com.alibaba.fastjson")),
                () -> assertFalse(codec.contains("com.fasterxml.jackson")),
                () -> assertTrue(codec.contains("return Map.of()")),
                () -> assertFalse(codec.contains("org.springframework")),
                () -> assertFalse(application.contains("McpJsonCodecPort")),
                () -> assertTrue(configuration.contains("new McpJsonCodec()")),
                () -> assertFalse(configuration.contains("McpJsonCodecPort")),
                () -> assertFalse(Files.exists(projectRoot().resolve(REMOVED_ADAPTER))));
    }

    private String readJavaTree(String relativeRoot) throws IOException {
        StringBuilder source = new StringBuilder();
        try (var files = Files.walk(projectRoot().resolve(relativeRoot))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
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
