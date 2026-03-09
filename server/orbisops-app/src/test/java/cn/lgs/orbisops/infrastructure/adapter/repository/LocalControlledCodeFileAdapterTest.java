package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.ControlledCodeFilePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalControlledCodeFileAdapterTest {

    @TempDir
    Path tempDir;

    private final LocalControlledCodeFileAdapter adapter = new LocalControlledCodeFileAdapter();

    @Test
    void readsGrepsGlobsWritesAndExecutesInsideRealRoot() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/App.java"),
                "class App {\n  String password = abc123;\n}\n", StandardCharsets.UTF_8);

        assertTrue(adapter.exists(tempDir, "src/App.java"));
        assertTrue(adapter.read(tempDir, "src/App.java", true).contains("class App"));
        assertEquals(1, adapter.grep(
                tempDir, "**/*.java", "password", false, false, 10).size());
        assertEquals(List.of("src/App.java"), adapter.glob(
                tempDir, "**/*.java", 10).stream().map(ControlledCodeFilePort.FileEntry::path).toList());

        adapter.write(tempDir, "src/New.java", "class New {}\n");
        assertTrue(adapter.exists(tempDir, "src/New.java"));
        ControlledCodeFilePort.ProcessOutput output = adapter.execute(
                tempDir, List.of("pwd"), 10_000, 4096);
        assertEquals(0, output.exitCode());
        assertTrue(output.output().contains(tempDir.toRealPath().toString()));
        assertFalse(output.truncated());
    }

    @Test
    void rejectsTraversalBinaryAndNonDirectoryCwd() throws Exception {
        Files.write(tempDir.resolve("binary.bin"), new byte[]{1, 0, 2});
        Files.writeString(tempDir.resolve("file.txt"), "text", StandardCharsets.UTF_8);

        assertThrows(SecurityException.class,
                () -> adapter.read(tempDir, "../outside.txt", true));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.read(tempDir, "binary.bin", true));
        assertThrows(SecurityException.class,
                () -> adapter.directory(tempDir, "file.txt"));
    }

    @Test
    void missingOptionalFileReturnsEmptyButMissingRequiredFileFails() {
        assertEquals("", adapter.read(tempDir, "missing.txt", false));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.read(tempDir, "missing.txt", true));
    }
}
