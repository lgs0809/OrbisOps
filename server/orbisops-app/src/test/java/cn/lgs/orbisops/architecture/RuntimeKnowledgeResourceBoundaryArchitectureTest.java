package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeKnowledgeResourceBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void resolverMustOwnDefaultSelectionAuthorizationScopeAndMetadata() throws IOException {
        String resolver = read(RUNTIME + "OpsRuntimeKnowledgeResolver.java");

        assertAll(
                () -> assertTrue(resolver.contains(
                        "Supplier<ProjectKnowledgeAuthorizationApplicationService>")),
                () -> assertTrue(resolver.contains("authorization.defaultId(context.getProjectId())")),
                () -> assertTrue(resolver.contains("authorization.scope(")),
                () -> assertTrue(resolver.contains("未授权给项目")),
                () -> assertTrue(resolver.contains("\"ragEnabled\"")),
                () -> assertTrue(resolver.contains("\"knowledgeBaseId\"")),
                () -> assertTrue(resolver.contains("\"knowledgeBaseScope\"")),
                () -> assertFalse(resolver.contains("ObjectProvider")),
                () -> assertFalse(resolver.contains("@Autowired")),
                () -> assertFalse(resolver.contains("@Value")),
                () -> assertTrue(resolver.lines().count() < 70));
    }

    @Test
    void pipelineMustDelegateKnowledgeRuleAndAssemblerMustNeverReclaimAuthorization() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains("OpsRuntimeKnowledgeResolver knowledgeResolver")),
                () -> assertTrue(pipeline.contains("rule(\"KNOWLEDGE_BASE\", knowledgeResolver::resolve)")),
                () -> assertEquals(1, occurrences(
                        assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeKnowledgeResolver")),
                () -> assertFalse(assembler.contains(
                        "ProjectKnowledgeAuthorizationApplicationService")),
                () -> assertFalse(assembler.contains(
                        "projectKnowledgeAuthorizationService")),
                () -> assertFalse(assembler.contains("authorization.defaultId(")),
                () -> assertFalse(assembler.contains("authorization.scope(")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void configurationMustBeTheOnlyObjectProviderOwner() throws IOException {
        String configuration = read(
                RUNTIME + "OpsRuntimeKnowledgeResolverConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains(
                        "ObjectProvider<ProjectKnowledgeAuthorizationApplicationService>")),
                () -> assertTrue(configuration.contains(
                        "new OpsRuntimeKnowledgeResolver(authorizationProvider::getIfAvailable)")),
                () -> assertTrue(configuration.lines().count() < 30));
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
