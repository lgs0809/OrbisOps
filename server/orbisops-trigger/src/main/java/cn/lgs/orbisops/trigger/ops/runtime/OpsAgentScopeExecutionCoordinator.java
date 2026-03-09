package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Provides the canonical top-level and Graph-node AgentScope execution boundary. */
final class OpsAgentScopeExecutionCoordinator {

    private final OpsAgentScopeExecutor executor;
    private final OpsRuntimeConversationContextCoordinator conversationContextCoordinator;
    private final Consumer<OpsAgentChatRequest> cancellationCheck;
    private final Supplier<ToolLoopCoordinator> toolLoopCoordinatorSupplier;

    OpsAgentScopeExecutionCoordinator(
            OpsAgentScopeExecutor executor,
            OpsRuntimeConversationContextCoordinator conversationContextCoordinator,
            Consumer<OpsAgentChatRequest> cancellationCheck,
            Supplier<ToolLoopCoordinator> toolLoopCoordinatorSupplier) {
        this.executor = executor;
        this.conversationContextCoordinator = conversationContextCoordinator;
        this.cancellationCheck = cancellationCheck;
        this.toolLoopCoordinatorSupplier = toolLoopCoordinatorSupplier;
    }

    String execute(OpsAgentDefinition definition,
                   OpsAgentChatRequest request,
                   String input,
                   List<OpsRuntimeEvent> events,
                   Consumer<OpsRuntimeEvent> eventSink) {
        return executor.execute(
                definition,
                request,
                input,
                events,
                eventSink,
                hooks());
    }

    boolean isMeaningfulText(String text) {
        return executor.isMeaningfulText(text);
    }

    private OpsAgentScopeExecutor.Hooks hooks() {
        return new OpsAgentScopeExecutor.Hooks() {
            @Override
            public void assertNotCanceled(OpsAgentChatRequest request) {
                cancellationCheck.accept(request);
            }

            @Override
            public boolean hasPreparedMemoryContext(OpsAgentChatRequest request) {
                return conversationContextCoordinator.hasMemoryContext(request);
            }

            @Override
            public String memoryContext(OpsAgentChatRequest request) {
                return conversationContextCoordinator.memoryContext(request);
            }

            @Override
            public ToolLoopCoordinator toolLoopCoordinator() {
                return toolLoopCoordinatorSupplier == null
                        ? null
                        : toolLoopCoordinatorSupplier.get();
            }
        };
    }
}
