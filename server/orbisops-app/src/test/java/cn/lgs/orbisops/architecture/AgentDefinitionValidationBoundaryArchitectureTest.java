package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionValidationBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/agentdefinition/";

    @Test
    void validatorMustKeepThreeDependencyConstructorAndDelegateStructuralCompilation() throws IOException {
        String validator = read(RUNTIME + "OpsAgentDefinitionValidator.java");
        String compiler = read(RUNTIME + "OpsAgentWorkflowStructuralCompiler.java");

        assertAll(
                () -> assertTrue(validator.contains(
                        "private final OpsAgentWorkflowStructuralCompiler structuralCompiler;")),
                () -> assertEquals(1, occurrences(
                        validator, "public OpsAgentDefinitionValidator(")),
                () -> assertTrue(validator.contains(
                        "OpsAgentGraphDefinitionPolicy graphDefinitionPolicy")),
                () -> assertTrue(validator.contains(
                        "OpsAgentScopeDefinitionPolicy agentScopeDefinitionPolicy")),
                () -> assertTrue(validator.contains(
                        "OpsAgentDefinitionResourceValidator resourceValidator")),
                () -> assertTrue(validator.contains("return structuralCompiler.compile(definition);")),
                () -> assertTrue(compiler.contains("new TopologyValidationStage(graphPolicy.domainPolicy())")),
                () -> assertTrue(compiler.contains("resourceValidator.validate(definition)")),
                () -> assertTrue(compiler.contains("agentScopePolicy.validate(definition)")),
                () -> assertFalse(validator.contains("@Autowired")),
                () -> assertFalse(validator.contains("ObjectProvider")),
                () -> assertFalse(validator.contains("Repository")),
                () -> assertFalse(validator.contains("ApplicationService")),
                () -> assertFalse(validator.contains("OpsSkillToolProvider")),
                () -> assertFalse(validator.contains("OpsSourceRepositoryService")),
                () -> assertFalse(validator.contains("normalizeAnalysisSource")),
                () -> assertFalse(validator.contains("routeConditionSource")),
                () -> assertTrue(validator.lines().count() < 45));
    }

    @Test
    void graphValidationRulesMustLiveInPureAgentDefinitionDomain() throws IOException {
        String domainModel = read(DOMAIN + "model/AgentGraphDefinition.java");
        String domainGraph = read(DOMAIN + "service/AgentGraphDefinitionPolicy.java");
        String domainNode = read(DOMAIN + "service/AgentNodeDefinitionPolicy.java");
        String domainTopology = read(DOMAIN + "service/AgentGraphTopologyPolicy.java");
        String triggerGraph = read(RUNTIME + "OpsAgentGraphDefinitionPolicy.java");
        String triggerNode = read(RUNTIME + "OpsAgentNodeDefinitionPolicy.java");
        String triggerTopology = read(RUNTIME + "OpsAgentGraphTopologyPolicy.java");
        String mapper = read(RUNTIME + "OpsAgentGraphDefinitionMapper.java");

        assertAll(
                () -> assertTrue(domainModel.contains("public record AgentGraphDefinition(")),
                () -> assertTrue(domainModel.contains("public record Node(")),
                () -> assertTrue(domainModel.contains("public record Edge(")),
                () -> assertTrue(domainModel.contains("public record Loop(")),
                () -> assertTrue(domainGraph.contains("AgentNodeDefinitionPolicy nodePolicy")),
                () -> assertTrue(domainGraph.contains("AgentGraphTopologyPolicy topologyPolicy")),
                () -> assertTrue(domainGraph.contains("nodePolicy.validate(node);")),
                () -> assertTrue(domainTopology.contains("validateGraphStructure")),
                () -> assertTrue(domainTopology.contains("validateLoops")),
                () -> assertTrue(domainTopology.contains("traverse(")),
                () -> assertTrue(domainNode.contains("requiresExplicitRuntimeCapability")),
                () -> assertTrue(domainNode.contains("ChangePackage 工具")),
                () -> assertFalse(domainModel.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domainGraph.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domainNode.contains("org.springframework")),
                () -> assertFalse(domainTopology.contains("org.springframework")),
                () -> assertFalse(domainModel.contains("lombok")),
                () -> assertFalse(domainModel.contains("com.fasterxml.jackson")),
                () -> assertTrue(triggerGraph.contains("AgentGraphDefinitionPolicy domainPolicy")),
                () -> assertTrue(triggerGraph.contains("domainPolicy.validate(mapper.map(definition))")),
                () -> assertTrue(triggerNode.contains("AgentNodeDefinitionPolicy domainPolicy")),
                () -> assertTrue(triggerTopology.contains("AgentGraphTopologyPolicy domainPolicy")),
                () -> assertTrue(mapper.contains("new AgentGraphDefinition(")),
                () -> assertFalse(triggerGraph.contains("ENGINES = Set.of")),
                () -> assertFalse(triggerNode.contains("NODE_TYPES = Set.of")),
                () -> assertFalse(triggerTopology.contains("EDGE_CONDITION_TYPES")),
                () -> assertFalse(triggerTopology.contains("validateGraphStructure")),
                () -> assertFalse(triggerTopology.contains("traverse(")),
                () -> assertTrue(triggerGraph.lines().count() < 45),
                () -> assertTrue(triggerNode.lines().count() < 50),
                () -> assertTrue(triggerTopology.lines().count() < 70));
    }

    @Test
    void structuralScopeAndInlineMcpRulesMustBeDomainWhileExternalReferencesRemainAdapters() throws IOException {
        String resources = read(RUNTIME + "OpsAgentDefinitionResourceValidator.java");
        String resourceUseCase = read(
                APPLICATION + "AgentDefinitionResourceValidationUseCase.java");
        String resourceRequest = read(
                APPLICATION + "AgentDefinitionResourceValidationRequest.java");
        String capabilities = read(
                RUNTIME + "OpsAgentProjectCapabilityReferenceValidator.java");
        String modelKnowledge = read(
                RUNTIME + "OpsAgentModelKnowledgeReferenceValidator.java");
        String domainMcp = read(DOMAIN + "service/AgentMcpServerDefinitionPolicy.java");
        String domainScope = read(DOMAIN + "service/AgentScopeDefinitionPolicy.java");
        String domainToolNames = read(DOMAIN + "service/AgentToolNamePolicy.java");
        String domainRole = read(DOMAIN + "model/OpsBuiltinSubAgentRole.java");
        String triggerMcp = read(RUNTIME + "OpsAgentMcpServerDefinitionPolicy.java");
        String triggerScope = read(RUNTIME + "OpsAgentScopeDefinitionPolicy.java");
        String triggerToolNames = read(RUNTIME + "OpsAgentToolNamePolicy.java");
        String legacyRolePath = read(RUNTIME + "OpsBuiltinSubAgentRole.java");

        assertAll(
                () -> assertTrue(resourceRequest.contains("public record AgentDefinitionResourceValidationRequest(")),
                () -> assertTrue(resourceRequest.contains("public record ResourceOwner(")),
                () -> assertTrue(resourceUseCase.contains("public final class AgentDefinitionResourceValidationUseCase")),
                () -> assertTrue(resourceUseCase.contains("for (AgentDefinitionResourceValidationRequest.ResourceOwner owner")),
                () -> assertTrue(resourceUseCase.contains("inlineMcpPolicy.validate(")),
                () -> assertFalse(resourceUseCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(resources.contains("AgentDefinitionResourceValidationUseCase useCase")),
                () -> assertTrue(resources.contains("useCase.validate(mapper.resources(definition))")),
                () -> assertFalse(resources.contains("validateNodes(")),
                () -> assertFalse(resources.contains("validateAgentScopes(")),
                () -> assertFalse(resources.contains("validateOwner(")),
                () -> assertTrue(capabilities.contains("implements AgentCapabilityReferenceValidationPort")),
                () -> assertTrue(capabilities.contains("validateSkills(")),
                () -> assertTrue(capabilities.contains("validateMcpReferences(")),
                () -> assertTrue(capabilities.contains("existsEnabledAny(id)")),
                () -> assertTrue(capabilities.contains("resolveMcpServer(projectId, id)")),
                () -> assertTrue(modelKnowledge.contains("implements AgentModelKnowledgeReferenceValidationPort")),
                () -> assertTrue(modelKnowledge.contains("validateModel(")),
                () -> assertTrue(modelKnowledge.contains("validateKnowledge(")),
                () -> assertTrue(modelKnowledge.contains("queryByKnowledgeTag(id)")),
                () -> assertTrue(domainMcp.contains("streamable-http")),
                () -> assertTrue(domainMcp.contains("TOOL_CAPABILITIES")),
                () -> assertTrue(domainScope.contains("role.defaultAllowedTools()")),
                () -> assertTrue(domainToolNames.contains("[A-Za-z0-9_.:-]+")),
                () -> assertTrue(domainRole.contains("public enum OpsBuiltinSubAgentRole")),
                () -> assertFalse(domainMcp.contains("org.springframework")),
                () -> assertFalse(domainScope.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domainRole.contains("org.springframework")),
                () -> assertTrue(triggerMcp.contains("AgentMcpServerDefinitionPolicy domainPolicy")),
                () -> assertTrue(triggerScope.contains("AgentScopeDefinitionPolicy domainPolicy")),
                () -> assertTrue(triggerToolNames.contains("AgentToolNamePolicy domainPolicy")),
                () -> assertFalse(triggerMcp.contains("TRANSPORTS = Set.of")),
                () -> assertFalse(triggerScope.contains("OpsBuiltinSubAgentRole.parse")),
                () -> assertFalse(triggerToolNames.contains("matches(\"[A-Za-z0-9_.:-]+\")")),
                () -> assertFalse(legacyRolePath.contains("enum OpsBuiltinSubAgentRole")),
                () -> assertFalse(resources.contains("ObjectProvider")),
                () -> assertFalse(capabilities.contains("ObjectProvider")),
                () -> assertFalse(modelKnowledge.contains("ObjectProvider")),
                () -> assertTrue(resources.lines().count() < 55),
                () -> assertTrue(capabilities.lines().count() < 180),
                () -> assertTrue(modelKnowledge.lines().count() < 110),
                () -> assertTrue(triggerMcp.lines().count() < 40),
                () -> assertTrue(triggerScope.lines().count() < 35),
                () -> assertTrue(triggerToolNames.lines().count() < 50));
    }

    @Test
    void configurationMustBeTheOnlyOptionalDependencyOwner() throws IOException {
        String configuration = read(
                RUNTIME + "OpsAgentDefinitionReferenceConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<McpClientCatalogPort>")),
                () -> assertFalse(configuration.contains("IAiClientToolMcpConfigRepository")),
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<AiClientModelCatalogPort>")),
                () -> assertFalse(configuration.contains("IAiClientModelConfigRepository")),
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<RagOrderCatalogPort>")),
                () -> assertFalse(configuration.contains("IAiClientRagOrderConfigRepository")),
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<ProjectMcpAuthorizationApplicationService>")),
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<ProjectKnowledgeAuthorizationApplicationService>")),
                () -> assertTrue(configuration.contains(
                        "new OpsAgentProjectCapabilityReferenceValidator(")),
                () -> assertTrue(configuration.contains(
                        "new OpsAgentModelKnowledgeReferenceValidator(")),
                () -> assertTrue(configuration.lines().count() < 70));
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
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
