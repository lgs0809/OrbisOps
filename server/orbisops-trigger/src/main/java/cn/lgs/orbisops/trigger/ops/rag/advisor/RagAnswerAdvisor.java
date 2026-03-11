package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RagAnswerAdvisor implements BaseAdvisor {

    private final RagRetrievalSettings ragAnswer;
    private final RagRetrievalOrchestrator retrievalOrchestrator;
    private final RagContextRenderer contextRenderer;
    private final RagRetrievalPlanPolicy retrievalPlanPolicy;

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
        this(vectorStore, searchRequest, null, null, null);
    }

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest, RagRetrievalSettings ragAnswer, IRagKnowledgeRepository ragKnowledgeRepository) {
        this(vectorStore, searchRequest, ragAnswer, ragKnowledgeRepository, null);
    }

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest, RagRetrievalSettings ragAnswer, IRagKnowledgeRepository ragKnowledgeRepository, RagMultimodalEmbeddingService ragMultimodalEmbeddingService) {
        this(vectorStore, searchRequest, ragAnswer, ragKnowledgeRepository, ragMultimodalEmbeddingService, null);
    }

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest, RagRetrievalSettings ragAnswer, IRagKnowledgeRepository ragKnowledgeRepository, RagMultimodalEmbeddingService ragMultimodalEmbeddingService, EmbeddingModel embeddingModel) {
        this.ragAnswer = ragAnswer == null ? new RagRetrievalSettings() : ragAnswer;
        RagRecallCoordinator recallCoordinator = new RagRecallCoordinator(
                vectorStore,
                searchRequest,
                ragKnowledgeRepository,
                ragMultimodalEmbeddingService,
                embeddingModel);
        this.retrievalOrchestrator = new RagRetrievalOrchestrator(this.ragAnswer, recallCoordinator);
        this.contextRenderer = new RagContextRenderer();
        this.retrievalPlanPolicy = new RagRetrievalPlanPolicy();
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        HashMap<String, Object> context = new HashMap<>(chatClientRequest.context());

        String userText = chatClientRequest.prompt().getUserMessage().getText();
        RagRetrievalPlan plan = retrievalPlanPolicy.resolve(
                userText,
                context,
                ragAnswer,
                SearchRequest.DEFAULT_TOP_K);
        List<Document> documents = retrievalOrchestrator.retrieve(userText, context, plan);
        context.put("qa_retrieved_documents", documents);
        context.put("qa_retrieval_plan", plan);

        RagContextRenderer.RenderedContext renderedContext = contextRenderer.render(
                userText,
                chatClientRequest.context(),
                documents,
                plan);
        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(
                        new UserMessage(renderedContext.advisedUserText()),
                        new AssistantMessage(renderedContext.serializedParameters())).build())
                .context(renderedContext.parameters())
                .build();
    }

    public List<Document> retrieve(String userText, Map<String, Object> context) {
        Map<String, Object> safeContext = context == null ? new HashMap<>() : new HashMap<>(context);
        RagRetrievalPlan plan = retrievalPlanPolicy.resolve(
                userText,
                safeContext,
                ragAnswer,
                SearchRequest.DEFAULT_TOP_K);
        List<Document> documents = retrievalOrchestrator.retrieve(userText, safeContext, plan);
        if (context != null) {
            try {
                context.putAll(safeContext);
            } catch (UnsupportedOperationException ignored) {
                // Callers may pass immutable context maps; diagnostics are best-effort.
            }
        }
        return documents;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        ChatResponse.Builder chatResponseBuilder = ChatResponse.builder().from(chatClientResponse.chatResponse());
        chatResponseBuilder.metadata("qa_retrieved_documents", chatClientResponse.context().get("qa_retrieved_documents"));
        chatResponseBuilder.metadata("qa_retrieval_plan", chatClientResponse.context().get("qa_retrieval_plan"));
        ChatResponse chatResponse = chatResponseBuilder.build();

        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(chatClientResponse.context())
                .build();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(this.before(chatClientRequest, callAdvisorChain));
        return this.after(chatClientResponse, callAdvisorChain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(chatClientRequest, streamAdvisorChain);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

}
