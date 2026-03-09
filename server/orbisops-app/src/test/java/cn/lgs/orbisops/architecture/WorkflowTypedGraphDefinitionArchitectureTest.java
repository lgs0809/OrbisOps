package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTypedGraphDefinitionArchitectureTest {

    private static final String DOMAIN_MODEL = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/model/";
    private static final String DOMAIN_SERVICE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/service/";
    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void authoritativeGraphMustPublishNamedTypedSubmodels() throws IOException {
        String graph = read(DOMAIN_MODEL + "AgentGraphDefinition.java");
        String definition = read(DOMAIN_MODEL + "AgentWorkflowDefinition.java");
        String node = read(DOMAIN_MODEL + "AgentWorkflowNodeDefinition.java");
        String edge = read(DOMAIN_MODEL + "AgentWorkflowEdgeDefinition.java");
        String type = read(DOMAIN_MODEL + "AgentWorkflowNodeType.java");
        String route = read(DOMAIN_MODEL + "AgentWorkflowRouteMode.java");
        String rule = read(DOMAIN_MODEL + "AgentWorkflowRuleExpression.java");
        String resource = read(DOMAIN_MODEL + "AgentWorkflowResourceReference.java");

        assertAll(
                () -> assertTrue(graph.contains("workflowNodes()")),
                () -> assertTrue(graph.contains("workflowEdges()")),
                () -> assertTrue(definition.contains("int schemaVersion")),
                () -> assertTrue(definition.contains("int definitionVersion")),
                () -> assertTrue(definition.contains("String definitionHash")),
                () -> assertTrue(definition.contains("AgentGraphDefinition graph")),
                () -> assertTrue(node.contains("public record AgentWorkflowNodeDefinition(")),
                () -> assertTrue(edge.contains("public record AgentWorkflowEdgeDefinition(")),
                () -> assertTrue(type.contains("PARALLEL")),
                () -> assertTrue(type.contains("COMPENSATION")),
                () -> assertTrue(route.contains("REVIEW_DECISION")),
                () -> assertTrue(rule.contains("AgentWorkflowRouteMode mode")),
                () -> assertTrue(resource.contains("EXECUTION_TARGET")),
                () -> assertTrue(resource.contains("INLINE_MCP")),
                () -> assertFalse(node.contains("com.fasterxml")),
                () -> assertFalse(edge.contains("org.springframework")));
    }

    @Test
    void enabledSubsetMustBeExplicitAndFutureTypesMustRemainDisabled() throws IOException {
        String policy = read(DOMAIN_SERVICE + "AgentWorkflowNodeTypePolicy.java");
        String nodePolicy = read(DOMAIN_SERVICE + "AgentNodeDefinitionPolicy.java");

        assertAll(
                () -> assertTrue(policy.contains("ENABLED_TYPES = Set.of(")),
                () -> assertTrue(policy.contains("AgentWorkflowNodeType.SUB_WORKFLOW")),
                () -> assertFalse(policy.contains("AgentWorkflowNodeType.PARALLEL,")),
                () -> assertFalse(policy.contains("AgentWorkflowNodeType.JOIN,")),
                () -> assertFalse(policy.contains("AgentWorkflowNodeType.WAIT,")),
                () -> assertFalse(policy.contains("AgentWorkflowNodeType.COMPENSATION,")),
                () -> assertTrue(policy.contains("WORKFLOW_NODE_TYPE_NOT_ENABLED")),
                () -> assertTrue(nodePolicy.contains("workflowTypePolicy.validateEnabled(node.workflowDefinition())")),
                () -> assertFalse(nodePolicy.contains("NODE_TYPES = Set.of")));
    }

    @Test
    void inboundMapperMustBeTheAclForOpenRuntimeDtos() throws IOException {
        String mapper = read(RUNTIME + "OpsAgentGraphDefinitionMapper.java");
        String compiler = read(RUNTIME + "OpsAgentGraphCompilerAdapter.java");

        assertAll(
                () -> assertTrue(mapper.contains("AgentWorkflowNodeDefinition workflowNode(")),
                () -> assertTrue(mapper.contains("AgentWorkflowEdgeDefinition workflowEdge(")),
                () -> assertTrue(mapper.contains("ResourceType.MODEL")),
                () -> assertTrue(mapper.contains("ResourceType.KNOWLEDGE_BASE")),
                () -> assertTrue(mapper.contains("ResourceType.SKILL")),
                () -> assertTrue(mapper.contains("ResourceType.MCP")),
                () -> assertTrue(mapper.contains("ResourceType.EXECUTION_TARGET")),
                () -> assertTrue(mapper.contains("ResourceType.INLINE_MCP")),
                () -> assertTrue(compiler.contains("definitionMapper.workflowNode(node)")),
                () -> assertTrue(compiler.contains("nodeTypePolicy.validateEnabled(typed)")),
                () -> assertTrue(compiler.contains("definitionMapper.workflowEdge(edge)")),
                () -> assertFalse(compiler.contains("return \"CHAT\";")));
    }

    @Test
    void legacyMigrationMustBeCentralizedAndSchemaVersionMustParticipateInHash() throws IOException {
        String migrator = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/agentdefinition/AgentWorkflowDefinitionMigrator.java");
        String mutationAdapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionMutationAdapter.java");
        String definitionDto = read(RUNTIME + "OpsAgentDefinition.java");
        String snapshotMapper = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionSnapshotMapper.java");

        assertAll(
                () -> assertTrue(migrator.contains("class AgentWorkflowDefinitionMigrator")),
                () -> assertTrue(migrator.contains("migrateLegacyV0")),
                () -> assertTrue(migrator.contains("normalizeV1")),
                () -> assertTrue(migrator.contains("WORKFLOW_SCHEMA_VERSION_UNSUPPORTED")),
                () -> assertTrue(mutationAdapter.contains("workflowMigrator.migrate(definition)")),
                () -> assertFalse(mutationAdapter.contains("normalizeNodeType(")),
                () -> assertFalse(mutationAdapter.contains("inferNodeMode(")),
                () -> assertTrue(definitionDto.contains("private Integer schemaVersion;")),
                () -> assertTrue(snapshotMapper.contains("OpsRuntimeHashing.canonicalHash(hashInput)")),
                () -> assertFalse(snapshotMapper.contains("hashInput.setSchemaVersion(null)")));
    }

    @Test
    void normalizedSnapshotMustRecoverTypedLanguageWithoutSchemaFork() throws IOException {
        String snapshot = read(DOMAIN_MODEL + "AgentDefinitionNormalizedGraphSnapshot.java");

        assertAll(
                () -> assertTrue(snapshot.contains("AgentWorkflowNodeDefinition workflowDefinition()")),
                () -> assertTrue(snapshot.contains("AgentWorkflowEdgeDefinition workflowDefinition()")),
                () -> assertTrue(snapshot.contains("AgentWorkflowNodeType.fromPublishedName")),
                () -> assertTrue(snapshot.contains("AgentWorkflowRouteMode.fromPublishedName")),
                () -> assertFalse(snapshot.contains("typed_nodes_json")),
                () -> assertFalse(snapshot.contains("workflow_graph_v2")));
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
