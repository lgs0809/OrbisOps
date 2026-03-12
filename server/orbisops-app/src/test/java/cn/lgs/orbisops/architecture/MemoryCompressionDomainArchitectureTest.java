package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCompressionDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/MemoryCompressionPolicy.java";
    private static final String PLAN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/MemoryCompressionPlan.java";
    private static final String SUMMARY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/MemoryCompressionSummary.java";
    private static final String PROJECTION = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/MemoryCompressionProjection.java";
    private static final String COMPRESSOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextCompressor.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsColdMemoryMapper.java";

    @Test
    void domainPolicyOwnsThresholdPartitionRuleSummaryProjectionAndContextTrim() throws IOException {
        String policy = read(POLICY);
        String plan = read(PLAN);
        String summary = read(SUMMARY);
        String projection = read(PROJECTION);

        assertAll(
                () -> assertTrue(plan.contains("record MemoryCompressionPlan")),
                () -> assertTrue(plan.contains("boolean required()")),
                () -> assertTrue(summary.contains("record MemoryCompressionSummary")),
                () -> assertTrue(projection.contains("record MemoryCompressionProjection")),
                () -> assertTrue(policy.contains("messages.size() < Math.max(4, thresholdMessages)")),
                () -> assertTrue(policy.contains("Math.max(2, Math.min(keepRecent, messages.size() - 1))")),
                () -> assertTrue(policy.contains("ruleSummary(")),
                () -> assertTrue(policy.contains("会话压缩摘要：共压缩")),
                () -> assertTrue(policy.contains("appendSection(builder, \"用户关注\"")),
                () -> assertTrue(policy.contains("appendSection(builder, \"已有结论\"")),
                () -> assertTrue(policy.contains("project(")),
                () -> assertTrue(policy.contains("summaryMetadata.put(\"memory_type\", \"summary\")")),
                () -> assertTrue(policy.contains("summaryMetadata.put(\"source\", \"ops_context_compressor\")")),
                () -> assertTrue(policy.contains("BigDecimal.valueOf(0.86D)")),
                () -> assertTrue(policy.contains("[\\\"summary\\\",\\\"conversation\\\"]")),
                () -> assertTrue(policy.contains("hashPolicy.stableHash(summary.content())")),
                () -> assertTrue(policy.contains("trimContext(")),
                () -> assertTrue(policy.contains("memory context compressed, metadata=")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("ChatClient")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("OpsMemoryMessage")),
                () -> assertFalse(policy.contains("OpsMemoryItem")));
    }

    @Test
    void triggerCompressorOnlyOwnsCompatibilityConfigurationAndTypedCommandMapping() throws IOException {
        String compressor = read(COMPRESSOR);
        String mapper = read(MAPPER);

        assertAll(
                () -> assertTrue(compressor.contains("MemoryCompressionApplicationService")),
                () -> assertTrue(compressor.contains("new MemoryCompressionCommand(")),
                () -> assertTrue(compressor.contains("applicationService.compress(")),
                () -> assertTrue(compressor.contains("applicationService.trimContext(")),
                () -> assertFalse(compressor.contains("MemoryCompressionPolicy")),
                () -> assertFalse(compressor.contains("MemoryContentHashPolicy")),
                () -> assertFalse(compressor.contains("ChatClient")),
                () -> assertFalse(compressor.contains("ChatModel")),
                () -> assertFalse(compressor.contains("ModelAvailabilityPort")),
                () -> assertFalse(compressor.contains("OpsHotMemoryStore")),
                () -> assertFalse(compressor.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(compressor.contains("recentMessages.size() < Math.max(4")),
                () -> assertFalse(compressor.contains("int keep = Math.max(2")),
                () -> assertFalse(compressor.contains("会话压缩摘要：共压缩")),
                () -> assertFalse(compressor.contains("OpsMemoryMessage.systemSummary(")),
                () -> assertFalse(compressor.contains("OpsMemoryItem.builder()")),
                () -> assertFalse(compressor.contains("memory context compressed, metadata=")),
                () -> assertTrue(mapper.contains("messageSnapshots(List<OpsMemoryMessage> messages)")),
                () -> assertTrue(mapper.contains("messageViews(List<ColdMemoryMessageSnapshot> messages)")),
                () -> assertTrue(mapper.contains("messageView(ColdMemoryMessageSnapshot message)")));
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
