package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionExecutionShapeBoundaryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/";
    private static final String TRIGGER_AGENT_DEFINITION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/agentdefinition/";
    private static final String LEGACY_APPLICATION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsAgentDefinitionApplicationService.java";

    @Test
    void executionShapeRulesAndViewProjectionMustRemainOutsideLifecycleOrchestrator()
            throws IOException {
        String domainPolicy = read(DOMAIN + "service/AgentExecutionShapePolicy.java");
        String nodeFact = read(DOMAIN + "model/AgentExecutionNodeFact.java");
        String decision = read(DOMAIN + "model/AgentExecutionShapeDecision.java");
        String shapeMapper = read(TRIGGER_AGENT_DEFINITION
                + "OpsAgentDefinitionExecutionShapeMapper.java");
        String viewMapper = read(TRIGGER_AGENT_DEFINITION
                + "OpsAgentDefinitionViewMapper.java");
        String assembly = read(TRIGGER_AGENT_DEFINITION
                + "OpsAgentDefinitionManagementAssembly.java");
        String draftAdapter = read(TRIGGER_AGENT_DEFINITION
                + "OpsAgentDefinitionDraftSaveAdapter.java");
        String bindingAdapter = read(TRIGGER_AGENT_DEFINITION
                + "OpsAgentDefinitionBindingUpdateAdapter.java");
        String cloneAdapter = read(TRIGGER_AGENT_DEFINITION
                + "OpsAgentDefinitionCloneAdapter.java");
        String application = read(LEGACY_APPLICATION);

        assertAll(
                () -> assertTrue(domainPolicy.contains("public final class AgentExecutionShapePolicy")),
                () -> assertTrue(domainPolicy.contains("hasReactNode ? \"HYBRID\" : \"GRAPH\"")),
                () -> assertTrue(domainPolicy.contains("hasReactNode ? \"HYBRID\" : \"GRAPH\",")),
                () -> assertTrue(domainPolicy.contains("true);")),
                () -> assertFalse(domainPolicy.contains("OpsAgentDefinition")),
                () -> assertFalse(domainPolicy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domainPolicy.contains("org.springframework")),
                () -> assertTrue(nodeFact.contains("public record AgentExecutionNodeFact(")),
                () -> assertTrue(decision.contains("public record AgentExecutionShapeDecision(")),
                () -> assertTrue(shapeMapper.contains("AgentExecutionShapePolicy domainPolicy")),
                () -> assertTrue(shapeMapper.contains("definition.setEngine(decision.engine())")),
                () -> assertTrue(shapeMapper.contains("definition.setAgentscopeAgents(List.of())")),
                () -> assertTrue(viewMapper.contains("public final class OpsAgentDefinitionViewMapper")),
                () -> assertTrue(viewMapper.contains("executionShapeMapper.inferEngine(definition)")),
                () -> assertTrue(viewMapper.contains("workflowNodeView")),
                () -> assertTrue(viewMapper.contains("graphEdgeView")),
                () -> assertTrue(viewMapper.contains("loopPolicyView")),
                () -> assertTrue(assembly.contains("OpsAgentDefinitionExecutionShapeMapper shapeMapper")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionExecutionShapeMapper()")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionViewMapper()")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionDraftSaveAdapter")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionBindingUpdateAdapter")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionCloneAdapter")),
                () -> assertTrue(draftAdapter.contains("executionShapeMapper.normalize(definition)")),
                () -> assertTrue(bindingAdapter.contains("executionShapeMapper.normalize(definition)")),
                () -> assertTrue(cloneAdapter.contains("executionShapeMapper.normalize(definition)")),
                () -> assertTrue(application.contains("OpsAgentDefinitionViewMapper viewMapper")),
                () -> assertTrue(application.contains("this.viewMapper = assembly.viewMapper()")),
                () -> assertTrue(application.contains("return viewMapper.view(definition)")),
                () -> assertFalse(application.contains("OpsAgentDefinitionExecutionShapeMapper executionShapeMapper")),
                () -> assertFalse(application.contains("executionShapeMapper.normalize(")),
                () -> assertFalse(application.contains("private Map<String, Object> workflowNodeView(")),
                () -> assertFalse(application.contains("private Map<String, Object> graphEdgeView(")),
                () -> assertFalse(application.contains("private Map<String, Object> loopPolicyView(")),
                () -> assertFalse(application.contains("private OpsAgentDefinition normalizeExecutionShape(")),
                () -> assertFalse(application.contains("private String inferEngine(")),
                () -> assertFalse(application.contains("private boolean isReactNode(")));
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
