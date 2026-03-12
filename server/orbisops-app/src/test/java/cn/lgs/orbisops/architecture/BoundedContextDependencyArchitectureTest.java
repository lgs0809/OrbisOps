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

/** Executable strategic Context Map for all explicitly owned domain and application packages. */
class BoundedContextDependencyArchitectureTest {

    private static final String DOMAIN_ROOT = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/";
    private static final String APPLICATION_ROOT = "orbisops-application/src/main/java/cn/lgs/orbisops/application/";

    @Test
    void allDomainsKeepBoundedContextOwnership() throws IOException {
        assertMatrix(DOMAIN_ROOT, domainMatrix());
    }

    @Test
    void allApplicationsFollowDeclaredContextMap() throws IOException {
        assertMatrix(APPLICATION_ROOT, applicationMatrix());
    }

    private void assertMatrix(String root, Map<String, Set<String>> matrix) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : matrix.entrySet()) {
            violations.addAll(contextViolations(root + entry.getKey(), entry.getValue()));
        }
        assertTrue(violations.isEmpty(), () -> "Bounded Context dependency violations:\n"
                + String.join("\n", violations));
    }

    private Map<String, Set<String>> domainMatrix() {
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        register(matrix, Set.of("project", "source", "shared"), "project", "source");
        register(matrix, Set.of("agentdefinition", "shared"), "agentdefinition");
        register(matrix, Set.of(
                        "worksession", "runtime", "analysis", "investigation", "chatsession", "shared"),
                "worksession", "runtime", "analysis", "investigation", "chatsession");
        register(matrix, Set.of("changepackage", "shared"), "changepackage");
        register(matrix, Set.of("execution", "toolexecution", "mcpexecution", "mcp", "toolset", "shared"),
                "execution", "toolexecution", "mcpexecution", "mcp", "toolset");
        register(matrix, Set.of("evidence", "audit", "shared"), "evidence", "audit");
        register(matrix, Set.of("knowledge", "rageval", "shared"), "knowledge", "rageval");
        register(matrix, Set.of("skill", "shared"), "skill");
        register(matrix, Set.of("memory", "shared"), "memory");
        register(matrix, Set.of("channel", "shared"), "channel");
        register(matrix, Set.of("alert", "incident", "statistics", "agenteval", "modelpolicy", "shared"),
                "alert", "incident", "statistics", "agenteval", "modelpolicy");
        register(matrix, Set.of("repair", "shared"), "repair");
        register(matrix, Set.of("security", "shared"), "security");
        register(matrix, Set.of("shared"), "shared");
        return Map.copyOf(matrix);
    }

    private Map<String, Set<String>> applicationMatrix() {
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        register(matrix, Set.of("project", "source", "shared"), "project", "source");
        register(matrix, Set.of("agentdefinition", "agent", "shared"),
                "agentdefinition", "agent", "capability", "schedule");
        register(matrix, Set.of(
                        "worksession", "runtime", "analysis", "investigation", "chatsession", "shared"),
                "worksession", "runtime", "analysis", "chatsession", "resourcehealth");
        register(matrix, Set.of("changepackage", "shared"), "changepackage");
        register(matrix, Set.of("execution", "toolexecution", "mcpexecution", "mcp", "toolset", "shared"),
                "execution", "toolexecution", "mcpexecution", "mcp", "toolset");
        register(matrix, Set.of("evidence", "audit", "shared"), "evidence", "audit");
        register(matrix, Set.of("knowledge", "rageval", "shared"), "knowledge", "rag");
        register(matrix, Set.of("skill", "shared"), "skill");
        register(matrix, Set.of("memory", "shared"), "memory");
        register(matrix, Set.of("channel", "shared"), "channel");
        register(matrix, Set.of("alert", "incident", "statistics", "agenteval", "modelpolicy", "shared"),
                "alert", "incident", "statistics", "agenteval", "modelpolicy", "config");
        register(matrix, Set.of("repair", "shared"), "repair");
        register(matrix, Set.of("security", "shared"), "security");
        return Map.copyOf(matrix);
    }

    private void register(Map<String, Set<String>> matrix, Set<String> allowedContexts, String... contexts) {
        for (String context : contexts) matrix.put(context, allowedContexts);
    }

    private List<String> contextViolations(String relativeDirectory, Set<String> allowedContexts) throws IOException {
        Path directory = projectRoot().resolve(relativeDirectory);
        if (!Files.isDirectory(directory)) return List.of("Missing context directory: " + relativeDirectory);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> collectViolations(path, allowedContexts, violations));
        }
        return violations;
    }

    private void collectViolations(Path file, Set<String> allowedContexts, List<String> violations) {
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                String prefix = "import cn.lgs.orbisops.domain.";
                if (!trimmed.startsWith(prefix)) continue;
                String remainder = trimmed.substring(prefix.length());
                int separator = remainder.indexOf('.');
                String importedContext = separator < 0
                        ? remainder.replace(";", "")
                        : remainder.substring(0, separator);
                if (!allowedContexts.contains(importedContext)
                        && !allowedPublishedLanguage(file, trimmed)) {
                    violations.add(projectRoot().relativize(file) + " -> " + trimmed);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + file, exception);
        }
    }

    private boolean allowedPublishedLanguage(Path file, String importLine) {
        String relative = projectRoot().relativize(file).toString().replace('\\', '/');
        if (relative.contains("/application/agentdefinition/")) {
            return importLine.startsWith("import cn.lgs.orbisops.domain.agenteval.model.");
        }
        if (relative.contains("/application/project/")) {
            return importLine.equals("import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;")
                    || importLine.equals("import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;");
        }
        if (relative.contains("/application/mcp/")) {
            return importLine.equals("import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;")
                    || importLine.equals("import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;");
        }
        if (relative.contains("/application/repair/")) {
            return importLine.startsWith("import cn.lgs.orbisops.domain.source.model.");
        }
        return false;
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
