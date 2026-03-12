package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExtractionDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/MemoryExtractionPolicy.java";
    private static final String DRAFT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/MemoryExtractionDraft.java";
    private static final String EXTRACTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryExtractor.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsColdMemoryMapper.java";

    @Test
    void domainPolicyOwnsDeterministicClassificationFilteringDefaultsDedupAndLimit() throws IOException {
        String policy = read(POLICY);
        String draft = read(DRAFT);

        assertAll(
                () -> assertTrue(draft.contains("record MemoryExtractionDraft")),
                () -> assertTrue(policy.contains("PREFERENCE_PATTERN")),
                () -> assertTrue(policy.contains("WORKFLOW_PATTERN")),
                () -> assertTrue(policy.contains("DOMAIN_FOCUS_PATTERN")),
                () -> assertTrue(policy.contains("PROJECT_CONTEXT_PATTERN")),
                () -> assertTrue(policy.contains("PROJECT_CONVENTION_PATTERN")),
                () -> assertTrue(policy.contains("PROJECT_GLOSSARY_PATTERN")),
                () -> assertTrue(policy.contains("HARD_POLICY_PATTERN")),
                () -> assertTrue(policy.contains("TRANSIENT_EVIDENCE_PATTERN")),
                () -> assertTrue(policy.contains("ruleDrafts(")),
                () -> assertTrue(policy.contains("materialize(")),
                () -> assertTrue(policy.contains("normalizeType(")),
                () -> assertTrue(policy.contains("normalizeScope(")),
                () -> assertTrue(policy.contains("normalizeImportance(")),
                () -> assertTrue(policy.contains("rejectAsLongTermMemory(")),
                () -> assertTrue(policy.contains("distinctAndLimit(")),
                () -> assertTrue(policy.contains("metadata.put(\"tokenEstimate\"")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("OpsMemoryItem")));
    }

    @Test
    void triggerExtractorOnlyOwnsCompatibilityConfigurationAndMapping() throws IOException {
        String extractor = read(EXTRACTOR);
        String mapper = read(MAPPER);

        assertAll(
                () -> assertTrue(extractor.contains("MemoryExtractionApplicationService")),
                () -> assertTrue(extractor.contains("new MemoryExtractionCommand(")),
                () -> assertTrue(extractor.contains("applicationService.extract(")),
                () -> assertTrue(extractor.contains("memoryMapper.candidateViews(")),
                () -> assertFalse(extractor.contains("MemoryExtractionPolicy")),
                () -> assertFalse(extractor.contains("MemoryContentHashPolicy")),
                () -> assertFalse(extractor.contains("ChatClient")),
                () -> assertFalse(extractor.contains("ChatModel")),
                () -> assertFalse(extractor.contains("com.alibaba.fastjson")),
                () -> assertFalse(extractor.contains("PREFERENCE_PATTERN")),
                () -> assertFalse(extractor.contains("WORKFLOW_PATTERN")),
                () -> assertFalse(extractor.contains("HARD_POLICY_PATTERN")),
                () -> assertFalse(extractor.contains("TRANSIENT_EVIDENCE_PATTERN")),
                () -> assertFalse(extractor.contains("OpsMemoryItem.builder()")),
                () -> assertFalse(extractor.contains("metadata.put(\"scopeType\"")),
                () -> assertTrue(mapper.contains("candidateViews(List<MemoryItemCandidate> items)")),
                () -> assertTrue(mapper.contains("candidateView(MemoryItemCandidate item)")));
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
