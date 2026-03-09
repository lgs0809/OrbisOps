package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextRenderingBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String RENDERER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagContextRenderer.java";

    @Test
    void promptTemplateBudgetDocumentJoiningAndJsonProjectionMustRemainInsideRenderer() throws IOException {
        String renderer = read(RENDERER);

        assertAll(
                () -> assertTrue(renderer.contains("class RagContextRenderer")),
                () -> assertTrue(renderer.contains("USER_TEXT_ADVISE")),
                () -> assertTrue(renderer.contains("{question_answer_context}")),
                () -> assertTrue(renderer.contains("If the answer is not in the context")),
                () -> assertTrue(renderer.contains("MIN_TRUNCATED_DOCUMENT_CHARS = 300")),
                () -> assertTrue(renderer.contains("plan.maxContextChars()")),
                () -> assertTrue(renderer.contains("StringUtils.hasText")),
                () -> assertTrue(renderer.contains("text.substring(0, remaining)")),
                () -> assertTrue(renderer.contains("Collectors.joining(System.lineSeparator())")),
                () -> assertTrue(renderer.contains("question_answer_context")),
                () -> assertTrue(renderer.contains("qa_retrieval_plan")),
                () -> assertTrue(renderer.contains("JSON.toJSONString(parameters)")),
                () -> assertTrue(renderer.contains("record RenderedContext")),
                () -> assertFalse(renderer.contains("IRagKnowledgeRepository")),
                () -> assertFalse(renderer.contains("VectorStore")),
                () -> assertFalse(renderer.contains("RagRecallCoordinator")),
                () -> assertFalse(renderer.contains("RagReciprocalRankFusion")),
                () -> assertFalse(renderer.contains("RagMmrDiversitySelector")),
                () -> assertFalse(renderer.contains("RagRerankProtocol")),
                () -> assertFalse(renderer.contains("ChatClientRequest")),
                () -> assertFalse(renderer.contains("ChatClientResponse")));
    }

    @Test
    void advisorMustDelegateRenderingWithoutOwningTemplateBudgetOrJson() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagContextRenderer contextRenderer")),
                () -> assertTrue(advisor.contains("contextRenderer.render")),
                () -> assertTrue(advisor.contains("renderedContext.advisedUserText()")),
                () -> assertTrue(advisor.contains("renderedContext.serializedParameters()")),
                () -> assertTrue(advisor.contains("renderedContext.parameters()")),
                () -> assertFalse(advisor.contains("userTextAdvise")),
                () -> assertFalse(advisor.contains("USER_TEXT_ADVISE")),
                () -> assertFalse(advisor.contains("limitContext")),
                () -> assertFalse(advisor.contains("question_answer_context")),
                () -> assertFalse(advisor.contains("JSON.toJSONString")),
                () -> assertFalse(advisor.contains("com.alibaba.fastjson")),
                () -> assertFalse(advisor.contains("maxContextChars()")),
                () -> assertTrue(advisor.contains("ChatClientRequest before")),
                () -> assertTrue(advisor.contains("ChatClientResponse after")),
                () -> assertTrue(advisor.lines().count() < 270));
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
