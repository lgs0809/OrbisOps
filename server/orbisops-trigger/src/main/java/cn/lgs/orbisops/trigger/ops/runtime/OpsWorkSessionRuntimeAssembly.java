package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.runtime.OpsTaskContextAdapter;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;

import java.util.Objects;
import java.util.function.Supplier;

/** Immutable runtime component graph consumed by the Work Session lifecycle coordinator. */
record OpsWorkSessionRuntimeAssembly(
        OpsAgentRuntimeRuleRouter ruleRouter,
        OpsTelemetryService telemetryService,
        OpsWorkSessionRunAdapter workSessionRunService,
        OpsWorkSessionRequestControl requestControl,
        OpsWorkSessionFinalizer workSessionFinalizer,
        OpsAnalysisRuntimeStateManager analysisStateManager,
        OpsRuntimeEventJournal runtimeEventJournal,
        OpsRuntimeConversationContextCoordinator conversationContextCoordinator,
        OpsRuntimeSkillLearningCoordinator skillLearningCoordinator,
        OpsRuntimeEngineDispatcher engineDispatcher,
        Supplier<OpsTaskContextAdapter> taskContextServiceSupplier) {

    OpsWorkSessionRuntimeAssembly {
        Objects.requireNonNull(ruleRouter, "ruleRouter");
        Objects.requireNonNull(telemetryService, "telemetryService");
        Objects.requireNonNull(workSessionRunService, "workSessionRunService");
        Objects.requireNonNull(requestControl, "requestControl");
        Objects.requireNonNull(workSessionFinalizer, "workSessionFinalizer");
        Objects.requireNonNull(analysisStateManager, "analysisStateManager");
        Objects.requireNonNull(runtimeEventJournal, "runtimeEventJournal");
        Objects.requireNonNull(conversationContextCoordinator, "conversationContextCoordinator");
        Objects.requireNonNull(skillLearningCoordinator, "skillLearningCoordinator");
        Objects.requireNonNull(engineDispatcher, "engineDispatcher");
        Objects.requireNonNull(taskContextServiceSupplier, "taskContextServiceSupplier");
    }
}
