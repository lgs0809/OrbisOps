package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelAttachmentExecutionIsolationArchitectureTest {

    @Test
    void channelAttachmentsRemainOpaqueAssetsAndCannotBecomeExecutableWorkspaceInputs() throws IOException {
        Path root = projectRoot();
        String inbound = Files.readString(root.resolve(
                "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/channel/OpsChannelInboundProtocolAdapter.java"));
        String assetStore = Files.readString(root.resolve(
                "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/channel/FileChannelAttachmentAssetAdapter.java"));

        assertAll(
                () -> assertTrue(inbound.contains("ref.startsWith(\"bridge://\") || ref.startsWith(\"object://\")")),
                () -> assertTrue(assetStore.contains("private static final String REF_PREFIX = \"object://channel/\"")),
                () -> assertTrue(assetStore.contains("UUID.fromString(objectId)")),
                () -> assertTrue(assetStore.contains("root.resolve(objectId).normalize()")),
                () -> assertTrue(assetStore.contains("resolved.startsWith(root)")),
                () -> assertFalse(assetStore.contains("root.resolve(command.fileName()")),
                () -> assertFalse(assetStore.contains("ProcessBuilder")),
                () -> assertFalse(assetStore.contains("Runtime.getRuntime")));

        List<Path> channelSources = List.of(
                root.resolve("orbisops-application/src/main/java/cn/lgs/orbisops/application/channel"),
                root.resolve("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/channel"),
                root.resolve("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/channel"),
                root.resolve("orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/channel"));
        for (Path sourceRoot : channelSources) {
            try (var files = Files.walk(sourceRoot)) {
                for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(source);
                    assertFalse(text.contains("cn.lgs.orbisops.application.repair"), () -> "Channel source imports repair application: " + source);
                    assertFalse(text.contains("cn.lgs.orbisops.trigger.ops.repair"), () -> "Channel source imports repair trigger: " + source);
                    assertFalse(text.contains("RepairWorkspace"), () -> "Channel source references executable repair workspace: " + source);
                    assertFalse(text.contains("ProcessBuilder"), () -> "Channel source launches a process: " + source);
                    assertFalse(text.contains("Runtime.getRuntime"), () -> "Channel source launches runtime process: " + source);
                }
            }
        }
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
