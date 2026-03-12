package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentDefinitionPersistenceArchitectureTest {

    private static final String LEGACY_REGISTRY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsAgentDefinitionRegistry.java";
    private static final String APPLICATION_FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionApplicationFacade.java";
    private static final String YAML_LOADER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionYamlLoader.java";
    private static final String SNAPSHOT_MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionSnapshotMapper.java";
    private static final String MEMORY_CATALOG = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/agentdefinition/AgentDefinitionMemoryCatalog.java";
    private static final String MUTATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/agentdefinition/AgentDefinitionMutationService.java";
    private static final String MUTATION_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionMutationAdapter.java";
    private static final String LOAD_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/agentdefinition/AgentDefinitionCatalogLoadService.java";
    private static final String SOURCE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionCatalogSourceAdapter.java";
    private static final String FALLBACK_FACTORY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionFallbackFactory.java";
    private static final String CAPABILITY_QUERY_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentCapabilityBindingQueryAdapter.java";
    private static final String CATALOG_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionCatalogAdapter.java";
    private static final String DESCRIPTOR_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsAgentDefinitionDescriptorAdapter.java";
    private static final String PROJECT_DIRECTORY_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/agentdefinition/OpsProjectAgentDirectoryAdapter.java";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcAgentDefinitionRepository.java";
    private static final String GRAPH_REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcAgentDefinitionGraphRepository.java";
    private static final String CAPABILITY_REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcAgentCapabilityBindingRepository.java";
    private static final String SCHEMA_INITIALIZER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcAgentDefinitionSchemaInitializer.java";
    private static final String WORKFLOW_MIGRATION = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcWorkflowTypedDefinitionMigrationContributor.java";

    @Test
    void registryCannotReclaimAgentDefinitionSchemaLifecycle() throws IOException {
        String registry = read(APPLICATION_FACADE);

        assertAll(
                () -> assertFalse(registry.contains("CREATE TABLE")),
                () -> assertFalse(registry.contains("ALTER TABLE")),
                () -> assertFalse(registry.contains("information_schema")),
                () -> assertFalse(registry.contains("ensureTable(")),
                () -> assertFalse(registry.contains("addColumnIfMissing(")),
                () -> assertFalse(registry.contains("jdbcInitialized")),
                () -> assertFalse(registry.contains("orbisops.agents.auto-init")));
    }

    @Test
    void yamlDiscoveryAndMappingBelongOnlyToSourceLoader() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String loader = read(YAML_LOADER);

        assertAll(
                () -> assertFalse(registry.contains("org.yaml.snakeyaml")),
                () -> assertFalse(registry.contains("ResourcePatternResolver")),
                () -> assertFalse(registry.contains("PathMatchingResourcePatternResolver")),
                () -> assertFalse(registry.contains("parseNodes(")),
                () -> assertFalse(registry.contains("parseEdges(")),
                () -> assertFalse(registry.contains("parseMcpServers(")),
                () -> assertFalse(registry.contains("parseAgentScopeAgents(")),
                () -> assertFalse(registry.contains("parseLoops(")),
                () -> assertFalse(registry.contains("parseLocations(")),
                () -> assertFalse(registry.contains("private Map<String, Object> map(")),
                () -> assertFalse(registry.contains("private List<String> stringList(")),
                () -> assertFalse(registry.contains("private Map<String, String> stringMap(")),
                () -> assertFalse(registry.contains("private Integer integer(")),
                () -> assertFalse(registry.contains("private Boolean bool(")),
                () -> assertFalse(registry.contains("private String text(")),
                () -> assertFalse(loader.contains("registerDefinition(")),
                () -> assertFalse(loader.contains("project Agent")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(loader.contains("implements AgentDefinitionSourceLoader")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(loader.contains("ResourcePatternResolver")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(loader.contains("new Yaml().load")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(loader.contains("parseNodes(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(loader.contains("parseEdges(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(loader.contains("parseMcpServers(")));
    }

    @Test
    void snapshotCodecCopyAndHashBelongOnlyToSnapshotMapper() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String mapper = read(SNAPSHOT_MAPPER);

        assertAll(
                () -> assertFalse(registry.contains("ObjectMapper")),
                () -> assertFalse(registry.contains("OpsJsonSnapshotCodec")),
                () -> assertFalse(registry.contains("OpsRuntimeHashing")),
                () -> assertFalse(registry.contains("definitionFromSnapshot(")),
                () -> assertFalse(registry.contains("persistenceSnapshot(")),
                () -> assertFalse(registry.contains("parseStoredDefinition(")),
                () -> assertFalse(registry.contains("copyDefinition(")),
                () -> assertFalse(registry.contains("private String definitionHash(")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("implements AgentDefinitionSnapshotMapper")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("OpsJsonSnapshotCodec.read")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("OpsJsonSnapshotCodec.write")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("OpsRuntimeHashing.canonicalHash")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("objectMapper.writeValueAsBytes")));
    }

    @Test
    void currentAndVersionMemoryOwnershipBelongsOnlyToApplicationCatalog() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String catalog = read(MEMORY_CATALOG);

        assertAll(
                () -> assertFalse(registry.contains("Map<String, OpsAgentDefinition> definitions")),
                () -> assertFalse(registry.contains("definitionVersions")),
                () -> assertFalse(registry.contains("new TreeMap")),
                () -> assertFalse(registry.contains("NavigableMap<Integer, OpsAgentDefinition>")),
                () -> assertFalse(registry.contains("definitions.put(")),
                () -> assertFalse(registry.contains("definitions.remove(")),
                () -> assertFalse(registry.contains("definitions.containsKey(")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("class AgentDefinitionMemoryCatalog")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("currentDefinitions")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("NavigableMap<Integer, D>")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("descriptorPort.snapshot")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("descendingMap")));
    }

    @Test
    void mutationOrderingBelongsToApplicationServiceAndTriggerAdapter() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String service = read(MUTATION_SERVICE);
        String adapter = read(MUTATION_ADAPTER);

        assertAll(
                () -> assertFalse(registry.contains("private int nextVersion(")),
                () -> assertFalse(registry.contains("normalizeWorkflowNodeModes(")),
                () -> assertFalse(registry.contains("persistVersion(")),
                () -> assertFalse(registry.contains("persistPublishedCas(")),
                () -> assertFalse(registry.contains("persistNormalized(")),
                () -> assertFalse(registry.contains("persistCapabilityBindings(")),
                () -> assertFalse(registry.contains("definitionRepository.saveVersion(")),
                () -> assertFalse(registry.contains("definitionRepository.publish(")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("savePublished(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("saveDraft(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("validateVersion(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("publishVersion(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("disableVersion(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("deleteCurrent(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("memoryCatalog.register")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("implements")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("AgentDefinitionMutationModelPort")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("AgentDefinitionMutationStorePort")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("definitionRepository.saveVersion")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("definitionRepository.publish")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("graphRepository.replace")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("capabilityBindingUseCase.replace")));
    }

    @Test
    void catalogLoadPrecedenceBelongsToApplicationServiceAndSourceAdapters() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String service = read(LOAD_SERVICE);
        String sourceAdapter = read(SOURCE_ADAPTER);
        String fallbackFactory = read(FALLBACK_FACTORY);

        assertAll(
                () -> assertFalse(registry.contains("loadFromLocations(")),
                () -> assertFalse(registry.contains("loadFromJdbc(")),
                () -> assertFalse(registry.contains("isStoredYamlShadow(")),
                () -> assertFalse(registry.contains("isStoredYamlVersionShadow(")),
                () -> assertFalse(registry.contains("fallbackDefinition(")),
                () -> assertFalse(registry.contains("definitionRepository.listCurrentEnabled")),
                () -> assertFalse(registry.contains("definitionRepository.listVersionsEnabled")),
                () -> assertFalse(registry.contains("OpsWorkflowNode.builder()")),
                () -> assertFalse(registry.contains("OpsGraphEdge.builder()")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("loadPlatformDefinitions")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("loadStoredCurrentDefinitions")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("loadStoredVersionDefinitions")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("memoryCatalog.clear")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("isYaml(")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(service.contains("fallbackFactory.create")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(sourceAdapter.contains("definitionRepository.listCurrentEnabled")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(sourceAdapter.contains("definitionRepository.listVersionsEnabled")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(fallbackFactory.contains("implements AgentDefinitionFallbackFactory")),
                () -> assertFalse(fallbackFactory.contains("OpsAgentDefinitionRegistry")));
    }

    @Test
    void capabilityLegacyQueryBelongsOnlyToQueryAdapter() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String adapter = read(CAPABILITY_QUERY_ADAPTER);

        assertAll(
                () -> assertFalse(registry.contains("AgentCapabilityBindingUseCase")),
                () -> assertFalse(registry.contains("OpsAgentCapabilityBindingMapper")),
                () -> assertFalse(registry.contains("DataAccessException")),
                () -> assertFalse(registry.contains("jdbcUnavailableLogged")),
                () -> assertFalse(registry.contains("capabilityBindingMapper.views")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("AgentCapabilityBindingUseCase")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("OpsAgentCapabilityBindingMapper")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("catch (DataAccessException")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("storeUnavailableLogged")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(adapter.contains("mapper.views")));
    }

    @Test
    void queryPortsBelongToDedicatedTriggerAdapters() throws IOException {
        String registry = read(APPLICATION_FACADE);
        String catalog = read(CATALOG_ADAPTER);
        String descriptor = read(DESCRIPTOR_ADAPTER);
        String projectDirectory = read(PROJECT_DIRECTORY_ADAPTER);

        assertAll(
                () -> assertFalse(registry.contains("implements\n        AgentDefinitionCatalogPort")),
                () -> assertFalse(registry.contains("AgentDefinitionDescriptorPort<OpsAgentDefinition>")),
                () -> assertFalse(registry.contains("ProjectAgentDirectoryPort")),
                () -> assertFalse(registry.contains("return memoryCatalog.current(")),
                () -> assertFalse(registry.contains("return mutationService.findVersion(")),
                () -> assertFalse(registry.contains("new AgentDefinitionVersionState(")),
                () -> assertFalse(registry.contains("projectDefinitionService.exists(")),
                () -> assertFalse(registry.contains("projectDefinitionService.defaultAgentId(")));

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("implements AgentDefinitionCatalogPort")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("memoryCatalog.current")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("mutationService.findVersion")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(descriptor.contains("implements AgentDefinitionDescriptorPort")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(descriptor.contains("new AgentDefinitionVersionState")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(projectDirectory.contains("implements ProjectAgentDirectoryPort")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(projectDirectory.contains("service.exists")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(projectDirectory.contains("service.defaultAgentId")));
    }

    @Test
    void productionConsumersDependOnAgentDefinitionGatewaysAndLegacyRegistryIsRemoved() throws IOException {
        Set<String> concreteReferences = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            String content = Files.readString(source);
            if (content.contains("OpsAgentDefinitionRegistry")) {
                concreteReferences.add(relative(source));
            }
        }

        assertEquals(Set.of(), concreteReferences,
                "Production code must not reference the removed Agent Definition Registry class");
        assertFalse(Files.exists(projectRoot().resolve(LEGACY_REGISTRY)),
                "Legacy OpsAgentDefinitionRegistry source must stay removed");

        String facade = read(APPLICATION_FACADE);
        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(facade.contains("@Service")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(
                        facade.contains("implements OpsAgentDefinitionGateway")));
    }

    @Test
    void currentAndVersionDmlBelongOnlyToRepository() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            for (String line : Files.readAllLines(source)) {
                if (isCurrentOrVersionDml(line)) {
                    owners.add(relative(source));
                }
            }
        }

        assertEquals(Set.of(REPOSITORY, WORKFLOW_MIGRATION), owners,
                "Agent Definition DML must stay behind the repository or controlled migration contributor");
    }

    @Test
    void normalizedGraphDmlBelongsOnlyToGraphRepository() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            for (String line : Files.readAllLines(source)) {
                if (isNormalizedGraphDml(line)) {
                    owners.add(relative(source));
                }
            }
        }

        assertEquals(Set.of(GRAPH_REPOSITORY), owners,
                "Agent Definition normalized graph DML must stay behind IAgentDefinitionGraphRepository");

        String registry = read(APPLICATION_FACADE);
        assertAll(
                () -> assertFalse(registry.contains("ai_ops_agent_node")),
                () -> assertFalse(registry.contains("ai_ops_agent_edge")),
                () -> assertFalse(registry.contains("ai_ops_agentscope_agent")),
                () -> assertFalse(registry.contains("ai_ops_agent_skill_binding")),
                () -> assertFalse(registry.contains("ai_ops_agent_mcp_server")),
                () -> assertFalse(registry.contains("insertSkills(")),
                () -> assertFalse(registry.contains("insertMcpServers(")),
                () -> assertFalse(registry.contains("persistNormalized(JdbcTemplate")));
    }

    @Test
    void capabilityBindingDmlBelongsOnlyToCapabilityRepository() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            for (String line : Files.readAllLines(source)) {
                if (containsDml(line.trim().toUpperCase(Locale.ROOT),
                        "AI_OPS_AGENT_CAPABILITY_BINDING")) {
                    owners.add(relative(source));
                }
            }
        }

        assertEquals(Set.of(CAPABILITY_REPOSITORY), owners,
                "Agent capability binding DML must stay behind IAgentCapabilityBindingRepository");

        String registry = read(APPLICATION_FACADE);
        assertAll(
                () -> assertFalse(registry.contains("ai_ops_agent_capability_binding")),
                () -> assertFalse(registry.contains("JdbcTemplate")),
                () -> assertFalse(registry.contains("insertCapabilityRefs(")),
                () -> assertFalse(registry.contains("insertInlineMcpCapabilityRefs(")),
                () -> assertFalse(registry.contains("insertCapabilityRef(")),
                () -> assertFalse(registry.contains("capabilityScope(")));
    }

    @Test
    void agentDefinitionDdlBelongsOnlyToSchemaInitializer() throws IOException {
        Set<String> owners = new LinkedHashSet<>();
        for (Path source : productionJavaSources()) {
            String content = Files.readString(source);
            if (containsAgentDefinitionTable(content) && containsSchemaLifecycle(content)) {
                owners.add(relative(source));
            }
        }

        assertEquals(Set.of(SCHEMA_INITIALIZER), owners,
                "Agent Definition DDL must have one Infrastructure schema owner");

        String repository = read(REPOSITORY);
        assertAll(
                () -> assertFalse(repository.contains("CREATE TABLE")),
                () -> assertFalse(repository.contains("ALTER TABLE")),
                () -> assertFalse(repository.contains("information_schema")));
    }

    private boolean isCurrentOrVersionDml(String line) {
        String normalized = line.trim().toUpperCase(Locale.ROOT);
        boolean currentTable = normalized.contains("AI_OPS_AGENT_DEFINITION")
                || normalized.contains("AI_OPS_AGENT_DEFINITION_VERSION");
        boolean dml = normalized.contains("FROM AI_OPS_AGENT_DEFINITION")
                || normalized.contains("INSERT INTO AI_OPS_AGENT_DEFINITION")
                || normalized.contains("INSERT IGNORE INTO AI_OPS_AGENT_DEFINITION")
                || normalized.contains("UPDATE AI_OPS_AGENT_DEFINITION")
                || normalized.contains("DELETE FROM AI_OPS_AGENT_DEFINITION");
        return currentTable && dml;
    }

    private boolean isNormalizedGraphDml(String line) {
        String normalized = line.trim().toUpperCase(Locale.ROOT);
        return containsDml(normalized, "AI_OPS_AGENT_NODE")
                || containsDml(normalized, "AI_OPS_AGENT_EDGE")
                || containsDml(normalized, "AI_OPS_AGENTSCOPE_AGENT")
                || containsDml(normalized, "AI_OPS_AGENT_SKILL_BINDING")
                || containsDml(normalized, "AI_OPS_AGENT_MCP_SERVER");
    }

    private boolean containsDml(String normalizedLine, String tableName) {
        return containsSqlOperation(normalizedLine, "FROM", tableName)
                || containsSqlOperation(normalizedLine, "INSERT INTO", tableName)
                || containsSqlOperation(normalizedLine, "INSERT IGNORE INTO", tableName)
                || containsSqlOperation(normalizedLine, "UPDATE", tableName)
                || containsSqlOperation(normalizedLine, "DELETE FROM", tableName);
    }

    private boolean containsSqlOperation(String normalizedLine, String operation, String tableName) {
        String token = operation + " " + tableName;
        int index = normalizedLine.indexOf(token);
        if (index < 0) {
            return false;
        }
        int end = index + token.length();
        return end == normalizedLine.length()
                || Character.isWhitespace(normalizedLine.charAt(end))
                || normalizedLine.charAt(end) == '(';
    }

    private boolean containsAgentDefinitionTable(String content) {
        return containsTableToken(content, "ai_ops_agent_definition")
                || containsTableToken(content, "ai_ops_agent_definition_version")
                || containsTableToken(content, "ai_ops_agent_node")
                || containsTableToken(content, "ai_ops_agent_edge")
                || containsTableToken(content, "ai_ops_agentscope_agent")
                || containsTableToken(content, "ai_ops_agent_skill_binding")
                || containsTableToken(content, "ai_ops_agent_mcp_server")
                || containsTableToken(content, "ai_ops_agent_capability_binding");
    }

    private boolean containsTableToken(String content, String tableName) {
        return content.contains("\"" + tableName + "\"")
                || content.contains(tableName + " ")
                || content.contains(tableName + " (");
    }

    private boolean containsSchemaLifecycle(String content) {
        return content.contains("CREATE TABLE")
                || content.contains("ALTER TABLE");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private List<Path> productionJavaSources() throws IOException {
        Path root = projectRoot();
        try (Stream<Path> modules = Files.list(root)) {
            List<Path> sourceRoots = modules
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("orbisops-"))
                    .map(path -> path.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .toList();
            try (Stream<Path> sources = sourceRoots.stream()
                    .flatMap(this::walkUnchecked)) {
                return sources
                        .filter(path -> path.toString().endsWith(".java"))
                        .toList();
            }
        }
    }

    private Stream<Path> walkUnchecked(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot inspect production source tree: " + root, error);
        }
    }

    private String relative(Path source) {
        return projectRoot().relativize(source).toString().replace('\\', '/');
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) {
            return nested;
        }
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
