package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagChunkMaterializationBoundaryArchitectureTest {

    private static final String DRAFT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/model/RagChunkDraft.java";
    private static final String MATERIALIZER = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/service/RagChunkMaterializer.java";
    private static final String PARSER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParser.java";
    private static final String PROJECTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentProjector.java";

    @Test
    void domainChunkBoundaryMustRemainFrameworkNeutral() throws IOException {
        String draft = read(DRAFT);
        String materializer = read(MATERIALIZER);
        String core = draft + "\n" + materializer;

        assertAll(
                () -> assertTrue(draft.contains("record RagChunkDraft")),
                () -> assertTrue(draft.contains("STRUCTURE_BOUNDED")),
                () -> assertTrue(draft.contains("EXACT")),
                () -> assertTrue(materializer.contains("List<RagDocument> materialize")),
                () -> assertTrue(materializer.contains("UUID.nameUUIDFromBytes")),
                () -> assertTrue(materializer.contains("\"chunk_index\"")),
                () -> assertTrue(materializer.contains("\"oversized_structural_block\"")),
                () -> assertFalse(core.contains("org.springframework")),
                () -> assertFalse(core.contains("org.apache.pdfbox")),
                () -> assertFalse(core.contains("TikaDocumentReader")),
                () -> assertFalse(core.contains("org.apache.poi")),
                () -> assertFalse(core.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void triggerProjectorMustDelegateSharedMaterializationAndOnlyAdaptSpringDocument() throws IOException {
        String parser = read(PARSER);
        String projector = read(PROJECTOR);

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertFalse(parser.contains("RagChunkDraft")),
                () -> assertFalse(parser.contains("new Document(")),
                () -> assertTrue(projector.contains("RagChunkMaterializer")),
                () -> assertTrue(projector.contains("RagChunkDraft")),
                () -> assertTrue(projector.contains("chunkMaterializer.materialize(drafts)")),
                () -> assertTrue(projector.contains("new Document(document.id(), document.text(), document.metadata())")),
                () -> assertFalse(projector.contains("private void appendOversizedBlock")),
                () -> assertFalse(projector.contains("UUID.nameUUIDFromBytes")),
                () -> assertFalse(projector.contains("private Document newDocument")),
                () -> assertFalse(projector.contains("private int hardSplitOverlapChars")),
                () -> assertFalse(projector.contains("private Map<String, Object> withMetadata")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
