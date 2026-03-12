package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit whitelist for Application ports whose result schema is intentionally dynamic. */
class DynamicApplicationPortBoundaryArchitectureTest {

    private static final String APPLICATION_ROOT =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application";
    private static final Pattern DYNAMIC_RETURN = Pattern.compile(
            "^(?:public\\s+|default\\s+)?(?:Map<String, Object>|List<Map<String, Object>>|Optional<Map<String, Object>>)\\s+\\w+\\s*\\(");

    private static final Map<String, String> ALLOWED_DYNAMIC_RESULT_PORTS = Map.ofEntries(
            Map.entry("analysis/AnalysisTaskSupplementPort.java", "QUERY_SUPPLEMENT"),
            Map.entry("changepackage/ChangePackageQueryPort.java", "QUERY_READ_MODEL"),
            Map.entry("execution/ExecutionAdapterTemplatePort.java", "QUERY_GENERATED_TARGETS"),
            Map.entry("mcp/McpRuntimeOperationsPort.java", "QUERY_RUNTIME_CATALOG"),
            Map.entry("mcp/McpSummaryPort.java", "QUERY_SUMMARY_PROJECTION"),
            Map.entry("mcp/McpTemplateProjectUsagePort.java", "QUERY_TEMPLATE_USAGE"),
            Map.entry("mcpexecution/McpExecutionRemotePort.java", "EXTERNAL_PROTOCOL_RESULT"),
            Map.entry("project/ProjectMcpUpdatePreparationPort.java", "OPEN_SCHEMA_ENRICHMENT"),
            Map.entry("project/ProjectResourceCredentialResolutionPort.java", "CREDENTIAL_ENVELOPE"),
            Map.entry("project/ProjectResourcePreparationPort.java", "OPEN_SCHEMA_ENRICHMENT"),
            Map.entry("project/ProjectWorkspaceQueryPort.java", "QUERY_READ_MODEL"),
            Map.entry("skill/SkillRuntimeUsagePort.java", "QUERY_RUNTIME_USAGE"),
            Map.entry("toolset/LocalMySqlExecutionPort.java", "EXTERNAL_ROW_SET"),
            Map.entry("toolset/ToolExecutionPort.java", "OPEN_TOOL_RESULT"),
            Map.entry("worksession/WorkSessionLifecyclePort.java", "QUERY_CAPABILITIES"));

    @Test
    void onlyExplicitQueryOrProtocolPortsMayReturnDynamicResults() throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = projectRoot().resolve(APPLICATION_ROOT);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Port.java"))
                    .sorted()
                    .toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (ALLOWED_DYNAMIC_RESULT_PORTS.containsKey(relative)) continue;
                for (String line : Files.readAllLines(file)) {
                    String normalized = line.trim().replaceAll("\\s+", " ");
                    if (DYNAMIC_RETURN.matcher(normalized).find()) {
                        violations.add(relative + " -> " + normalized);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Unclassified dynamic Application Port results:\n"
                + String.join("\n", violations));
    }

    @Test
    void everyDynamicResultExceptionRemainsConcreteAndCategorized() throws IOException {
        Path root = projectRoot().resolve(APPLICATION_ROOT);
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> exception : ALLOWED_DYNAMIC_RESULT_PORTS.entrySet()) {
            Path file = root.resolve(exception.getKey());
            boolean hasDynamicReturn = Files.isRegularFile(file)
                    && Files.readAllLines(file).stream()
                    .map(String::trim)
                    .map(line -> line.replaceAll("\\s+", " "))
                    .anyMatch(line -> DYNAMIC_RETURN.matcher(line).find());
            if (exception.getValue().isBlank() || !hasDynamicReturn) {
                stale.add(exception.getKey() + " -> " + exception.getValue());
            }
        }
        assertTrue(stale.isEmpty(), () -> "Stale dynamic result exceptions:\n" + String.join("\n", stale));
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
