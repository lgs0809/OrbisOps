package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsScheduledTaskExecutionAdaptersTest {

    @Test void missingCreatorFailsBeforeRuntimeEvenWhenPayloadClaimsAnOwner() {
        var service = mock(OpsAnalysisApplicationService.class);
        var adapter = new OpsScheduledTaskAnalysisAdapter(service, new OpsTaskScheduleRuntimeConfigurationCodec());
        assertThrows(IllegalArgumentException.class, () -> adapter.prepare(
                new ScheduledTaskExecutionCommand(7L, "test", "agent", "MANUAL", "{\"createdBy\":\"victim\"}"), 1L));
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void analysisAdapterDecodesUnifiedRuntimeAndBuildsNormalizedAgentRequest() {
        OpsAnalysisApplicationService analysisService = mock(OpsAnalysisApplicationService.class);
        OpsTaskScheduleRuntimeConfigurationCodec codec = new OpsTaskScheduleRuntimeConfigurationCodec();
        OpsScheduledTaskAnalysisAdapter adapter = new OpsScheduledTaskAnalysisAdapter(analysisService, codec);
        String payload = """
                {"projectId":"payment","agentBindingMode":"PINNED_VERSION","agentVersion":5,
                 "agentDefinitionHash":"hash-v5","prompt":"检查 5xx 和 P99","rangeMinutes":30,
                 "promWindow":"15m","includeRecentLogs":true,"maxRounds":4,
                 "subAgentMaxIterations":2,"nodeTimeoutSeconds":180,"maxEvidenceItems":20,
                 "notifyChannel":true,"notificationChannelId":"ops-alerts",
                 "notificationTarget":"oncall"}
                """;
        when(analysisService.normalizeRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ScheduledTaskExecutionCommand command = command(payload);

        OpsAgentRunRequestDTO request = adapter.prepare(command, 42L);

        assertEquals("task_7_42", request.getRunId());
        assertEquals("creator-a", request.getRequestedBy());
        assertEquals("payment", request.getProjectId());
        assertEquals("ops-agent", request.getAgentDefinitionId());
        assertEquals(5, request.getAgentVersion());
        assertEquals(30, request.getRangeMinutes());
        assertEquals("15m", request.getPromWindow());
        assertEquals(4, request.getMaxRounds());
        assertEquals(2, request.getSubAgentMaxIterations());
        assertEquals(180, request.getNodeTimeoutSeconds());
        assertEquals(20, request.getMaxEvidenceItems());
        assertTrue(request.getNotifyChannel());
        assertEquals("ops-alerts", request.getNotificationChannelId());
        assertEquals("oncall", request.getNotificationTarget());
        assertTrue(request.getQuestion().contains("支付巡检"));
        assertTrue(request.getQuestion().contains("检查 5xx 和 P99"));
        assertEquals("task-schedule", request.getTriggerSource());
        assertEquals("WORKFLOW", request.getExecutionStyle());
        assertEquals("42", request.getTriggerEventId());
    }

    @Test
    void explicitNonDefaultInspectionAgentUsesFixedWorkflowStyle() {
        OpsAnalysisApplicationService analysisService = mock(OpsAnalysisApplicationService.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(projects.defaultAgentId("payment")).thenReturn("generic-ops-react-agent");
        when(analysisService.normalizeRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OpsScheduledTaskAnalysisAdapter adapter = new OpsScheduledTaskAnalysisAdapter(
                analysisService,
                new OpsTaskScheduleRuntimeConfigurationCodec(),
                projects);
        String payload = "{\"projectId\":\"payment\",\"prompt\":\"固定巡检\"}";

        OpsAgentRunRequestDTO request = adapter.prepare(command(payload), 43L);

        assertEquals("ops-agent", request.getAgentDefinitionId());
        assertEquals("WORKFLOW", request.getExecutionStyle());
    }

    @Test
    void projectDefaultInspectionAgentUsesReactStyle() {
        OpsAnalysisApplicationService analysisService = mock(OpsAnalysisApplicationService.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(projects.defaultAgentId("payment")).thenReturn("ops-agent");
        when(analysisService.normalizeRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OpsScheduledTaskAnalysisAdapter adapter = new OpsScheduledTaskAnalysisAdapter(
                analysisService,
                new OpsTaskScheduleRuntimeConfigurationCodec(),
                projects);
        String payload = "{\"projectId\":\"payment\",\"prompt\":\"默认巡检\"}";

        OpsAgentRunRequestDTO request = adapter.prepare(command(payload), 44L);

        assertEquals("ops-agent", request.getAgentDefinitionId());
        assertEquals("REACT", request.getExecutionStyle());
    }

    @Test
    void structuredWorkflowInputReachesRuntimeWithoutTaskNamePrefixAndRejectsMalformedJson() {
        var service = mock(OpsAnalysisApplicationService.class);
        when(service.normalizeRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var adapter = new OpsScheduledTaskAnalysisAdapter(service, new OpsTaskScheduleRuntimeConfigurationCodec());
        var input = java.util.Map.of("projectId", "payment", "serviceId", "orders", "environment", "acceptance");
        String payload = com.alibaba.fastjson.JSON.toJSONString(java.util.Map.of(
                "projectId", "payment", "prompt", com.alibaba.fastjson.JSON.toJSONString(input)));

        var request = adapter.prepare(command(payload), 45L);

        assertEquals(input, new cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy()
                .parseObject(request.getQuestion()));
        assertEquals("task_7_45", request.getRunId());
        assertEquals("WORKFLOW", request.getExecutionStyle());
        String malformed = com.alibaba.fastjson.JSON.toJSONString(java.util.Map.of(
                "projectId", "payment", "prompt", "{broken"));
        assertThrows(IllegalArgumentException.class, () -> adapter.prepare(command(malformed), 46L));
    }

    @Test
    void defaultReactAgentKeepsJsonLookingUserPromptAsText() {
        var service = mock(OpsAnalysisApplicationService.class);
        var projects = mock(ProjectDefinitionApplicationService.class);
        when(projects.defaultAgentId("payment")).thenReturn("ops-agent");
        when(service.normalizeRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var adapter = new OpsScheduledTaskAnalysisAdapter(service, new OpsTaskScheduleRuntimeConfigurationCodec(), projects);
        var request = adapter.prepare(command("{\"projectId\":\"payment\",\"prompt\":\"{broken\"}"), 47L);
        assertEquals("REACT", request.getExecutionStyle());
        assertEquals("定时任务：支付巡检\n执行要求：{broken", request.getQuestion());
    }

    @Test
    void analysisAdapterKeepsLegacyTextConfigurationFailClosed() {
        OpsScheduledTaskAnalysisAdapter adapter = new OpsScheduledTaskAnalysisAdapter(
                mock(OpsAnalysisApplicationService.class),
                new OpsTaskScheduleRuntimeConfigurationCodec());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.prepare(command("旧版文本 prompt"), 42L));

        assertEquals("周期任务仍使用旧版文本配置，请重新创建任务", error.getMessage());
    }

    @Test
    void outputRenderingPreservesMarkdownPromptAndJsonFallbackPriority() {
        OpsScheduledTaskAnalysisAdapter adapter = new OpsScheduledTaskAnalysisAdapter(
                mock(OpsAnalysisApplicationService.class),
                new OpsTaskScheduleRuntimeConfigurationCodec());

        assertEquals("# report", adapter.renderOutput(
                OpsAnalysisResponseDTO.builder().markdownReport("# report").aiPrompt("prompt").build()));
        assertEquals("prompt", adapter.renderOutput(
                OpsAnalysisResponseDTO.builder().aiPrompt("prompt").build()));
        assertTrue(adapter.renderOutput(OpsAnalysisResponseDTO.builder().analysisId("a-1").build())
                .contains("analysisId"));
    }

    @Test
    void executorAdapterDelegatesToThreadPool() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        OpsScheduledTaskExecutionExecutorAdapter adapter = new OpsScheduledTaskExecutionExecutorAdapter(executor);
        AtomicBoolean executed = new AtomicBoolean();

        adapter.execute(() -> executed.set(true));

        for (int i = 0; i < 20 && !executed.get(); i++) {
            Thread.sleep(20);
        }
        assertTrue(executed.get());
        executor.shutdownNow();
    }

    private ScheduledTaskExecutionCommand command(String payload) {
        return new ScheduledTaskExecutionCommand(
                7L,
                "支付巡检",
                "ops-agent",
                "SCHEDULED",
                payload, "creator-a");
    }
}
