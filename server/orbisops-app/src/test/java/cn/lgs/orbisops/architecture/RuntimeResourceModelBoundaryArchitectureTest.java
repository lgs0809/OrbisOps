package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeModelSettings;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeResourceModelBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void modelResolverMustOwnRepositoriesSecretsFallbackAndModelConstruction() throws IOException {
        String resolver = read(RUNTIME + "OpsRuntimeModelResolver.java");

        assertAll(
                () -> assertTrue(resolver.contains("Supplier<ChatModel> namedDefaultModelSupplier")),
                () -> assertTrue(resolver.contains("Supplier<ChatModel> fallbackModelSupplier")),
                () -> assertTrue(resolver.contains("Supplier<AiClientModelCatalogPort>")),
                () -> assertTrue(resolver.contains("Supplier<AiClientApiCatalogPort>")),
                () -> assertFalse(resolver.contains("domain.agent.adapter.repository")),
                () -> assertFalse(resolver.contains("AiClientConfigRecord")),
                () -> assertTrue(resolver.contains("ModelAvailabilityPort")),
                () -> assertTrue(resolver.contains("OpsSecretResolver")),
                () -> assertTrue(resolver.contains("OpsRuntimeModelSettings")),
                () -> assertTrue(resolver.contains("new OpsResilientOpenAiApi(")),
                () -> assertTrue(resolver.contains("settings.requestBudgetMillis()")),
                () -> assertTrue(resolver.contains("OpenAiChatModel.builder()")),
                () -> assertTrue(resolver.contains("RESOURCE_WARN")),
                () -> assertFalse(resolver.contains("ApplicationContext")),
                () -> assertFalse(resolver.contains("ObjectProvider")),
                () -> assertFalse(resolver.contains("@Autowired")),
                () -> assertFalse(resolver.contains("@Value")),
                () -> assertTrue(resolver.lines().count() < 170));
    }

    @Test
    void settingsMustOwnOnlyModelHttpTimeouts() throws IOException {
        String settings = read(RUNTIME + "OpsRuntimeModelSettings.java");

        assertAll(
                () -> assertTrue(settings.contains("spring.ai.openai.chat.connect-timeout-seconds")),
                () -> assertTrue(settings.contains("spring.ai.openai.chat.read-timeout-seconds")),
                () -> assertTrue(settings.contains("forTest")),
                () -> assertFalse(settings.contains("ChatModel")),
                () -> assertFalse(settings.contains("IAiClientModelConfigRepository")),
                () -> assertFalse(settings.contains("OpsSecretResolver")));

        // Enforce the responsibility, rather than mirroring formatting or accessor length.
        var fields = Arrays.stream(OpsRuntimeModelSettings.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic()).toList();
        assertEquals(Set.of("connectTimeoutSeconds", "readTimeoutSeconds", "modelCallTimeoutSeconds"),
                fields.stream().map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet()));
        assertTrue(fields.stream().allMatch(field -> field.getType() == int.class
                && Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers())));
        var type = new ClassFileImporter().importClasses(OpsRuntimeModelSettings.class)
                .get(OpsRuntimeModelSettings.class);
        assertTrue(type.getDirectDependenciesFromSelf().stream().allMatch(dependency -> {
            String target = dependency.getTargetClass().getName();
            return Set.of("int", "long", OpsRuntimeModelSettings.class.getName(),
                    "java.util.concurrent.TimeUnit", "org.springframework.beans.factory.annotation.Value",
                    "org.springframework.stereotype.Component").contains(target) || target.startsWith("java.lang.");
        }), () -> "Unexpected model-settings dependency: " + type.getDirectDependenciesFromSelf());
        var published = new OpsRuntimeModelSettings(3, 45, 660);
        assertEquals(45, published.readTimeoutSeconds());
        assertEquals(660_000L, published.requestBudgetMillis());
        assertEquals(660, published.synchronousResponseTimeoutSeconds());
    }

    @Test
    void pipelineMustDelegateModelRuleAndAssemblerMustRemainAThinFacade() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains("OpsRuntimeModelResolver modelResolver")),
                () -> assertTrue(pipeline.contains("context.setChatModel(modelResolver.resolve(context))")),
                () -> assertEquals(1, occurrences(assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertTrue(assembler.contains("OpsRuntimeResourceContextFactory contextFactory")),
                () -> assertTrue(assembler.contains("OpsRuntimeResourcePipeline pipeline")),
                () -> assertFalse(assembler.contains("OpsRuntimeModelResolver")),
                () -> assertFalse(assembler.contains("ApplicationContext")),
                () -> assertFalse(assembler.contains("ObjectProvider<ChatModel>")),
                () -> assertFalse(assembler.contains("IAiClientModelConfigRepository")),
                () -> assertFalse(assembler.contains("IAiClientApiConfigRepository")),
                () -> assertFalse(assembler.contains("ModelAvailabilityPort")),
                () -> assertFalse(assembler.contains("OpsSecretResolver")),
                () -> assertFalse(assembler.contains("OpenAiApi")),
                () -> assertFalse(assembler.contains("OpenAiChatModel")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void configurationMustOwnQualifiedAndFallbackModelBeanResolution() throws IOException {
        String configuration = read(RUNTIME + "OpsRuntimeResourceAssemblerConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("@Qualifier(\"openAiChatModel\")")),
                () -> assertTrue(configuration.contains("ObjectProvider<ChatModel> namedDefaultModelProvider")),
                () -> assertTrue(configuration.contains("ObjectProvider<ChatModel> fallbackModelProvider")),
                () -> assertTrue(configuration.contains("ObjectProvider<AiClientModelCatalogPort>")),
                () -> assertTrue(configuration.contains("ObjectProvider<AiClientApiCatalogPort>")),
                () -> assertFalse(configuration.contains("domain.agent.adapter.repository")),
                () -> assertTrue(configuration.contains("new OpsRuntimeModelResolver(")),
                () -> assertTrue(configuration.contains("namedDefaultModelProvider::getIfAvailable")),
                () -> assertTrue(configuration.lines().count() < 50));
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
