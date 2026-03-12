package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSubAgentToolBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/";

    @Test
    void domainPolicyMustOwnDepthRoleAllowlistAndToolFiltering() throws IOException {
        String domainPolicy = read(DOMAIN + "service/SubAgentToolBoundaryPolicy.java");
        String decision = read(DOMAIN + "model/SubAgentToolBoundaryDecision.java");
        String runtimeAdapter = read(RUNTIME + "OpsRuntimeSubAgentToolBoundaryPolicy.java");

        assertAll(
                () -> assertTrue(domainPolicy.contains("OpsBuiltinSubAgentRole.parse(roleValue)")),
                () -> assertTrue(domainPolicy.contains("maxDepth != 1")),
                () -> assertTrue(domainPolicy.contains("role.defaultAllowedTools()")),
                () -> assertTrue(domainPolicy.contains("SUB_AGENT_DEPTH_EXCEEDED")),
                () -> assertTrue(domainPolicy.contains("SUB_AGENT_TOOL_ALLOWLIST_MISSING")),
                () -> assertTrue(domainPolicy.contains("normalized.endsWith(\"*\")")),
                () -> assertTrue(decision.contains("public record SubAgentToolBoundaryDecision(")),
                () -> assertFalse(domainPolicy.contains("org.springframework")),
                () -> assertFalse(domainPolicy.contains("ToolCallback")),
                () -> assertTrue(runtimeAdapter.contains("SubAgentToolBoundaryPolicy domainPolicy")),
                () -> assertTrue(runtimeAdapter.contains("domainPolicy.evaluate(")),
                () -> assertTrue(runtimeAdapter.contains("decision.permittedToolNames()")),
                () -> assertTrue(runtimeAdapter.contains("subAgentRole")),
                () -> assertTrue(runtimeAdapter.contains("subAgentMaxDepth")),
                () -> assertTrue(runtimeAdapter.contains("subAgentAllowedTools")),
                () -> assertTrue(runtimeAdapter.contains("subAgentToolCount")),
                () -> assertFalse(runtimeAdapter.contains("OpsBuiltinSubAgentRole.parse")),
                () -> assertFalse(runtimeAdapter.contains("SUB_AGENT_DEPTH_EXCEEDED")),
                () -> assertFalse(runtimeAdapter.contains("normalized.endsWith(\"*\")")),
                () -> assertFalse(runtimeAdapter.contains("ObjectProvider")),
                () -> assertFalse(runtimeAdapter.contains("@Autowired")),
                () -> assertFalse(runtimeAdapter.contains("@Value")),
                () -> assertTrue(runtimeAdapter.lines().count() < 70));
    }

    @Test
    void pipelineMustDelegateAndAssemblerMustNeverReclaimSubAgentSecurityLogic() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeSubAgentToolBoundaryPolicy subAgentToolBoundaryPolicy")),
                () -> assertTrue(pipeline.contains(
                        "rule(\"SUB_AGENT_TOOL_BOUNDARY\", subAgentToolBoundaryPolicy::enforce)")),
                () -> assertEquals(1, occurrences(
                        assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeSubAgentToolBoundaryPolicy")),
                () -> assertFalse(assembler.contains("OpsBuiltinSubAgentRole.parse")),
                () -> assertFalse(assembler.contains("SUB_AGENT_DEPTH_EXCEEDED")),
                () -> assertFalse(assembler.contains("SUB_AGENT_TOOL_ALLOWLIST_MISSING")),
                () -> assertFalse(assembler.contains("private boolean allowedTool(")),
                () -> assertFalse(assembler.contains("role.defaultAllowedTools()")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void policyMustBeAConcreteTypedComponentWithoutCompatibilityConstructors() throws IOException {
        String policy = read(RUNTIME + "OpsRuntimeSubAgentToolBoundaryPolicy.java");

        assertAll(
                () -> assertTrue(policy.contains("@Component")),
                () -> assertEquals(0, occurrences(
                        policy, "public OpsRuntimeSubAgentToolBoundaryPolicy(")),
                () -> assertEquals(1, occurrences(policy, "public void enforce(")));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
