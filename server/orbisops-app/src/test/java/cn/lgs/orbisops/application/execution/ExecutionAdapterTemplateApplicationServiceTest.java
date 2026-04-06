package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionAdapterTemplateApplicationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void createUsesTypedCommandActorForAudit() {
        ExecutionAdapterTemplatePort<Object> port = mock(ExecutionAdapterTemplatePort.class);
        ExecutionAuditPort auditPort = mock(ExecutionAuditPort.class);
        ExecutionAdapterTemplateApplicationService<Object> service =
                new ExecutionAdapterTemplateApplicationService<>(port, auditPort);
        ExecutionAdapterTemplate stored = new ExecutionAdapterTemplate(
                1L,
                "template-1",
                "Template 1",
                ExecutionAdapterType.LOCAL_JAVA_SERVICE,
                List.of("ARTIFACT_DEPLOY"),
                Map.of(),
                ExecutionRiskLevel.HIGH,
                false,
                "",
                ExecutionResourceStatus.ENABLED,
                "alice",
                null,
                null);
        when(port.createTemplate(any(ExecutionAdapterTemplateCommands.Mutation.class)))
                .thenReturn(stored);
        when(port.generatedTargets("template-1")).thenReturn(List.of());
        ExecutionAdapterTemplateCommands.Mutation command = mutation("template-1", "alice");

        Map<String, Object> result = service.create(command);

        assertEquals("template-1", result.get("adapterTemplateId"));
        assertEquals("alice", result.get("createBy"));
        verify(port).createTemplate(command);
        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq("execution-adapter-template"),
                org.mockito.ArgumentMatchers.eq("create"),
                org.mockito.ArgumentMatchers.eq("template-1"),
                org.mockito.ArgumentMatchers.isNull(),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
    }

    @Test
    void commandRejectsMissingActorBeforeApplicationInvocation() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mutation("template-1", " "));

        assertEquals("EXECUTION_TEMPLATE_ACTOR_REQUIRED", error.getMessage());
    }

    private ExecutionAdapterTemplateCommands.Mutation mutation(String id, String actor) {
        return new ExecutionAdapterTemplateCommands.Mutation(
                supplied(id),
                supplied("Template 1"),
                supplied(ExecutionAdapterType.LOCAL_JAVA_SERVICE),
                supplied(List.of("ARTIFACT_DEPLOY")),
                supplied(Map.of()),
                supplied(ExecutionRiskLevel.HIGH),
                supplied(false),
                supplied(""),
                supplied(ExecutionResourceStatus.ENABLED),
                actor);
    }

    private static <T> ExecutionAdapterTemplateCommands.Field<T> supplied(T value) {
        return ExecutionAdapterTemplateCommands.Field.supplied(value);
    }
}
