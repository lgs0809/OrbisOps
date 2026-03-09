package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents one bounded context from orchestrating another through its concrete application services. */
class ApplicationCrossContextServiceDependencyArchitectureTest {

    private static final String APPLICATION_ROOT =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/";
    private static final String IMPORT_PREFIX = "import cn.lgs.orbisops.application.";
    private static final Set<String> FORBIDDEN_SUFFIXES = Set.of(
            "ApplicationService",
            "UseCase",
            "ProcessManager",
            "Coordinator",
            "QueryService");

    @Test
    void boundedContextsCollaborateThroughPortsAndPublishedLanguageInsteadOfConcreteServices() throws IOException {
        Map<String, String> groups = applicationGroups();
        List<String> violations = new ArrayList<>();
        Path root = projectRoot().resolve(APPLICATION_ROOT);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> inspect(path, root, groups, violations));
        }
        assertTrue(violations.isEmpty(), () -> "Cross-context concrete Application service dependencies:\n"
                + String.join("\n", violations));
    }

    private void inspect(Path file, Path root, Map<String, String> groups, List<String> violations) {
        String sourceContext = root.relativize(file).getName(0).toString();
        String sourceGroup = groups.get(sourceContext);
        if (sourceGroup == null) return;
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(IMPORT_PREFIX)) continue;
                String imported = trimmed.substring(IMPORT_PREFIX.length(), trimmed.length() - 1);
                int separator = imported.indexOf('.');
                if (separator < 0) continue;
                String targetContext = imported.substring(0, separator);
                String targetGroup = groups.get(targetContext);
                if (targetGroup == null || sourceGroup.equals(targetGroup)) continue;
                String simpleName = imported.substring(imported.lastIndexOf('.') + 1);
                if (FORBIDDEN_SUFFIXES.stream().anyMatch(simpleName::endsWith)) {
                    violations.add(projectRoot().relativize(file) + " -> " + trimmed);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + file, exception);
        }
    }

    private Map<String, String> applicationGroups() {
        Map<String, String> groups = new LinkedHashMap<>();
        register(groups, "project-workspace", "project", "source");
        register(groups, "agent-definition", "agentdefinition", "agent", "capability", "schedule");
        register(groups, "work-session", "worksession", "runtime", "analysis", "intent", "chat", "chatsession", "resourcehealth");
        register(groups, "controlled-change", "changepackage");
        register(groups, "execution-governance", "execution", "toolexecution", "mcpexecution", "mcp", "toolset");
        register(groups, "evidence-trust", "evidence", "audit");
        register(groups, "knowledge-rag", "knowledge", "rag");
        register(groups, "skill-lifecycle", "skill");
        register(groups, "memory", "memory");
        register(groups, "channel", "channel");
        register(groups, "alert-incident", "alert", "incident", "statistics", "agenteval", "modelpolicy", "config");
        register(groups, "repair-sandbox", "repair", "sandbox");
        register(groups, "security", "security");
        return Map.copyOf(groups);
    }

    private void register(Map<String, String> groups, String group, String... contexts) {
        for (String context : contexts) groups.put(context, group);
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
