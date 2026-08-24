package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.TaskExecutionResponseDTO;
import cn.lgs.orbisops.api.dto.TaskScheduleRequestDTO;
import cn.lgs.orbisops.api.dto.TaskScheduleResponseDTO;
import cn.lgs.orbisops.application.schedule.TaskExecutionView;
import cn.lgs.orbisops.application.schedule.TaskScheduleCatalogUseCase;
import cn.lgs.orbisops.application.schedule.TaskScheduleCommand;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionUseCase;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskScheduleApplicationServiceTest {

    @Test
    void creationWithoutAuthenticatedHumanCannotPersistOrUsePromptAsOwner() {
        var catalog = mock(TaskScheduleCatalogUseCase.class);
        var service = new TaskScheduleApplicationService(catalog, mock(TaskScheduleExecutionUseCase.class));
        var request = TaskScheduleRequestDTO.builder().taskParam("{\"createdBy\":\"victim\"}").build();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.create(request));
        var http = new org.springframework.mock.web.MockHttpServletRequest();
        http.setAttribute(cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new cn.lgs.orbisops.trigger.application.security.AdminAuthService.AuthPrincipal("robot", "robot", "", "ADMIN", true));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(http));
        try { org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.create(request)); }
        finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
        org.mockito.Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void createProjectsDtoToTypedCommand() {
        TaskScheduleCatalogUseCase catalog = mock(TaskScheduleCatalogUseCase.class);
        TaskScheduleExecutionUseCase execution = mock(TaskScheduleExecutionUseCase.class);
        TaskScheduleApplicationService service = new TaskScheduleApplicationService(catalog, execution);
        when(catalog.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("creator-a"))).thenReturn(true);
        TaskScheduleRequestDTO request = TaskScheduleRequestDTO.builder()
                .id(7L)
                .projectId(" payment ")
                .agentId(" ops-agent ")
                .agentBindingMode("PINNED_VERSION")
                .agentVersion(5)
                .taskName("支付巡检")
                .description("核心链路")
                .cronExpression("0 0/15 * * * ?")
                .taskParam("检查错误率和延迟")
                .status(1)
                .rangeMinutes(30)
                .promWindow("5m")
                .includeRecentLogs(false)
                .maxRounds(4)
                .subAgentMaxIterations(2)
                .nodeTimeoutSeconds(90)
                .maxEvidenceItems(20)
                .notifyChannel(true)
                .notificationChannelId("oncall")
                .notificationTarget("room")
                .build();

        var http = new org.springframework.mock.web.MockHttpServletRequest();
        http.setAttribute(cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new cn.lgs.orbisops.trigger.application.security.AdminAuthService.AuthPrincipal("alice", "creator-a", "jwt", "ADMIN", false));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(http));
        try { assertTrue(service.create(request)); }
        finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

        ArgumentCaptor<TaskScheduleCommand> command = ArgumentCaptor.forClass(TaskScheduleCommand.class);
        verify(catalog).create(command.capture(), org.mockito.ArgumentMatchers.eq("creator-a"));
        assertEquals(7L, command.getValue().id());
        assertEquals(" payment ", command.getValue().projectId());
        assertEquals("检查错误率和延迟", command.getValue().prompt());
        assertEquals(20, command.getValue().maxEvidenceItems());
    }

    @Test
    void listProjectsTypedScheduleAndRuntimeConfiguration() {
        TaskScheduleCatalogUseCase catalog = mock(TaskScheduleCatalogUseCase.class);
        TaskScheduleExecutionUseCase execution = mock(TaskScheduleExecutionUseCase.class);
        TaskScheduleApplicationService service = new TaskScheduleApplicationService(catalog, execution);
        TaskScheduleRuntimeConfiguration runtime = new TaskScheduleRuntimeConfiguration(
                "payment", "LATEST_PUBLISHED", 5, "hash-v5", "检查错误率", 15, "5m",
                true, 3, 3, 120, 12, false, null, null);
        when(catalog.listSchedules("payment")).thenReturn(List.of(new TaskScheduleDefinition(
                7L, "payment", "ops-agent", "支付巡检", "核心链路", "0 0/15 * * * ?",
                runtime, 1,
                LocalDateTime.of(2026, 7, 30, 6, 0),
                LocalDateTime.of(2026, 7, 30, 6, 30))));

        List<TaskScheduleResponseDTO> result = service.listSchedules("payment");

        assertEquals(1, result.size());
        assertEquals("LATEST_PUBLISHED", result.get(0).getAgentBindingMode());
        assertEquals("hash-v5", result.get(0).getAgentDefinitionHash());
        assertEquals("检查错误率", result.get(0).getTaskParam());
        assertEquals("2026-07-30 06:30:00", result.get(0).getUpdateTime());
    }

    @Test
    void executionOperationsDelegateAndProjectTypedHistory() {
        TaskScheduleCatalogUseCase catalog = mock(TaskScheduleCatalogUseCase.class);
        TaskScheduleExecutionUseCase execution = mock(TaskScheduleExecutionUseCase.class);
        TaskScheduleApplicationService service = new TaskScheduleApplicationService(catalog, execution);
        when(execution.runNow(7L, "payment")).thenReturn(42L);
        when(execution.listExecutions("payment", 7L, 10)).thenReturn(List.of(new TaskExecutionView(
                42L, 7L, "支付巡检", "ops-agent", "MANUAL", "SUCCESS",
                LocalDateTime.of(2026, 7, 30, 6, 0),
                LocalDateTime.of(2026, 7, 30, 6, 1),
                "input", "output", null)));

        assertEquals(42L, service.runNow(7L, "payment"));
        List<TaskExecutionResponseDTO> result = service.listExecutions("payment", 7L, 10);

        assertEquals("SUCCESS", result.get(0).getStatus());
        assertEquals("2026-07-30 06:01:00", result.get(0).getEndedAt());
        verify(execution).runNow(7L, "payment");
        verify(execution).listExecutions("payment", 7L, 10);
    }
}
