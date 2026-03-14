package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsMainAgentCoordinator;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.model.ModelAvailabilitySnapshot;
import cn.lgs.orbisops.trigger.application.chat.OpsChatRequestPreparationFacade;
import cn.lgs.orbisops.trigger.application.chat.OpsPreparedChatRequest;
import cn.lgs.orbisops.trigger.application.chatsession.OpsChatSessionApplicationFacade;
import cn.lgs.orbisops.trigger.application.worksession.OpsWorkSessionRunApplicationFacade;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionCreateRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionUpdateRequest;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Service
public class OpsChatApplicationService {

    private final ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession;
    private final OpsChatSessionApplicationFacade chatSessionFacade;
    private final ModelAvailabilityPort aiModelAvailability;
    private final OpsAgentLlmClient opsAgentLlmClient;
    private final OpsChatRequestPreparationFacade requestPreparationFacade;
    private final OpsWorkSessionRunApplicationFacade workSessionRunFacade;
    private final OpsChatMainAgentExecutionFacade mainAgentExecutionFacade;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("opsSubAgentExecutor")
    private Executor workSessionResumeExecutor;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OpsConfiguredChatModelAvailabilityService configuredChatModelAvailability;

    private final OpsChatIncidentPromotionService incidentPromotionService;

    public OpsChatApplicationService(
                                     ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
                                     OpsChatSessionService chatSessionService,
                                     ModelAvailabilityPort aiModelAvailability,
                                     OpsAgentLlmClient opsAgentLlmClient,
                                     ProjectDefinitionApplicationService projectDefinitionService,
                                     GraphEventApplicationService graphEventService,
                                     OpsRunCancellationRegistry cancellationRegistry,
                                     OpsWorkSessionRunAdapter workSessionRunService,
                                     OpsMainAgentCoordinator mainAgentCoordinator) {
        this(executeWorkSession, chatSessionService, aiModelAvailability, opsAgentLlmClient,
                projectDefinitionService, graphEventService, cancellationRegistry, workSessionRunService,
                mainAgentCoordinator, null);
    }

    public OpsChatApplicationService(
                                     ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
                                     OpsChatSessionService chatSessionService,
                                     ModelAvailabilityPort aiModelAvailability,
                                     OpsAgentLlmClient opsAgentLlmClient,
                                     ProjectDefinitionApplicationService projectDefinitionService,
                                     GraphEventApplicationService graphEventService,
                                     OpsRunCancellationRegistry cancellationRegistry,
                                     OpsWorkSessionRunAdapter workSessionRunService,
                                     OpsMainAgentCoordinator mainAgentCoordinator,
                                     OpsChatIncidentPromotionService incidentPromotionService) {
        this(executeWorkSession, chatSessionService, aiModelAvailability, opsAgentLlmClient,
                projectDefinitionService, graphEventService, cancellationRegistry, workSessionRunService,
                mainAgentCoordinator, incidentPromotionService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpsChatApplicationService(
                                     ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
                                     OpsChatSessionService chatSessionService,
                                     ModelAvailabilityPort aiModelAvailability,
                                     OpsAgentLlmClient opsAgentLlmClient,
                                     ProjectDefinitionApplicationService projectDefinitionService,
                                     GraphEventApplicationService graphEventService,
                                     OpsRunCancellationRegistry cancellationRegistry,
                                     OpsWorkSessionRunAdapter workSessionRunService,
                                     OpsMainAgentCoordinator mainAgentCoordinator,
                                     OpsChatIncidentPromotionService incidentPromotionService,
                                     OpsWorkSessionRunApplicationFacade workSessionRunFacade) {
        this.executeWorkSession = executeWorkSession;
        this.chatSessionFacade = new OpsChatSessionApplicationFacade(
                chatSessionService,
                projectDefinitionService,
                graphEventService);
        this.aiModelAvailability = aiModelAvailability;
        this.opsAgentLlmClient = opsAgentLlmClient;
        this.requestPreparationFacade = new OpsChatRequestPreparationFacade(
                projectDefinitionService,
                chatSessionFacade);
        this.workSessionRunFacade = workSessionRunFacade != null
                ? workSessionRunFacade
                : new OpsWorkSessionRunApplicationFacade(
                        workSessionRunService,
                        cancellationRegistry,
                        request -> {
                            if (workSessionResumeExecutor == null) {
                                throw new IllegalStateException("WORK_SESSION_RESUME_EXECUTOR_UNAVAILABLE");
                            }
                            workSessionResumeExecutor.execute(() -> executeWorkSession.execute(request));
                        },
                        graphEventService);
        this.mainAgentExecutionFacade = new OpsChatMainAgentExecutionFacade(mainAgentCoordinator);
        this.incidentPromotionService = incidentPromotionService;
    }

    public OpsChatSession createSession(OpsChatSessionCreateRequest request, String scopedUserId) {
        return chatSessionFacade.create(request, scopedUserId);
    }

    public OpsChatSession updateSession(String sessionId, OpsChatSessionUpdateRequest request, String scopedUserId) {
        return chatSessionFacade.update(sessionId, request, scopedUserId);
    }

    public List<OpsChatSessionService.SessionParticipant> sessionParticipants(String sessionId, String actor) {
        return chatSessionFacade.participants(sessionId, actor);
    }

    public List<OpsChatSessionService.SessionParticipant> replaceSessionParticipants(
            String sessionId,
            long expectedSessionVersion,
            List<OpsChatSessionService.SessionParticipantInput> participants,
            String actor) {
        return chatSessionFacade.replaceParticipants(
                sessionId,
                expectedSessionVersion,
                participants,
                actor);
    }

    public List<OpsChatSession> listSessions(String requestedUserId,
                                             String agentId,
                                             String keyword,
                                             Boolean favorite,
                                             Integer limit,
                                             String scopedUserId) {
        return chatSessionFacade.list(
                requestedUserId,
                agentId,
                keyword,
                favorite,
                limit,
                scopedUserId);
    }

    public List<OpsChatMessageView> messages(String sessionId, Integer limit, String scopedUserId) {
        return chatSessionFacade.messages(sessionId, limit, scopedUserId);
    }

    public List<GraphEvent> events(String sessionId, String scopedUserId) {
        return chatSessionFacade.events(sessionId, scopedUserId);
    }

    public List<GraphEvent> runEvents(String runId, long afterSequence, int limit, String projectId) {
        return workSessionRunFacade.events(runId, afterSequence, limit, projectId);
    }

    public List<GraphEvent> runEventsForActor(String runId, long afterSequence, int limit, String projectId, String actor) {
        return workSessionRunFacade.eventsForActor(
                runId,
                afterSequence,
                limit,
                projectId,
                actor);
    }

    public Map<String, Object> run(String runId, String projectId) {
        return workSessionRunFacade.run(runId, projectId);
    }

    public Map<String, Object> runForActor(String runId, String projectId, String actor) {
        return workSessionRunFacade.runForActor(runId, projectId, actor);
    }

    public Map<String, Object> resumeRun(String runId, String projectId, String actor) {
        return workSessionRunFacade.resume(runId, projectId, actor);
    }

    public boolean deleteSession(String sessionId, String scopedUserId) {
        return chatSessionFacade.delete(sessionId, scopedUserId);
    }

    public OpsAgentChatResponse chat(OpsAgentChatRequest request, String scopedUserId) {
        OpsPreparedChatRequest prepared = requestPreparationFacade.prepareOrCreate(
                request,
                scopedUserId,
                true);
        OpsChatSession session = ensureForChat(prepared.request());
        OpsAgentChatResponse response = promoteIncident(
                prepared.request(),
                executeThroughMainAgent(
                        prepared.request(),
                        null,
                        true));
        chatSessionFacade.touch(session, response);
        return response;
    }

    public void prepareStreamRequest(OpsAgentChatRequest request, String scopedUserId) {
        requestPreparationFacade.prepareRequired(request, scopedUserId, true);
    }

    public OpsChatSession ensureForChat(OpsAgentChatRequest request) {
        OpsChatSession session = chatSessionFacade.ensureForChat(request);
        requestPreparationFacade.applyBoundAgentExecutionStyle(request);
        return session;
    }

    public OpsAgentChatResponse executeStream(OpsAgentChatRequest request, Consumer<OpsRuntimeEvent> eventSink) {
        return promoteIncident(
                request,
                executeThroughMainAgent(
                        request,
                        eventSink,
                        false));
    }

    public void touch(OpsChatSession session, OpsAgentChatResponse response) {
        chatSessionFacade.touch(session, response);
    }

    public OpsAgentChatResponse testRunAgent(OpsAgentChatRequest request) {
        OpsPreparedChatRequest prepared = requestPreparationFacade.prepareOrCreate(
                request,
                "",
                false);
        return executeThroughMainAgent(
                prepared.request(),
                null,
                true);
    }

    public OpsAgentChatResponse adminChat(OpsAgentChatRequest request) {
        OpsPreparedChatRequest prepared = requestPreparationFacade.prepareOrCreate(
                request,
                "",
                false);
        OpsChatSession session = ensureForChat(prepared.request());
        OpsAgentChatResponse response = promoteIncident(
                prepared.request(),
                executeThroughMainAgent(
                        prepared.request(),
                        null,
                        true));
        chatSessionFacade.touch(session, response);
        return response;
    }

    public OpsAgentChatResponse executeAdminStream(OpsAgentChatRequest request, Consumer<OpsRuntimeEvent> eventSink) {
        OpsPreparedChatRequest prepared = requestPreparationFacade.prepareRequired(
                request,
                "",
                false);
        OpsChatSession session = ensureForChat(prepared.request());
        OpsAgentChatResponse response = promoteIncident(
                prepared.request(),
                executeThroughMainAgent(
                        prepared.request(),
                        eventSink,
                        false));
        chatSessionFacade.touch(session, response);
        return response;
    }

    public boolean cancelRun(String runId) {
        return workSessionRunFacade.cancelLocal(runId);
    }

    public boolean cancelRun(String runId, String projectId, String actor, String reason) {
        return workSessionRunFacade.requestCancel(runId, projectId, actor, reason);
    }

    public boolean cancelRunForActor(String runId, String actor, String reason) {
        return workSessionRunFacade.requestCancelForActor(runId, actor, reason);
    }

    private OpsAgentChatResponse executeThroughMainAgent(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink,
            boolean synchronous) {
        return mainAgentExecutionFacade.execute(
                request,
                eventSink,
                synchronous);
    }

    private OpsAgentChatResponse promoteIncident(
            OpsAgentChatRequest request,
            OpsAgentChatResponse response) {
        if (incidentPromotionService == null || response == null) return response;
        try {
            incidentPromotionService.promote(request, response);
        } catch (RuntimeException promotionFailure) {
            Map<String, Object> metadata = response.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(response.getMetadata());
            metadata.put("formalDiagnosisPromotionFailed", true);
            metadata.put("formalDiagnosisPromotionError", promotionFailure.getMessage());
            response.setMetadata(metadata);
        }
        return response;
    }

    public Map<String, Object> runtimeCapabilities() {
        return executeWorkSession.capabilities();
    }

    public Map<String, Object> modelStatus() {
        ModelAvailabilitySnapshot snapshot = aiModelAvailability.snapshot();
        Map<String, Object> data = modelAvailabilityView(snapshot);
        boolean fallbackChatAvailable = snapshot != null && snapshot.chatAvailable();
        Map<String, Object> configuredStatus = configuredChatModelAvailability == null
                ? Map.of("enabledConfiguredModelCount", 0,
                        "usableConfiguredModelCount", 0,
                        "configuredModelAvailable", false)
                : configuredChatModelAvailability.status();
        boolean configuredModelAvailable = Boolean.TRUE.equals(configuredStatus.get("configuredModelAvailable"));
        data.put("fallbackChatAvailable", fallbackChatAvailable);
        data.putAll(configuredStatus);
        data.put("chatAvailable", fallbackChatAvailable || configuredModelAvailable);
        if (!fallbackChatAvailable && configuredModelAvailable) {
            data.put("message", "模型管理中存在可用的已启用 Chat 模型，Agent Runtime 将按 modelId 使用该配置。");
        }
        data.put("opsAgentLlm", opsAgentLlmClient.status());
        return data;
    }

    private Map<String, Object> modelAvailabilityView(ModelAvailabilitySnapshot snapshot) {
        if (snapshot == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modelCallsEnabled", snapshot.modelCallsEnabled());
        data.put("openAiBaseUrl", snapshot.openAiBaseUrl());
        data.put("embeddingBaseUrl", snapshot.embeddingBaseUrl());
        data.put("chatModel", snapshot.chatModel());
        data.put("embeddingModel", snapshot.embeddingModel());
        data.put("rerankProvider", snapshot.rerankProvider());
        data.put("rerankBaseUrl", snapshot.rerankBaseUrl());
        data.put("rerankModel", snapshot.rerankModel());
        data.put("openAiApiKeyConfigured", snapshot.openAiApiKeyConfigured());
        data.put("openAiApiKeyUsable", snapshot.openAiApiKeyUsable());
        data.put("embeddingApiKeyConfigured", snapshot.embeddingApiKeyConfigured());
        data.put("embeddingApiKeyUsable", snapshot.embeddingApiKeyUsable());
        data.put("embeddingLocalEndpointReady", snapshot.embeddingLocalEndpointReady());
        data.put("rerankApiKeyConfigured", snapshot.rerankApiKeyConfigured());
        data.put("rerankApiKeyUsable", snapshot.rerankApiKeyUsable());
        data.put("rerankLocalEndpointReady", snapshot.rerankLocalEndpointReady());
        data.put("chatAvailable", snapshot.chatAvailable());
        data.put("embeddingAvailable", snapshot.embeddingAvailable());
        data.put("rerankAvailable", snapshot.rerankAvailable());
        data.put("message", snapshot.message());
        return data;
    }

}
