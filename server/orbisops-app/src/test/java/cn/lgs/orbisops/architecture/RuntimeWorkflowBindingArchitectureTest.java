package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeWorkflowBindingArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/workflow/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/runtime/workflow/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void boundPlanMustRemainFrameworkFreeAndPersistable() throws IOException {
        String plan = read(DOMAIN + "model/BoundWorkflowExecutionPlan.java");
        String resource = read(DOMAIN + "model/BoundWorkflowResourceSnapshot.java");
        String policy = read(DOMAIN + "service/BoundWorkflowPlanPolicy.java");

        assertAll(
                () -> assertTrue(plan.contains("record BoundWorkflowExecutionPlan")),
                () -> assertTrue(plan.contains("contextBundleHash")),
                () -> assertTrue(plan.contains("planHash")),
                () -> assertTrue(resource.contains("resourceVersion")),
                () -> assertTrue(resource.contains("resourceHash")),
                () -> assertTrue(policy.contains("assertResourceCompatibility")),
                () -> assertFalse(plan.contains("ToolCallback")),
                () -> assertFalse(plan.contains("ChatModel")),
                () -> assertFalse(plan.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.domain.agentdefinition")));
    }

    @Test
    void bindingPipelineMustBeOrderedAndMustNotResolveClients() throws IOException {
        String service = read(APPLICATION + "RuntimeWorkflowBindingApplicationService.java");
        String pipeline = read(APPLICATION + "RuntimeWorkflowBindingPipeline.java");
        String adapter = read(TRIGGER + "OpsRuntimeWorkflowBindingAdapter.java");

        assertAll(
                () -> assertTrue(service.contains("ACCESS_VALIDATION")),
                () -> assertTrue(service.contains("MODEL_BINDING")),
                () -> assertTrue(service.contains("TOOL_BINDING")),
                () -> assertTrue(service.contains("MCP_BINDING")),
                () -> assertTrue(service.contains("SKILL_BINDING")),
                () -> assertTrue(service.contains("KNOWLEDGE_BINDING")),
                () -> assertTrue(service.contains("MEMORY_BINDING")),
                () -> assertTrue(service.contains("POLICY_BINDING")),
                () -> assertTrue(service.contains("BOUND_PLAN_ASSEMBLY")),
                () -> assertTrue(pipeline.contains("RUNTIME_WORKFLOW_BINDING_STAGE_ORDER_DUPLICATE")),
                () -> assertFalse(service.contains("ToolCallback")),
                () -> assertFalse(service.contains("ChatModel")),
                () -> assertFalse(adapter.contains("getHeaders()")),
                () -> assertFalse(adapter.contains("getEnv()")),
                () -> assertFalse(adapter.contains("getUrl()")),
                () -> assertFalse(adapter.contains("getLandingRuntimeToken()")));
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
