package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationEntryBoundaryArchitectureTest {

    private static final String CHANGE_TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void agentResolverOwnsFrozenWorkSessionAgentSelection() throws IOException {
        String resolver = read(CHANGE_TRIGGER + "OpsPreparationAgentResolver.java");

        assertAll(
                () -> assertTrue(resolver.contains("final class OpsPreparationAgentResolver")),
                () -> assertTrue(resolver.contains("OpsAgentDefinition resolve(")),
                () -> assertTrue(resolver.contains("preparationGraphId")),
                () -> assertTrue(resolver.contains("projectDefaultAgentId(")),
                () -> assertTrue(resolver.contains("agentSnapshot")),
                () -> assertTrue(resolver.contains("agentRunExecutionContext")),
                () -> assertTrue(resolver.contains("PREPARATION_AGENT_OVERRIDE_FORBIDDEN")),
                () -> assertTrue(resolver.contains("PREPARATION_AGENT_SNAPSHOT_REQUIRED")),
                () -> assertFalse(resolver.contains("defaultPreparationAgentId")),
                () -> assertFalse(resolver.contains("CHANGE_PREPARATION")),
                () -> assertFalse(resolver.contains("ChangePackagePreparationAgentSelection")),
                () -> assertFalse(resolver.contains("selection(")),
                () -> assertFalse(resolver.contains("Map<String, Object> view(")),
                () -> assertTrue(resolver.contains("Supplier<ProjectDefinitionApplicationService>")),
                () -> assertFalse(resolver.contains("org.springframework")),
                () -> assertFalse(resolver.contains("@Service")),
                () -> assertFalse(resolver.contains("@Component")),
                () -> assertFalse(resolver.contains("interface OpsPreparationAgent")));
    }

    @Test
    void contextBundleServiceOwnsAuthoritativeBindingAndScopeVerification() throws IOException {
        String bundleService = read(CHANGE_TRIGGER + "OpsPreparationContextBundleService.java");

        assertAll(
                () -> assertTrue(bundleService.contains("final class OpsPreparationContextBundleService")),
                () -> assertTrue(bundleService.contains("bindLatestForSession(")),
                () -> assertTrue(bundleService.contains("requireForRequest(")),
                () -> assertTrue(bundleService.contains("validateRevision(")),
                () -> assertTrue(bundleService.contains("latestCompletedBundleForSession(")),
                () -> assertTrue(bundleService.contains("service.requireBundle(")),
                () -> assertTrue(bundleService.contains("CONTEXT_BUNDLE_PROJECT_MISMATCH")),
                () -> assertTrue(bundleService.contains("CONTEXT_BUNDLE_RUN_MISMATCH")),
                () -> assertTrue(bundleService.contains("CONTEXT_BUNDLE_SESSION_MISMATCH")),
                () -> assertTrue(bundleService.contains("applyAuthoritative(")),
                () -> assertFalse(bundleService.contains("@Service")),
                () -> assertFalse(bundleService.contains("@Component")),
                () -> assertFalse(bundleService.contains("ObjectProvider")),
                () -> assertFalse(bundleService.contains("interface OpsPreparationContextBundle")));
    }

    @Test
    void preparationServiceDelegatesEntryResponsibilitiesWithoutDuplicatingRules() throws IOException {
        String preparation = read(CHANGE_TRIGGER + "OpsChangePackagePreparationService.java");

        assertAll(
                () -> assertTrue(preparation.contains("OpsPreparationAgentResolver preparationAgentResolver")),
                () -> assertTrue(preparation.contains("OpsPreparationContextBundleService contextBundleService")),
                () -> assertTrue(preparation.contains("preparationAgentResolver.resolve(")),
                () -> assertFalse(preparation.contains("preparationAgentResolver.selection(")),
                () -> assertTrue(preparation.contains("ChangePackagePreparationPlan.from(")),
                () -> assertTrue(preparation.contains("ChangePackageRevisionPlan.from(")),
                () -> assertFalse(preparation.contains("preparationAgentResolver.view(")),
                () -> assertTrue(preparation.contains("contextBundleService.bindLatestForSession(")),
                () -> assertTrue(preparation.contains("contextBundleService.validateRevision(")),
                () -> assertTrue(preparation.contains("contextBundleService.requireForRequest(")),
                () -> assertFalse(preparation.contains("resolvePreparationAgent(")),
                () -> assertFalse(preparation.contains("isPreparationAgent(")),
                () -> assertFalse(preparation.contains("verifyBundleScope(")),
                () -> assertFalse(preparation.contains("applyAuthoritativeBundle(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> contextBundle(")),
                () -> assertFalse(preparation.contains("private List<Object> skillRefValues(")));
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
