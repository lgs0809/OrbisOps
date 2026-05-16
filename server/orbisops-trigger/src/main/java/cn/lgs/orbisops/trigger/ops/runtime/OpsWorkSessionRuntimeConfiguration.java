package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRepositoryPort;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import cn.lgs.orbisops.trigger.application.runtime.OpsTaskContextAdapter;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmSettings;
import cn.lgs.orbisops.trigger.ops.OpsAnalysisReportComposer;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Spring composition root for the controlled Work Session runtime component graph. */
@Configuration(proxyBeanMethods = false)
public class OpsWorkSessionRuntimeConfiguration {

    @Bean
    WorkflowApprovalApplicationService workflowApprovalApplicationService(
            WorkflowApprovalRepositoryPort repository) {
        return new WorkflowApprovalApplicationService(repository);
    }

    @Bean
    OpsWorkSessionLifecycleCoordinator opsWorkSessionLifecycleCoordinator(
            OpsWorkSessionRuntimeAssembly runtimeAssembly) {
        return new OpsWorkSessionLifecycleCoordinator(runtimeAssembly);
    }

    @Bean
    UnifiedAgentRuntime unifiedAgentRuntime(
            OpsWorkSessionLifecycleCoordinator lifecycleCoordinator) {
        return new UnifiedAgentRuntime(lifecycleCoordinator);
    }

    @Bean
    OpsWorkSessionRuntimeAssembly opsWorkSessionRuntimeAssembly(
            OpsAgentDefinitionQueryGateway definitionRegistry,
            OpsAgentRuntimeRuleRouter ruleRouter,
            List<OpsEngineAdapter> engineAdapterList,
            OpsConversationMemoryService memoryService,
            OpsNodeRagService nodeRagService,
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsAgentDefinitionValidator definitionValidator,
            OpsRuntimeWorkflowBindingAdapter runtimeWorkflowBindingAdapter,
            OpsDurableWorkflowRuntimeCoordinator durableWorkflowRuntimeCoordinator,
            OpsRuntimeContextBundleAdapter runtimeContextBundleAdapter,
            WorkflowApprovalApplicationService workflowApprovalApplicationService,
            OpsWorkflowApprovalChannelBridge workflowApprovalChannelBridge,
            OpsTypedWorkflowSettings typedWorkflowSettings,
            OpsTelemetryService telemetryService,
            GraphEventApplicationService graphEventService,
            ObjectProvider<SkillRuntimeToolProvider> skillToolProvider,
            ModelAvailabilityPort aiModelAvailability,
            OpsMainAgentPlanner planner,
            OpsInvestigationExecutor investigationExecutor,
            OpsAnalysisReportComposer analysisReportComposer,
            OpsChannelNotificationService channelNotificationService,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsWorkSessionRunAdapter workSessionRunService,
            OpsWorkSessionContextPreparationService contextPreparationService,
            OpsWorkSessionFinalizer workSessionFinalizer,
            OpsChannelRunProgressProjector channelRunProgressProjector,
            ObjectProvider<OpsConfigAuditService> configAuditService,
            ObjectProvider<OpsTaskContextAdapter> taskContextService,
            ObjectProvider<OpsAgentGraphCompilerAdapter> graphCompilerAdapter,
            ObjectProvider<OpsChatApplicationService> chatApplicationService,
            OpsWorkflowChangeContextReader changeContextReader,
            ObjectProvider<OpsNodeExecutionStrategyRegistry> nodeExecutionStrategyRegistry,
            ObjectProvider<ToolLoopCoordinator> toolLoopCoordinator,
            ObjectProvider<OpsSkillRuntimeUsageRecorder> skillRuntimeUsageRecorder,
            @Qualifier("opsSubAgentExecutor") Executor opsSubAgentExecutor,
            @Qualifier("opsModelCallExecutor") ExecutorService opsModelCallExecutor,
            OpsAgentLlmSettings llmSettings, OpsMcpDiscoveryPolicy discoveryPolicy) {
        OpsWorkSessionRunAdapter requiredRunService = Objects.requireNonNull(
                workSessionRunService,
                "OpsWorkSessionRunAdapter 不能为空；Work Session 不允许使用内存运行状态降级");
        OpsWorkSessionContextPreparationService requiredContextPreparationService =
                Objects.requireNonNull(
                        contextPreparationService,
                        "OpsWorkSessionContextPreparationService 不能为空；Work Session 不允许绕过上下文准备");
        OpsWorkSessionFinalizer requiredFinalizer = Objects.requireNonNull(
                workSessionFinalizer,
                "OpsWorkSessionFinalizer 不能为空；Work Session 不允许绕过统一收尾");

        OpsAnalysisRoutingPolicy analysisRoutingPolicy = new OpsAnalysisRoutingPolicy();
        OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy =
                new OpsRuntimeNodeExecutionPolicy(analysisRoutingPolicy);
        OpsRuntimePromptAssembler promptAssembler = new OpsRuntimePromptAssembler();
        OpsRuntimeLlmInvoker llmInvoker = new OpsRuntimeLlmInvoker(
                aiModelAvailability,
                opsModelCallExecutor,
                llmSettings.modelCallTimeoutSeconds());
        OpsWorkSessionRequestControl requestControl = new OpsWorkSessionRequestControl(
                definitionRegistry,
                cancellationRegistry,
                requiredRunService);
        OpsRuntimeEventJournal runtimeEventJournal = new OpsRuntimeEventJournal(
                requiredRunService,
                graphEventService,
                configAuditService::getIfAvailable,
                channelRunProgressProjector);
        OpsTypedWorkflowExecutionCoordinator typedWorkflowCoordinator =
                new OpsTypedWorkflowExecutionCoordinator(
                        definitionValidator,
                        runtimeWorkflowBindingAdapter,
                        durableWorkflowRuntimeCoordinator,
                        resourceAssembler,
                        runtimeContextBundleAdapter,
                        workflowApprovalApplicationService,
                        workflowApprovalChannelBridge,
                        typedWorkflowSettings,
                        runtimeEventJournal);
        OpsRuntimeConversationContextCoordinator conversationContextCoordinator =
                new OpsRuntimeConversationContextCoordinator(
                        requiredContextPreparationService,
                        memoryService,
                        runtimeEventJournal);
        OpsChatEngineExecutionCoordinator chatEngineExecutionCoordinator =
                new OpsChatEngineExecutionCoordinator(
                        resourceAssembler,
                        nodeRagService,
                        promptAssembler,
                        llmInvoker,
                        conversationContextCoordinator,
                        runtimeEventJournal);
        OpsRuntimeSkillLearningCoordinator skillLearningCoordinator =
                new OpsRuntimeSkillLearningCoordinator(
                        runtimeEventJournal,
                        skillRuntimeUsageRecorder::getIfAvailable);
        OpsAgentScopeExecutor agentScopeExecutor = new OpsAgentScopeExecutor(
                resourceAssembler,
                nodeRagService,
                promptAssembler,
                opsSubAgentExecutor,
                opsModelCallExecutor,
                llmSettings.modelCallTimeoutSeconds(), discoveryPolicy);
        OpsAgentScopeExecutionCoordinator agentScopeExecutionCoordinator =
                new OpsAgentScopeExecutionCoordinator(
                        agentScopeExecutor,
                        conversationContextCoordinator,
                        requestControl::assertNotCanceled,
                        toolLoopCoordinator::getIfAvailable);
        OpsAnalysisRuntimeEventRecorder analysisEventRecorder =
                new OpsAnalysisRuntimeEventRecorder(
                        graphEventService,
                        cancellationRegistry);
        OpsAnalysisRuntimeStateManager analysisStateManager =
                new OpsAnalysisRuntimeStateManager(
                        new OpsAnalysisRuntimeStateFactory(
                                new OpsAnalysisSkillReadinessInspector(
                                        skillToolProvider::getIfAvailable)),
                        analysisEventRecorder,
                        new OpsAnalysisResponseNotes());
        OpsAnalysisChangePackageCoordinator changePackageCoordinator =
                new OpsAnalysisChangePackageCoordinator(
                        resourceAssembler,
                        promptAssembler,
                        llmInvoker,
                        analysisStateManager,
                        runtimeEventJournal,
                        graphEventService);
        OpsGraphRuntimeStateManager graphRuntimeStateManager =
                new OpsGraphRuntimeStateManager();
        OpsGraphConditionEvaluator conditionEvaluator =
                new OpsGraphConditionEvaluator();
        OpsGraphTopologyAssembler graphTopologyAssembler =
                new OpsGraphTopologyAssembler(
                        analysisRoutingPolicy,
                        conditionEvaluator,
                        graphRuntimeStateManager,
                        new OpsGraphFeedbackLoopPolicy(
                                analysisRoutingPolicy,
                                conditionEvaluator));
        OpsAnalysisPlanLoopCoordinator analysisPlanLoopCoordinator =
                new OpsAnalysisPlanLoopCoordinator(
                        planner,
                        investigationExecutor,
                        analysisStateManager,
                        analysisRoutingPolicy,
                        opsSubAgentExecutor);
        OpsAnalysisNodeExecutionCoordinator analysisNodeExecutionCoordinator =
                OpsAnalysisNodeExecutionAssembly.create(
                        planner,
                        investigationExecutor,
                        analysisReportComposer,
                        channelNotificationService,
                        analysisStateManager,
                        analysisPlanLoopCoordinator,
                        analysisRoutingPolicy,
                        graphRuntimeStateManager,
                        graphTopologyAssembler,
                        graphEventService);
        OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks = analysisNodeHooks(
                nodeExecutionPolicy,
                changePackageCoordinator,
                conversationContextCoordinator);
        OpsSubWorkflowNodeExecutor subWorkflowNodeExecutor = new OpsSubWorkflowNodeExecutor(
                definitionRegistry,
                chatApplicationService);
        OpsGraphNodeExecutionCoordinator graphNodeExecutionCoordinator =
                OpsGraphNodeExecutionAssembly.create(
                        resourceAssembler,
                        nodeRagService,
                        promptAssembler,
                        llmInvoker,
                        agentScopeExecutionCoordinator,
                        graphRuntimeStateManager,
                        graphTopologyAssembler,
                        analysisStateManager,
                        analysisNodeExecutionCoordinator,
                        analysisRoutingPolicy,
                        conditionEvaluator,
                        subWorkflowNodeExecutor,
                        telemetryService,
                        graphEventService,
                        changeContextReader);
        OpsGraphNodeExecutionCoordinator.Hooks graphNodeHooks = graphNodeHooks(
                requestControl,
                nodeExecutionPolicy,
                analysisNodeHooks,
                runtimeEventJournal);
        OpsGraphEngineExecutionCoordinator graphEngineExecutionCoordinator =
                new OpsGraphEngineExecutionCoordinator(
                        graphRuntimeStateManager,
                        analysisStateManager,
                        graphCompilerAdapter::getIfAvailable,
                        graphNodeExecutionCoordinator,
                        graphTopologyAssembler,
                        conversationContextCoordinator,
                        analysisRoutingPolicy,
                        skillLearningCoordinator,
                        runtimeEventJournal,
                        nodeExecutionPolicy,
                        typedWorkflowCoordinator,
                        opsSubAgentExecutor);
        OpsRuntimeEngineDispatcher engineDispatcher = new OpsRuntimeEngineDispatcher(
                engineAdapterList,
                nodeExecutionStrategyRegistry::getIfAvailable,
                chatEngineExecutionCoordinator,
                graphEngineExecutionCoordinator,
                agentScopeExecutionCoordinator,
                requestControl::assertNotCanceled,
                () -> graphNodeHooks);

        return new OpsWorkSessionRuntimeAssembly(
                ruleRouter,
                telemetryService,
                requiredRunService,
                requestControl,
                requiredFinalizer,
                analysisStateManager,
                runtimeEventJournal,
                conversationContextCoordinator,
                skillLearningCoordinator,
                engineDispatcher,
                taskContextService::getIfAvailable);
    }

    private OpsGraphNodeExecutionCoordinator.Hooks graphNodeHooks(
            OpsWorkSessionRequestControl requestControl,
            OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy,
            OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks,
            OpsRuntimeEventJournal runtimeEventJournal) {
        return new OpsGraphNodeExecutionCoordinator.Hooks() {
            @Override
            public void assertNotCanceled(OpsAgentChatRequest request) {
                requestControl.assertNotCanceled(request);
            }

            @Override
            public String executionNodeType(OpsWorkflowNode node) {
                return nodeExecutionPolicy.executionNodeType(node);
            }

            @Override
            public OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks() {
                return analysisNodeHooks;
            }

            @Override
            public long requestStartedNanos(OpsAgentChatRequest request) {
                return runtimeEventJournal.requestStartedNanos(request);
            }
        };
    }

    private OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks(
            OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy,
            OpsAnalysisChangePackageCoordinator changePackageCoordinator,
            OpsRuntimeConversationContextCoordinator conversationContextCoordinator) {
        return new OpsAnalysisNodeExecutionCoordinator.Hooks() {
            @Override
            public String executionNodeType(OpsWorkflowNode node) {
                return nodeExecutionPolicy.executionNodeType(node);
            }

            @Override
            public String evaluateChangePackage(
                    OpsAgentDefinition definition,
                    OpsWorkflowNode node,
                    OpsAgentChatRequest request,
                    OpsAnalysisResponseDTO response,
                    List<OpsRuntimeEvent> events,
                    Consumer<OpsRuntimeEvent> eventSink) {
                return changePackageCoordinator.evaluate(
                        definition,
                        node,
                        request,
                        response,
                        events,
                        eventSink,
                        conversationContextCoordinator.originalUserQuery(request),
                        nodeExecutionPolicy.executionNodeType(node));
            }
        };
    }
}
