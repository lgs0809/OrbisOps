package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Executable registry for every Application-to-Application relationship that
 * crosses a bounded-context boundary. New dependencies require an explicit
 * Context Map decision instead of silently expanding package access.
 */
class CrossContextApplicationServiceDependencyArchitectureTest {

    private static final String APPLICATION_ROOT =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application";
    private static final String APPLICATION_IMPORT = "import cn.lgs.orbisops.application.";

    private static final Set<String> ALLOWED_IMPORTS = Set.of(
            "agentdefinition/ProjectDefaultAgentBootstrapUseCase.java -> agenteval.AgentEvalCreateSuiteCommand",
            "agentdefinition/ProjectDefaultAgentEvalPort.java -> agenteval.AgentEvalCreateSuiteCommand",
            "agentdefinition/ProjectDefaultAgentEvalSuiteFactory.java -> agenteval.AgentEvalCreateSuiteCommand",
            "changepackage/ChangePackageLandingProcessManager.java -> toolexecution.ToolExecutionIdempotencyPort",
            "mcp/ManageMcpTemplateUseCase.java -> project.ProjectWorkspaceProjectionPort",
            "project/ProjectKnowledgeAuthorizationApplicationService.java -> knowledge.KnowledgeAuthorizationQueryPort",
            "project/ProjectKnowledgeAuthorizationApplicationService.java -> knowledge.KnowledgeWorkspaceCatalogPort",
            "project/ProjectMcpManagementApplicationService.java -> mcp.McpReviewedToolPolicySnapshot",
            "project/ProjectMcpReviewedPolicyPort.java -> mcp.McpReviewedToolPolicySnapshot",
            "project/ProjectProductReadinessApplicationService.java -> capability.CapabilityDependencyReadiness",
            "project/ProjectProductReadinessApplicationService.java -> capability.CapabilityReadinessSnapshot",
            "project/ProjectSkillAuthorizationApplicationService.java -> skill.SkillAuthorizationCatalogPort"
    );

    @Test
    void everyCrossContextApplicationImportMustBeExplicitlyRegistered() throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        Path root = projectRoot().resolve(APPLICATION_ROOT);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                collect(root, file, actual);
            }
        }
        assertEquals(ALLOWED_IMPORTS, actual,
                "Cross-context Application imports must be narrow Ports or reviewed immutable Published Language");
    }

    private void collect(Path root, Path file, Set<String> actual) throws IOException {
        String sourcePackage = root.relativize(file).getName(0).toString();
        String sourceContext = contextOf(sourcePackage);
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(APPLICATION_IMPORT)) continue;
            String imported = trimmed.substring(APPLICATION_IMPORT.length()).replace(";", "");
            int separator = imported.indexOf('.');
            if (separator < 0) continue;
            String targetContext = contextOf(imported.substring(0, separator));
            if (sourceContext.equals(targetContext)) continue;
            actual.add(root.relativize(file) + " -> " + imported);
        }
    }

    private String contextOf(String applicationPackage) {
        return contextMap().getOrDefault(applicationPackage, applicationPackage);
    }

    private Map<String, String> contextMap() {
        Map<String, String> contexts = new LinkedHashMap<>();
        register(contexts, "project-workspace", "project", "source");
        register(contexts, "agent-definition", "agentdefinition", "agent", "capability", "schedule");
        register(contexts, "work-session", "worksession", "runtime", "analysis", "intent", "chat",
                "chatsession", "resourcehealth");
        register(contexts, "controlled-change", "changepackage");
        register(contexts, "execution-governance", "execution", "toolexecution", "mcpexecution", "mcp", "toolset");
        register(contexts, "evidence-trust", "evidence", "audit");
        register(contexts, "knowledge-rag", "knowledge", "rag");
        register(contexts, "skill-lifecycle", "skill");
        register(contexts, "memory", "memory");
        register(contexts, "channel", "channel");
        register(contexts, "alert-incident", "alert", "incident", "statistics", "agenteval", "modelpolicy", "config");
        register(contexts, "repair-sandbox", "repair", "sandbox");
        register(contexts, "security", "security");
        return Map.copyOf(contexts);
    }

    private void register(Map<String, String> contexts, String context, String... packages) {
        for (String applicationPackage : packages) contexts.put(applicationPackage, context);
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
