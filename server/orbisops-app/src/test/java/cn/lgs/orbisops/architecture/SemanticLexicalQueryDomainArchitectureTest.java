package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticLexicalQueryDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/SemanticLexicalQueryPolicy.java";
    private static final String ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsSemanticLexicalRecallAdapter.java";

    @Test
    void domainOwnsTokenizationNormalizationDedupLimitAndOrRendering() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("TOKEN_PATTERN")),
                () -> assertTrue(policy.contains("matcher.group().toLowerCase(Locale.ROOT)")),
                () -> assertTrue(policy.contains(".filter(term -> term.length() >= 2)")),
                () -> assertTrue(policy.contains(".distinct()")),
                () -> assertTrue(policy.contains(".limit(12)")),
                () -> assertTrue(policy.contains("String.join(\" OR \", terms)")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("websearch_to_tsquery")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void lexicalAdapterDelegatesQueryBuildingAndOnlyOwnsSqlCodecAndTableSafety() throws IOException {
        String adapter = read(ADAPTER);

        assertAll(
                () -> assertTrue(adapter.contains("SemanticLexicalQueryPolicy")),
                () -> assertTrue(adapter.contains("lexicalQueryPolicy.webSearchQuery(query)")),
                () -> assertFalse(adapter.contains("TOKEN_PATTERN")),
                () -> assertFalse(adapter.contains("private List<String> tokenize(")),
                () -> assertFalse(adapter.contains("private String lexicalQuery(")),
                () -> assertFalse(adapter.contains(".limit(12)")),
                () -> assertFalse(adapter.contains("String.join(\" OR \", terms)")),
                () -> assertTrue(adapter.contains("websearch_to_tsquery")),
                () -> assertTrue(adapter.contains("JSON.parseObject(")),
                () -> assertTrue(adapter.contains("safeTableName(")));
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
