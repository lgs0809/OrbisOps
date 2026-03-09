package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateCommands;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsExecutionAdapterTemplateCommandMapperTest {

    private final OpsExecutionAdapterTemplateCommandMapper mapper =
            new OpsExecutionAdapterTemplateCommandMapper();

    @Test
    void mapsAliasesAndStableRiskFieldsAtTriggerBoundary() {
        ExecutionAdapterTemplateCommands.Mutation command = mapper.mutation(Map.of(
                "templateId", "template-1",
                "name", "Template",
                "adapter", "mysql-controlled",
                "supportedActions", "CREATE_INDEX,ANALYZE",
                "defaultConfig", Map.of("approvalRequired", true),
                "riskLevel", "critical",
                "readOnly", "1",
                "status", "disabled"), "alice");

        assertEquals("template-1", command.templateId().value());
        assertEquals("Template", command.templateName().value());
        assertEquals(ExecutionAdapterType.MYSQL_CONTROLLED, command.adapterType().value());
        assertEquals(List.of("CREATE_INDEX", "ANALYZE"), command.supportedActions().value());
        assertEquals(ExecutionRiskLevel.CRITICAL, command.riskLevel().value());
        assertEquals(true, command.readOnly().value());
        assertEquals(ExecutionResourceStatus.DISABLED, command.status().value());
        assertEquals("alice", command.actor());
    }

    @Test
    void omittedUpdateFieldsStayAbsent() {
        ExecutionAdapterTemplateCommands.Mutation command = mapper.mutation(
                Map.of("description", "updated"), "alice");

        assertTrue(command.description().supplied());
        assertFalse(command.templateId().supplied());
        assertFalse(command.adapterType().supplied());
        assertFalse(command.riskLevel().supplied());
        assertFalse(command.status().supplied());
    }

    @Test
    void statusAndTargetCommandsOwnProtocolDefaultsAndAliases() {
        ExecutionAdapterTemplateCommands.StatusChange status = mapper.status(Map.of(), "alice");
        ExecutionAdapterTemplateCommands.TargetGeneration target = mapper.target(
                "project-1", Map.of("templateId", "template-1", "environment", "prod"), "alice");

        assertEquals(ExecutionResourceStatus.DISABLED, status.status());
        assertEquals("template-1", target.templateId());
        assertEquals("prod", target.request().get("environment"));
    }

    @Test
    void unknownRiskOrAdapterFailsClosedBeforeApplication() {
        IllegalArgumentException risk = assertThrows(IllegalArgumentException.class,
                () -> mapper.mutation(Map.of("riskLevel", "catastrophic"), "alice"));
        IllegalArgumentException adapter = assertThrows(IllegalArgumentException.class,
                () -> mapper.mutation(Map.of("adapterType", "shell"), "alice"));

        assertTrue(risk.getMessage().contains("EXECUTION_RISK_LEVEL_UNKNOWN"));
        assertEquals("EXECUTION_ADAPTER_UNSUPPORTED:shell", adapter.getMessage());
    }
}
