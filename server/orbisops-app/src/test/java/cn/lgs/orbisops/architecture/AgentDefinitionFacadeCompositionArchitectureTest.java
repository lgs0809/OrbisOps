package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionFacadeCompositionArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/";

    @Test
    void facadeMustRemainPureGatewayWithoutServiceLocatorOrObjectGraphConstruction() throws IOException {
        String facade = read(ROOT + "OpsAgentDefinitionApplicationFacade.java");

        assertAll(
                () -> assertTrue(facade.contains(
                        "OpsAgentDefinitionApplicationFacade(")),
                () -> assertTrue(facade.contains("OpsAgentDefinitionRuntimeSettings runtimeSettings")),
                () -> assertFalse(facade.contains("ObjectProvider")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("IAgentDefinitionRepository")),
                () -> assertFalse(facade.contains("IAgentDefinitionGraphRepository")),
                () -> assertFalse(facade.contains("IAgentCapabilityBindingRepository")),
                () -> assertFalse(facade.contains("ProjectDefinitionApplicationService")),
                () -> assertFalse(facade.contains("new AgentDefinitionMemoryCatalog")),
                () -> assertFalse(facade.contains("new OpsAgentDefinitionMutationAdapter")),
                () -> assertFalse(facade.contains("new AgentDefinitionMutationService")),
                () -> assertFalse(facade.contains("new AgentDefinitionQueryService")),
                () -> assertTrue(facade.lines().count() < 270));
    }

    @Test
    void configurationMustOwnOptionalBeanResolutionAndAssemblyCreation() throws IOException {
        String configuration = read(ROOT + "OpsAgentDefinitionApplicationConfiguration.java");
        String assembly = read(ROOT + "OpsAgentDefinitionApplicationAssembly.java");

        assertAll(
                () -> assertTrue(configuration.contains("ObjectProvider<IAgentDefinitionRepository>")),
                () -> assertTrue(configuration.contains("ObjectProvider<IAgentDefinitionGraphRepository>")),
                () -> assertTrue(configuration.contains("ObjectProvider<IAgentCapabilityBindingRepository>")),
                () -> assertTrue(configuration.contains("ObjectProvider<ProjectDefinitionApplicationService>")),
                () -> assertTrue(configuration.contains("OpsAgentDefinitionApplicationAssembly.create(")),
                () -> assertTrue(configuration.contains("projectDefinitionServiceProvider::getIfAvailable")),
                () -> assertTrue(assembly.contains("record OpsAgentDefinitionApplicationAssembly")),
                () -> assertTrue(assembly.contains("new AgentDefinitionMemoryCatalog")),
                () -> assertTrue(assembly.contains("new OpsAgentDefinitionMutationAdapter")),
                () -> assertTrue(assembly.contains("new AgentDefinitionMutationService")),
                () -> assertTrue(assembly.contains("new AgentDefinitionQueryService")),
                () -> assertFalse(assembly.contains("ObjectProvider")),
                () -> assertFalse(assembly.contains("@Autowired")),
                () -> assertTrue(assembly.lines().count() < 130));
    }

    @Test
    void runtimeSettingsMustOwnAgentConfigurationAndEffectiveDefaultState() throws IOException {
        String settings = read(ROOT + "OpsAgentDefinitionRuntimeSettings.java");

        assertAll(
                () -> assertTrue(settings.contains("orbisops.agents.locations")),
                () -> assertTrue(settings.contains("orbisops.agents.default-agent-id")),
                () -> assertTrue(settings.contains("orbisops.agents.jdbc-enabled")),
                () -> assertTrue(settings.contains("volatile String effectiveDefaultAgentId")),
                () -> assertTrue(settings.contains("useEffectiveDefaultAgentId")),
                () -> assertFalse(settings.contains("IAgentDefinitionRepository")),
                () -> assertFalse(settings.contains("ProjectDefinitionApplicationService")),
                () -> assertTrue(settings.lines().count() < 80));
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
