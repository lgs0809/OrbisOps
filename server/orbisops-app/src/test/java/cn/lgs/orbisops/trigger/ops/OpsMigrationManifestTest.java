package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMigrationManifestTest {

    @Test
    void manifestRowsReferenceExistingSqlAndStableChecksums() throws Exception {
        Path root = findWorkspaceRoot();
        Path manifest = root.resolve("db/migrations/manifest.tsv");
        assertTrue(Files.isRegularFile(manifest), "migration manifest must exist");
        List<String> rows = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        assertTrue(rows.get(0).contains("checksum"), "manifest header must include checksum");
        for (String row : rows) {
            if (row.isBlank() || row.startsWith("#")) {
                continue;
            }
            String[] parts = row.split("\t");
            assertEquals(6, parts.length, "manifest row must include service/version/description/sql_path/checksum/created_at: " + row);
            Path sql = root.resolve(parts[3]);
            assertTrue(Files.isRegularFile(sql), "manifest sql_path missing: " + parts[3]);
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(sql)));
            assertEquals(parts[4], checksum, "manifest checksum mismatch: " + parts[3]);
            assertTrue(parts[5].matches("\\d{4}-\\d{2}-\\d{2}"), "created_at must be yyyy-MM-dd");
        }
    }

    private Path findWorkspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("db/migrations/manifest.tsv"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + Path.of("").toAbsolutePath());
    }
}
