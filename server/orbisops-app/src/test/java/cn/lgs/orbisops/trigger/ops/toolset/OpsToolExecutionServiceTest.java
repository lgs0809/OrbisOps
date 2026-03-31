package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.api.dto.TaskScheduleResponseDTO;
import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ReviewChangePackageUseCase;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.application.config.TaskScheduleApplicationService;
import cn.lgs.orbisops.trigger.application.evidence.OpsToolResultMapper;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionCatalogAdapter;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsChangePackageToolExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsCodeToolExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsCompositeToolExecutionDispatchAdapter;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsInspectionTaskExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsInternalToolInvoker;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsToolResultExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.ops.repair.OpsControlledCodeToolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsToolExecutionServiceTest {

    private ToolResultApplicationService typedResultsOverride;
    private TaskScheduleApplicationService taskScheduleOverride;

    @Test
    void preApprovalWorkflowBlocksTargetWriteToolsBeforeAdapterDispatch() {
        OpsToolExecutionService service = service(null, null);

        Map<String, Object> result = service.execute(Map.of(
                "projectId", "project-1",
                "userId", "alice",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "config.nacos.publish",
                "toolName", "nacos_publish",
                "arguments", Map.of("dataId", "application-prod.yml")), "alice");

        assertFalse((Boolean) result.get("allowed"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", result.get("reasonCode"));
        assertTrue(String.valueOf(result.get("resultId")).startsWith("tool-result-"));
    }

    @Test
    void normalAgentCannotForgeApprovedLandingInternalCaller() {
        OpsToolExecutionService service = service(null, null);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", "project-1");
        request.put("userId", "alice");
        request.put("executionScope", "APPROVED_LANDING");
        request.put("toolsetId", "config.nacos.publish");
        request.put("toolName", "nacos_publish");
        request.put("packageId", "cp-1");
        request.put("packageVersion", 1);
        request.put("packageHash", "hash");
        request.put("operationId", "op-1");
        request.put("landingApproved", true);
        request.put("metadata", Map.of(
                "internalCaller", OpsToolsetRouter.LANDING_INTERNAL_CALLER,
                "landingRuntimeToken", OpsToolsetRouter.LANDING_RUNTIME_TOKEN));
        request.put("arguments", Map.of("dataId", "application-prod.yml"));

        Map<String, Object> result = service.execute(request, "alice");

        assertFalse((Boolean) result.get("allowed"));
        assertEquals("APPROVED_LANDING_INTERNAL_CALLER_REQUIRED", result.get("reasonCode"));
        assertTrue(String.valueOf(result.get("resultId")).startsWith("tool-result-"));
    }

    @Test
    void codeBashDispatchesThroughExecutionServiceAndStoresResult() {
        OpsControlledCodeToolService code = mock(OpsControlledCodeToolService.class);
        when(code.bash(any(), eq("alice"))).thenReturn(Map.of(
                "exitCode", 0,
                "status", "SUCCEEDED",
                "stdoutPreview", "ok"));
        OpsToolExecutionService service = service(code, null);

        Map<String, Object> result = service.execute(Map.of(
                "projectId", "project-1",
                "userId", "alice",
                "sessionId", "session-1",
                "runId", "run-1",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "code.repair",
                "toolName", "code_bash",
                "arguments", Map.of("command", "npm test", "expectedEffect", "TEST_OR_BUILD", "workspaceId", "ws-1")), "alice");

        assertEquals(0, result.get("exitCode"));
        assertTrue(String.valueOf(result.get("resultId")).startsWith("tool-result-"));
        verify(code).bash(any(), eq("alice"));
    }

    @Test
    void changePackageSubmitReviewUsesApplicationService() {
        ReviewChangePackageUseCase changePackageReview = mock(ReviewChangePackageUseCase.class);
        when(changePackageReview.submitReview(any(ChangePackageCommands.SubmitReview.class))).thenReturn(Map.of(
                "packageId", "cp-1",
                "status", "REVIEWING"));
        OpsToolExecutionService service = service(null, changePackageReview);

        Map<String, Object> result = service.execute(Map.of(
                "projectId", "project-1",
                "userId", "alice",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "change_package",
                "toolName", "change_package_submit_review",
                "arguments", Map.of("packageId", "cp-1", "comment", "ready")), "alice");

        assertEquals("REVIEWING", result.get("status"));
        verify(changePackageReview).submitReview(argThat(command ->
                "cp-1".equals(command.packageId()) && "alice".equals(command.actor())));
    }

    @Test
    void toolResultReadCannotCrossProjectThroughUnifiedEntry() {
        ToolResultApplicationService typedResults = mock(ToolResultApplicationService.class);
        when(typedResults.read("tool-result-1", "other-project", "alice", 0, 100))
                .thenThrow(new SecurityException("TOOL_RESULT_NOT_FOUND_OR_FORBIDDEN"));
        OpsToolExecutionService service = service(null, null, typedResults);

        assertThrows(SecurityException.class, () -> service.execute(Map.of(
                "projectId", "other-project",
                "userId", "alice",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "tool_result",
                "toolName", "tool_result_read",
                "arguments", Map.of("resultId", "tool-result-1")), "alice"));
    }

    @Test
    void inspectionTaskListDispatchesThroughUnifiedEntryAndStoresResult() {
        TaskScheduleApplicationService scheduleService = mock(TaskScheduleApplicationService.class);
        when(scheduleService.listSchedules("project-1")).thenReturn(List.of(TaskScheduleResponseDTO.builder()
                .id(7L)
                .projectId("project-1")
                .agentId("demo-ops-agent")
                .taskName("项目巡检")
                .cronExpression("0 */30 * * * ?")
                .status(1)
                .build()));
        taskScheduleOverride = scheduleService;
        OpsToolExecutionService service = service(null, null);

        Map<String, Object> result = service.execute(Map.of(
                "projectId", "project-1",
                "userId", "alice",
                "sessionId", "session-1",
                "runId", "run-1",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "inspection.task",
                "toolName", "inspection_task_list",
                "arguments", Map.of()), "alice");

        assertEquals(1, ((List<?>) result.get("items")).size());
        assertTrue(String.valueOf(result.get("resultId")).startsWith("tool-result-"));
        verify(scheduleService).listSchedules("project-1");
    }

    @Test
    void inspectionTaskCreatePreservesStructuredTaskParamPrompt() {
        TaskScheduleApplicationService scheduleService = mock(TaskScheduleApplicationService.class);
        when(scheduleService.create(any())).thenReturn(true);
        taskScheduleOverride = scheduleService;
        OpsToolExecutionService service = service(null, null);

        Map<String, Object> result = service.execute(Map.of(
                "projectId", "project-1",
                "userId", "alice",
                "sessionId", "session-1",
                "runId", "run-1",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "inspection.task",
                "toolName", "inspection_task_create",
                "arguments", Map.of(
                        "taskName", "浏览器巡检",
                        "cronExpression", "0 */30 * * * ?",
                        "taskParam", Map.of(
                                "prompt", "检查接口错误率、实例在线状态和慢 SQL 风险。",
                                "rangeMinutes", 30,
                                "promWindow", "30m",
                                "includeRecentLogs", true,
                                "maxRounds", 3))), "alice");

        assertEquals("SUCCEEDED", result.get("status"));
        verify(scheduleService).create(argThat(request ->
                "检查接口错误率、实例在线状态和慢 SQL 风险。".equals(request.getTaskParam())
                        && Integer.valueOf(30).equals(request.getRangeMinutes())
                        && "30m".equals(request.getPromWindow())));
    }

    @Test
    void inspectionTaskCreateDefaultsToGenericAgentInsteadOfSampleProjectAgent() {
        TaskScheduleApplicationService scheduleService = mock(TaskScheduleApplicationService.class);
        when(scheduleService.create(any())).thenReturn(true);
        taskScheduleOverride = scheduleService;
        OpsToolExecutionService service = service(null, null);

        service.execute(Map.of(
                "projectId", "project-1",
                "userId", "alice",
                "executionScope", "PRE_APPROVAL_WORKFLOW",
                "toolsetId", "inspection.task",
                "toolName", "inspection_task_create",
                "arguments", Map.of("taskName", "通用巡检")), "alice");

        verify(scheduleService).create(argThat(request ->
                "generic-ops-react-agent".equals(request.getAgentId())));
    }

    @Test
    void landingDiscoveryPreservesTrustedLandingAuthority() {
        McpExecutionApplicationService mcp = mock(McpExecutionApplicationService.class);
        when(mcp.discover(any())).thenReturn(successMcpResponse());
        OpsToolExecutionService service = new OpsToolExecutionService(
                mock(ToolExecutionApplicationService.class),
                new OpsToolExecutionMapper(),
                mcp,
                new OpsMcpExecutionMapper());
        OpsMcpServerConfig config = landingMcpConfig();

        service.discoverMcpTools(config, "ops-agent");

        verify(mcp).discover(argThat(request ->
                request.trustedLandingRuntime()
                        && request.config().landingApproved()
                        && "cp-1".equals(request.config().changePackageId())
                        && request.config().approvedPackageVersion() == 1));
    }

    @Test
    void landingShapedMetadataWithoutRuntimeStageDoesNotElevateDiscovery() {
        McpExecutionApplicationService mcp = mock(McpExecutionApplicationService.class);
        when(mcp.discover(any())).thenReturn(successMcpResponse());
        OpsToolExecutionService service = new OpsToolExecutionService(
                mock(ToolExecutionApplicationService.class),
                new OpsToolExecutionMapper(),
                mcp,
                new OpsMcpExecutionMapper());
        OpsMcpServerConfig config = landingMcpConfig();
        config.setToolCallStage("PREPARE");

        service.discoverMcpTools(config, "ops-agent");

        verify(mcp).discover(argThat(request ->
                !request.trustedLandingRuntime() && !request.config().landingApproved()));
    }

    @Test
    void facadeRequiresTypedApplicationsAndBoundaryMappers() {
        ToolExecutionApplicationService tools = mock(ToolExecutionApplicationService.class);
        McpExecutionApplicationService mcp = mock(McpExecutionApplicationService.class);
        OpsToolExecutionMapper toolMapper = new OpsToolExecutionMapper();
        OpsMcpExecutionMapper mcpMapper = new OpsMcpExecutionMapper();

        assertThrows(IllegalArgumentException.class, () ->
                new OpsToolExecutionService(null, toolMapper, mcp, mcpMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new OpsToolExecutionService(tools, null, mcp, mcpMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new OpsToolExecutionService(tools, toolMapper, null, mcpMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new OpsToolExecutionService(tools, toolMapper, mcp, null));
    }

    private OpsToolExecutionService service(OpsControlledCodeToolService code,
                                             ReviewChangePackageUseCase changePackageService,
                                             ToolResultApplicationService typedResults) {
        typedResultsOverride = typedResults;
        return service(code, changePackageService);
    }

    private OpsToolExecutionService service(OpsControlledCodeToolService code,
                                            ReviewChangePackageUseCase changePackageService) {
        ToolResultApplicationService typedResults = typedResultsOverride == null
                ? mock(ToolResultApplicationService.class)
                : typedResultsOverride;
        TaskScheduleApplicationService taskSchedules = taskScheduleOverride;
        typedResultsOverride = null;
        taskScheduleOverride = null;
        OpsToolsetCatalogService catalog = mock(OpsToolsetCatalogService.class);
        OpsToolsetRouter router = new OpsToolsetRouter();
        when(catalog.listEffectiveToolsets(any(), any()))
                .thenReturn(new OpsToolsetRegistry().listBuiltInToolsets());
        AtomicLong nanos = new AtomicLong();
        OpsInternalToolInvoker internalInvoker = new OpsInternalToolInvoker(List.of(
                new OpsCodeToolExecutionDispatchHandler(providerOf(code), providerOf(null)),
                new OpsChangePackageToolExecutionDispatchHandler(
                        providerOf(null), providerOf(changePackageService), providerOf(null)),
                new OpsToolResultExecutionDispatchHandler(typedResults, new OpsToolResultMapper()),
                new OpsInspectionTaskExecutionDispatchHandler(providerOf(taskSchedules))));
        ToolExecutionApplicationService application = new ToolExecutionApplicationService(
                new OpsToolExecutionCatalogAdapter(catalog, router),
                new OpsCompositeToolExecutionDispatchAdapter(
                        ToolExecutionTarget::binding,
                        List.of(internalInvoker)),
                command -> new ToolExecutionRecordedResult(
                        "tool-result-test", "evidence-test", "preview", "a".repeat(64), false,
                        "tool-result://tool-result-test", "b".repeat(64), command.durationMs()),
                (request, checkpointType, payload) -> { },
                event -> { },
                () -> "tool-call-test",
                () -> nanos.addAndGet(1_000_000L));
        return new OpsToolExecutionService(
                application,
                new OpsToolExecutionMapper(),
                mock(McpExecutionApplicationService.class),
                new OpsMcpExecutionMapper());
    }

    private OpsMcpServerConfig landingMcpConfig() {
        return OpsMcpServerConfig.builder()
                .name("service-control")
                .projectId("project-1")
                .runId("landing-run-1")
                .agentId("platform-landing-react")
                .nodeId("landing")
                .mcpId("service-control-mcp")
                .toolId("service-control-mcp")
                .toolCallStage("LANDING")
                .landingApproved(true)
                .internalCaller("UNIFIED_AGENT_RUNTIME")
                .changePackageId("cp-1")
                .approvedPackageHash("a".repeat(64))
                .approvedPackageVersion(1)
                .allowedTools(List.of("restart_service"))
                .build();
    }

    private McpExecutionResponse successMcpResponse() {
        return new McpExecutionResponse(
                true,
                "ALLOWED",
                "MCP_DISCOVERY",
                "mcp.service-control-mcp",
                "tool_catalog",
                new McpExecutionRecordedResult(
                        "result-1", "evidence-1", "preview", "b".repeat(64), false,
                        "db:result-1", "c".repeat(64), 1L),
                Map.of("status", "SUCCEEDED", "count", 1, "tools", List.of()));
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        if (value != null) {
            when(provider.getObject()).thenReturn(value);
        }
        return provider;
    }
}
