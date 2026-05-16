package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsRuntimeResourcePipelineTest {

    @Test
    void ruleNamesMustExposeTheSingleSupportedProductionOrder() {
        OpsRuntimeResourcePipeline pipeline = pipeline(new Fixture());

        assertEquals(List.of(
                        "MODEL",
                        "MCP",
                        "SKILL",
                        "KNOWLEDGE_BASE",
                        "BUILT_IN_TOOLS",
                        "SUB_AGENT_TOOL_BOUNDARY",
                        "TOOL_TRACE",
                        "SUMMARY"),
                pipeline.ruleNames());
    }

    @Test
    void assembleMustInvokeEveryBoundaryInTheRequiredOrder() {
        Fixture fixture = new Fixture();
        ChatModel chatModel = mock(ChatModel.class);
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .request(OpsAgentChatRequest.builder().projectId("project-1").build())
                .projectId("project-1")
                .events(new ArrayList<>())
                .build();
        when(fixture.modelResolver.resolve(context)).thenReturn(chatModel);
        OpsRuntimeResourcePipeline pipeline = pipeline(fixture);

        OpsRuntimeResourceBundle bundle = pipeline.assemble(context);

        assertSame(chatModel, bundle.getChatModel());
        InOrder order = inOrder(
                fixture.modelResolver,
                fixture.mcpResolver,
                fixture.skillResolver,
                fixture.knowledgeResolver,
                fixture.builtInToolContributor,
                fixture.subAgentToolBoundaryPolicy,
                fixture.toolTraceDecorator,
                fixture.summaryAuditor);
        order.verify(fixture.modelResolver).resolve(context);
        order.verify(fixture.mcpResolver).resolve(context);
        order.verify(fixture.skillResolver).resolve(context);
        order.verify(fixture.knowledgeResolver).resolve(context);
        order.verify(fixture.builtInToolContributor).contribute(context);
        order.verify(fixture.subAgentToolBoundaryPolicy).enforce(context);
        order.verify(fixture.toolTraceDecorator).decorate(context);
        order.verify(fixture.summaryAuditor).summarize(context);
    }

    @Test
    void nullContextMustBeRejectedBeforeAnyRuleRuns() {
        OpsRuntimeResourcePipeline pipeline = pipeline(new Fixture());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.assemble(null));

        assertEquals("RUNTIME_RESOURCE_CONTEXT_REQUIRED", error.getMessage());
    }

    private OpsRuntimeResourcePipeline pipeline(Fixture fixture) {
        return new OpsRuntimeResourcePipeline(
                fixture.modelResolver,
                fixture.mcpResolver,
                fixture.skillResolver,
                fixture.builtInToolContributor,
                fixture.knowledgeResolver,
                fixture.subAgentToolBoundaryPolicy,
                fixture.toolTraceDecorator,
                fixture.summaryAuditor);
    }

    private static final class Fixture {
        private final OpsRuntimeModelResolver modelResolver =
                mock(OpsRuntimeModelResolver.class);
        private final OpsRuntimeMcpResolver mcpResolver =
                mock(OpsRuntimeMcpResolver.class);
        private final OpsRuntimeSkillResolver skillResolver =
                mock(OpsRuntimeSkillResolver.class);
        private final OpsRuntimeBuiltInToolContributor builtInToolContributor =
                mock(OpsRuntimeBuiltInToolContributor.class);
        private final OpsRuntimeKnowledgeResolver knowledgeResolver =
                mock(OpsRuntimeKnowledgeResolver.class);
        private final OpsRuntimeSubAgentToolBoundaryPolicy subAgentToolBoundaryPolicy =
                mock(OpsRuntimeSubAgentToolBoundaryPolicy.class);
        private final OpsRuntimeToolTraceDecorator toolTraceDecorator =
                mock(OpsRuntimeToolTraceDecorator.class);
        private final OpsRuntimeResourceSummaryAuditor summaryAuditor =
                mock(OpsRuntimeResourceSummaryAuditor.class);
    }
}
