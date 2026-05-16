package cn.lgs.orbisops.trigger.ops.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Locates bundled MCP scripts relative to the current reactor or repository root. */
final class OpsProjectMcpScriptLocator {

    String locate(String fileName) {
        List<Path> candidates = new ArrayList<>();
        Path current = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();
        for (int depth = 0; current != null && depth < 6; depth++) {
            candidates.add(current.resolve(Path.of("scripts", "mcp", fileName)));
            current = current.getParent();
        }
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0))
                .toAbsolutePath()
                .normalize()
                .toString();
    }
}
