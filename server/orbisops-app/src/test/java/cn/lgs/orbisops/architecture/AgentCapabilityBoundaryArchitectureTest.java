package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String CAPABILITY = TRIGGER + "application/agentdefinition/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/agentdefinition/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/";
    private static final String LEGACY_APPLICATION = TRIGGER
            + "application/ops/OpsAgentDefinitionApplicationService.java";

    @Test
    void lifecycleFacadeMustConsumeManagementAssemblyAndKeepCapabilityRulesBehindDedicatedServices() throws IOException {
        String source = read(LEGACY_APPLICATION);
        String assembly = read(CAPABILITY + "OpsAgentDefinitionManagementAssembly.java");
        String draftAdapter = read(CAPABILITY + "OpsAgentDefinitionDraftSaveAdapter.java");
        String bindingAdapter = read(CAPABILITY + "OpsAgentDefinitionBindingUpdateAdapter.java");
        String cloneAdapter = read(CAPABILITY + "OpsAgentDefinitionCloneAdapter.java");

        assertAll(
                () -> assertTrue(source.contains("OpsAgentDefinitionApplicationService(")),
                () -> assertTrue(source.contains("OpsAgentDefinitionManagementAssembly assembly")),
                () -> assertTrue(source.contains("private final OpsAgentCapabilityApplicationService capabilityApplicationService;")),
                () -> assertTrue(source.contains("this.capabilityApplicationService = assembly.capabilityService()")),
                () -> assertTrue(source.contains("capabilityApplicationService.projectCapabilities(projectId)")),
                () -> assertTrue(source.contains("capabilityApplicationService.validate(definition)")),
                () -> assertTrue(source.contains("capabilityApplicationService.requestBindings(request)")),
                () -> assertTrue(assembly.contains("OpsAgentCapabilityApplicationService capabilityService")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionDraftSaveAdapter")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionBindingUpdateAdapter")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionCloneAdapter")),
                () -> assertTrue(draftAdapter.contains("capabilities.requireExistingProject(definition.getProjectId())")),
                () -> assertTrue(draftAdapter.contains("capabilities.assertValid(definition)")),
                () -> assertTrue(bindingAdapter.contains("capabilities.applyBindings(definition, bindings)")),
                () -> assertTrue(bindingAdapter.contains("capabilities.assertValid(definition)")),
                () -> assertTrue(cloneAdapter.contains("capabilities.sanitizeForProject(definition, projectId)")),
                () -> assertFalse(source.contains("ProjectMcpCatalogApplicationService")),
                () -> assertFalse(source.contains("ProjectKnowledgeAuthorizationApplicationService")),
                () -> assertFalse(source.contains("ProjectMcpAuthorizationApplicationService")),
                () -> assertFalse(source.contains("ProjectSkillAuthorizationApplicationService")),
                () -> assertFalse(source.contains("ExecutionResourceQueryApplicationService")),
                () -> assertFalse(source.contains("private Map<String, Object> validateDefinitionBindings")),
                () -> assertFalse(source.contains("private void applyBindings")),
                () -> assertFalse(source.contains("private void sanitizeCapabilitiesForProject")),
                () -> assertTrue(source.lines().count() < 500));
    }

    @Test
    void capabilityApplicationMustBeThinAndDelegateSingleReasonComponents() throws IOException {
        String application = read(CAPABILITY + "OpsAgentCapabilityApplicationService.java");
        String catalog = read(CAPABILITY + "OpsAgentCapabilityCatalogService.java");
        String policy = read(CAPABILITY + "OpsAgentCapabilityBindingPolicy.java");
        String authorizationAdapter = read(
                CAPABILITY + "OpsAgentCapabilityAuthorizationAdapter.java");
        String validationUseCase = read(
                APPLICATION + "AgentCapabilityBindingValidationUseCase.java");
        String validationResult = read(
                APPLICATION + "AgentCapabilityBindingValidationResult.java");
        String authorizationPort = read(
                APPLICATION + "AgentCapabilityAuthorizationPort.java");
        String sanitizationUseCase = read(
                APPLICATION + "AgentCapabilitySanitizationUseCase.java");
        String catalogPort = read(
                APPLICATION + "AgentCapabilityCatalogPort.java");
        String domainPolicy = read(
                DOMAIN + "service/AgentCapabilityBindingPolicy.java");
        String sanitizationPolicy = read(
                DOMAIN + "service/AgentCapabilitySanitizationPolicy.java");
        String referenceSet = read(
                DOMAIN + "model/AgentCapabilityReferenceSet.java");
        String catalogAdapter = read(
                CAPABILITY + "OpsAgentCapabilityCatalogAdapter.java");
        String sanitizationMapper = read(
                CAPABILITY + "OpsAgentCapabilitySanitizationMapper.java");
        String editor = read(CAPABILITY + "OpsAgentCapabilityBindingEditor.java");

        assertAll(
                () -> assertTrue(application.contains("OpsAgentCapabilityCatalogService")),
                () -> assertTrue(application.contains("OpsAgentCapabilityBindingPolicy")),
                () -> assertTrue(application.contains("OpsAgentCapabilityBindingEditor")),
                () -> assertTrue(application.contains("OpsAgentCapabilityBindingMapper")),
                () -> assertTrue(application.lines().count() < 100),
                () -> assertTrue(catalog.contains("ProjectMcpCatalogApplicationService")),
                () -> assertTrue(catalog.contains("ExecutionResourceQueryApplicationService")),
                () -> assertFalse(catalog.contains("OpsAgentDefinitionGateway")),
                () -> assertTrue(catalog.lines().count() < 250),
                () -> assertTrue(policy.contains("AgentCapabilityBindingValidationUseCase validationUseCase")),
                () -> assertTrue(policy.contains("AgentCapabilitySanitizationUseCase sanitizationUseCase")),
                () -> assertTrue(policy.contains("validationUseCase.validate(bindingMapper.mapForValidation(definition))")),
                () -> assertTrue(policy.contains("sanitizationUseCase.sanitize(")),
                () -> assertTrue(policy.contains("sanitizationMapper.request(definition, projectId)")),
                () -> assertTrue(policy.contains("AgentCapabilityBindingValidationResult")),
                () -> assertFalse(policy.contains("AgentCapabilityType.INLINE_MCP_SERVER")),
                () -> assertFalse(policy.contains("capabilityRefs(")),
                () -> assertFalse(policy.contains("inlineOwner(")),
                () -> assertFalse(policy.contains("authorizedSkills(")),
                () -> assertFalse(policy.contains("authorizedMcps(")),
                () -> assertFalse(policy.contains("authorizedTargets(")),
                () -> assertFalse(policy.contains("firstAuthorizedKnowledgeBaseId(")),
                () -> assertFalse(policy.contains("isReactNode(")),
                () -> assertFalse(policy.contains("ProjectMcpCatalogApplicationService")),
                () -> assertTrue(policy.lines().count() < 150),
                () -> assertTrue(authorizationPort.contains("public interface AgentCapabilityAuthorizationPort")),
                () -> assertTrue(authorizationAdapter.contains("implements AgentCapabilityAuthorizationPort")),
                () -> assertTrue(authorizationAdapter.contains("executionTargetEnabled(")),
                () -> assertTrue(validationUseCase.contains("public final class AgentCapabilityBindingValidationUseCase")),
                () -> assertTrue(validationUseCase.contains("authorizationPort.skillAllowed(")),
                () -> assertTrue(validationUseCase.contains("authorizationPort.projectToolAllowed(")),
                () -> assertTrue(validationUseCase.contains("authorizationPort.knowledgeBaseAllowed(")),
                () -> assertTrue(validationUseCase.contains("authorizationPort.executionTargetEnabled(")),
                () -> assertTrue(validationUseCase.contains("不允许使用内联 MCP 配置")),
                () -> assertFalse(validationUseCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(validationResult.contains("public record AgentCapabilityBindingValidationResult(")),
                () -> assertTrue(catalogPort.contains("public interface AgentCapabilityCatalogPort")),
                () -> assertTrue(catalogAdapter.contains("implements AgentCapabilityCatalogPort")),
                () -> assertTrue(catalogAdapter.contains("knowledgeService.enabledIds(projectId)")),
                () -> assertTrue(sanitizationUseCase.contains("public final class AgentCapabilitySanitizationUseCase")),
                () -> assertTrue(sanitizationUseCase.contains("catalogPort.resolve(")),
                () -> assertTrue(sanitizationUseCase.contains("sanitizationPolicy.sanitize(")),
                () -> assertFalse(sanitizationUseCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(sanitizationMapper.contains("new AgentCapabilitySanitizationRequest(")),
                () -> assertTrue(sanitizationMapper.contains("decision.forceRagEnabled()")),
                () -> assertTrue(sanitizationMapper.contains("node.setRagEnabled(true)")),
                () -> assertTrue(domainPolicy.contains("public final class AgentCapabilityBindingPolicy")),
                () -> assertTrue(domainPolicy.contains("AgentCapabilityType.INLINE_MCP_SERVER")),
                () -> assertTrue(domainPolicy.contains("inlineOwner(")),
                () -> assertFalse(domainPolicy.contains("org.springframework")),
                () -> assertFalse(domainPolicy.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(sanitizationPolicy.contains("public final class AgentCapabilitySanitizationPolicy")),
                () -> assertTrue(sanitizationPolicy.contains("catalog.primaryKnowledgeBaseId()")),
                () -> assertTrue(sanitizationPolicy.contains("selection.enableRagWhenNoProjectTool()")),
                () -> assertFalse(sanitizationPolicy.contains("org.springframework")),
                () -> assertFalse(sanitizationPolicy.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(referenceSet.contains("public record AgentCapabilityReferenceSet(")),
                () -> assertTrue(editor.contains("AgentCapabilityOwnerType.require")),
                () -> assertTrue(editor.contains("AgentCapabilityType.require")),
                () -> assertTrue(editor.contains("不允许通过绑定接口写入内联 MCP 配置")),
                () -> assertFalse(editor.contains("ProjectDefinitionApplicationService")),
                () -> assertFalse(editor.contains("ExecutionResourceQueryApplicationService")),
                () -> assertTrue(editor.lines().count() < 260));
    }

    @Test
    void oldCapabilityImplementationMustNotReturnToLifecycleService() throws IOException {
        String source = read(LEGACY_APPLICATION);

        assertAll(
                () -> assertFalse(source.contains("collectSkillRefs")),
                () -> assertFalse(source.contains("collectMcpRefs")),
                () -> assertFalse(source.contains("collectKnowledgeRefs")),
                () -> assertFalse(source.contains("collectExecutionTargetRefs")),
                () -> assertFalse(source.contains("capabilityMaps(")),
                () -> assertFalse(source.contains("knowledgeCapability(")),
                () -> assertFalse(source.contains("validateBindingRequestShape")),
                () -> assertFalse(source.contains("uniqueAppend(")));
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
