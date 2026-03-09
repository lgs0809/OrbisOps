package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsIntentLlmClassifierBoundaryArchitectureTest {

    @Test
    void productionChatMustNotRequireAnIntentClassifier() throws IOException {
        String chat = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatApplicationService.java");
        String workSession = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/OpsWorkSessionRuntimeConfiguration.java");
        String preparation = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/chat/OpsChatRequestPreparationFacade.java");

        assertFalse(chat.contains("OpsIntentLlmClassifier"));
        assertFalse(chat.contains("OpsIntentRouter"));
        assertFalse(workSession.contains("OpsRuntimeIntentService"));
        assertFalse(preparation.contains("intentRouter.route"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
