package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSkillResourceBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void settingsMustOwnOnlySkillRuntimeProperties() throws IOException {
        String settings = read(RUNTIME + "OpsRuntimeSkillSettings.java");

        assertAll(
                () -> assertTrue(settings.contains("orbisops.multi-agent.skill-context-enabled")),
                () -> assertTrue(settings.contains("orbisops.multi-agent.skill-context-mode")),
                () -> assertTrue(settings.contains("orbisops.multi-agent.skill-context-max-chars")),
                () -> assertTrue(settings.contains("orbisops.skill-runtime.max-single-skill-chars")),
                () -> assertTrue(settings.contains("orbisops.skill-runtime.catalog-summary-max-chars")),
                () -> assertTrue(settings.contains("forTest")),
                () -> assertFalse(settings.contains("OpsSkillToolProvider")),
                () -> assertFalse(settings.contains("SkillCatalogQueryService")),
                () -> assertTrue(settings.lines().count() < 80));
    }

    @Test
    void frozenContextResolverMustOwnVersionCatalogCanaryAndBudgetMaterialization() throws IOException {
        String resolver = read(RUNTIME + "OpsRuntimeFrozenSkillContextResolver.java");

        assertAll(
                () -> assertTrue(resolver.contains("Supplier<SkillCatalogQueryService>")),
                () -> assertTrue(resolver.contains("Supplier<OpsSkillReleaseService>")),
                () -> assertTrue(resolver.contains("catalogQuery.getRuntimeSkillVersion(")),
                () -> assertTrue(resolver.contains("renderFrozenCanaryContext(")),
                () -> assertTrue(resolver.contains("usedSkillVersionRefs")),
                () -> assertTrue(resolver.contains("skillCatalogRefs")),
                () -> assertTrue(resolver.contains("maxSingleSkillChars")),
                () -> assertFalse(resolver.contains("OpsSkillToolProvider")),
                () -> assertFalse(resolver.contains("ProjectSkillAuthorizationApplicationService")),
                () -> assertFalse(resolver.contains("ObjectProvider")),
                () -> assertFalse(resolver.contains("@Autowired")),
                () -> assertFalse(resolver.contains("@Value")),
                () -> assertTrue(resolver.lines().count() < 220));
    }

    @Test
    void skillResolverMustOwnToolExposureModeAndProjectInheritance() throws IOException {
        String resolver = read(RUNTIME + "OpsRuntimeSkillResolver.java");

        assertAll(
                () -> assertTrue(resolver.contains("Supplier<SkillRuntimeToolProvider>")),
                () -> assertTrue(resolver.contains("Supplier<ProjectSkillAuthorizationApplicationService>")),
                () -> assertTrue(resolver.contains("Supplier<OpsProjectSkillToolProvider>")),
                () -> assertTrue(resolver.contains("OpsRuntimeFrozenSkillContextResolver")),
                () -> assertFalse(resolver.contains("buildSkillToolCallback")),
                () -> assertTrue(resolver.contains("provider.build(")),
                () -> assertTrue(resolver.contains("enabledProjectSkillIds")),
                () -> assertTrue(resolver.contains("skillContextMode")),
                () -> assertTrue(resolver.contains("OpsRuntimeGovernedToolCallback.wrap")),
                () -> assertTrue(resolver.contains("OpsRuntimeToolAuthorityDescriptor.readOnly")),
                () -> assertFalse(resolver.contains("SkillCatalogQueryService")),
                () -> assertFalse(resolver.contains("OpsSkillReleaseService")),
                () -> assertFalse(resolver.contains("ObjectProvider")),
                () -> assertFalse(resolver.contains("@Autowired")),
                () -> assertFalse(resolver.contains("@Value")),
                () -> assertTrue(resolver.lines().count() < 190));
    }

    @Test
    void legacyAnalysisSkillCallsMustUseFrozenGovernedContext() throws IOException {
        String helper = read(RUNTIME.replace("ops/runtime/", "ops/") + "OpsLlmSkillContextService.java");
        assertAll(
                () -> assertTrue(helper.contains("Supplier<OpsRuntimeSkillResolver>")),
                () -> assertTrue(helper.contains("trace.skillFrame()")),
                () -> assertTrue(helper.contains("resolver().resolve(context)")),
                () -> assertTrue(helper.contains("resolver().catalogTool(context)")),
                () -> assertTrue(helper.contains("SKILL_RUNTIME_CONTEXT_REQUIRED")),
                () -> assertFalse(helper.contains("SkillRuntimeToolProvider")),
                () -> assertFalse(helper.contains("renderSkillContext")),
                () -> assertFalse(helper.contains("buildSkillToolCallback")));
    }

    @Test
    void pipelineMustDelegateSkillRuleAndAssemblerMustNeverReclaimSkillInfrastructure() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String contextFactory = read(RUNTIME + "OpsRuntimeResourceContextFactory.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains("OpsRuntimeSkillResolver skillResolver")),
                () -> assertTrue(pipeline.contains("rule(\"SKILL\", skillResolver::resolve)")),
                () -> assertTrue(contextFactory.contains("skillResolver.enabledProjectSkillIds(projectId)")),
                () -> assertEquals(1, occurrences(assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeSkillResolver")),
                () -> assertFalse(assembler.contains("OpsSkillToolProvider")),
                () -> assertFalse(assembler.contains("SkillCatalogQueryService")),
                () -> assertFalse(assembler.contains("ProjectSkillAuthorizationApplicationService")),
                () -> assertFalse(assembler.contains("OpsSkillReleaseService")),
                () -> assertFalse(assembler.contains("OpsProjectSkillToolProvider")),
                () -> assertFalse(assembler.contains("skillContextEnabled")),
                () -> assertFalse(assembler.contains("skillContextMode")),
                () -> assertFalse(assembler.contains("catalogSkillContext")),
                () -> assertFalse(assembler.contains("lazySkillContext")),
                () -> assertFalse(assembler.contains("ObjectProvider")),
                () -> assertFalse(assembler.contains("@Value")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void configurationMustBeTheOnlyObjectProviderOwnerForSkillResolution() throws IOException {
        String configuration = read(RUNTIME + "OpsRuntimeSkillResolverConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("ObjectProvider<SkillCatalogQueryService>")),
                () -> assertTrue(configuration.contains("ObjectProvider<OpsSkillReleaseService>")),
                () -> assertTrue(configuration.contains("ObjectProvider<SkillRuntimeToolProvider>")),
                () -> assertTrue(configuration.contains("ObjectProvider<ProjectSkillAuthorizationApplicationService>")),
                () -> assertTrue(configuration.contains("ObjectProvider<OpsProjectSkillToolProvider>")),
                () -> assertTrue(configuration.contains("new OpsRuntimeFrozenSkillContextResolver(")),
                () -> assertTrue(configuration.contains("new OpsRuntimeSkillResolver(")),
                () -> assertTrue(configuration.lines().count() < 55));
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
