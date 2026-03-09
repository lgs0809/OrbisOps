package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolProvider;

import static org.mockito.Mockito.mock;

/** Creates the production-shaped analysis state manager graph for tests. */
final class OpsAnalysisRuntimeStateManagerTestFactory {

    private OpsAnalysisRuntimeStateManagerTestFactory() {
    }

    static OpsAnalysisRuntimeStateManager create() {
        return create(
                mock(GraphEventApplicationService.class),
                mock(OpsRunCancellationRegistry.class),
                null);
    }

    static OpsAnalysisRuntimeStateManager create(
            GraphEventApplicationService graphEventService,
            OpsRunCancellationRegistry cancellationRegistry) {
        return create(graphEventService, cancellationRegistry, null);
    }

    static OpsAnalysisRuntimeStateManager create(
            GraphEventApplicationService graphEventService,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsSkillToolProvider skillToolProvider) {
        OpsAnalysisRuntimeEventRecorder eventRecorder =
                new OpsAnalysisRuntimeEventRecorder(
                        graphEventService,
                        cancellationRegistry);
        return new OpsAnalysisRuntimeStateManager(
                new OpsAnalysisRuntimeStateFactory(
                        new OpsAnalysisSkillReadinessInspector(
                                () -> skillToolProvider)),
                eventRecorder,
                new OpsAnalysisResponseNotes());
    }
}
