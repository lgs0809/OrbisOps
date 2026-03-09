package cn.lgs.orbisops.trigger.ops.rag.advisor;

import com.alibaba.fastjson.JSON;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders retrieved documents into the Spring AI advisor prompt/context contract.
 */
public final class RagContextRenderer {

    private static final int MIN_TRUNCATED_DOCUMENT_CHARS = 300;
    private static final String USER_TEXT_ADVISE = "\nContext information is below, surrounded by ---------------------\n\n"
            + "---------------------\n"
            + "{question_answer_context}\n"
            + "---------------------\n\n"
            + "Given the context and provided history information and not prior knowledge,\n"
            + "reply to the user comment. If the answer is not in the context, inform\n"
            + "the user that you can't answer the question.\n";

    public RenderedContext render(String userText,
                                  Map<String, Object> requestContext,
                                  List<Document> documents,
                                  RagRetrievalPlan plan) {
        List<Document> limitedDocuments = limitDocuments(
                documents == null ? List.of() : documents,
                plan.maxContextChars());
        String documentContext = limitedDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));
        Map<String, Object> parameters = new HashMap<>(
                requestContext == null ? Map.of() : requestContext);
        parameters.put("question_answer_context", documentContext);
        parameters.put("qa_retrieval_plan", plan);
        String advisedUserText = userText + System.lineSeparator() + USER_TEXT_ADVISE;
        return new RenderedContext(
                advisedUserText,
                documentContext,
                parameters,
                JSON.toJSONString(parameters),
                limitedDocuments);
    }

    List<Document> limitDocuments(List<Document> documents,
                                  int maxContextChars) {
        List<Document> limited = new ArrayList<>();
        int total = 0;
        for (Document document : documents) {
            String text = document.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            int remaining = maxContextChars - total;
            if (remaining <= 0) {
                break;
            }
            if (text.length() <= remaining) {
                limited.add(document);
                total += text.length();
            } else if (remaining > MIN_TRUNCATED_DOCUMENT_CHARS) {
                limited.add(new Document(
                        document.getId(),
                        text.substring(0, remaining),
                        document.getMetadata()));
                break;
            }
        }
        return List.copyOf(limited);
    }

    public record RenderedContext(String advisedUserText,
                                  String documentContext,
                                  Map<String, Object> parameters,
                                  String serializedParameters,
                                  List<Document> limitedDocuments) {
    }
}
