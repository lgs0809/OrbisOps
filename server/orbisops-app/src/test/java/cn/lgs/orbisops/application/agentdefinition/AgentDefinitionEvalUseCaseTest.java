package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDefinitionEvalUseCaseTest {

    @Test
    void projectAdmissionPrecedesSuiteCreationAndRun() {
        @SuppressWarnings("unchecked")
        AgentDefinitionEvalPort<String, String> port = mock(AgentDefinitionEvalPort.class);
        AgentDefinitionEvalUseCase<String, String> useCase = new AgentDefinitionEvalUseCase<>(port);
        when(port.requireExistingProject("project-1")).thenReturn("project-1");
        when(port.createSuite("project-1", "agent-1", "request", "alice")).thenReturn("suite");
        when(port.run("project-1", "agent-1", 2, "suite-1", "alice")).thenReturn("run");

        assertEquals("suite", useCase.createSuite(
                new AgentDefinitionEvalSuiteCommand<>(" project-1 ", " agent-1 ", "request", " alice ")));
        assertEquals("run", useCase.run(
                new AgentDefinitionEvalRunCommand(" project-1 ", " agent-1 ", 2, " suite-1 ", " alice ")));

        InOrder order = inOrder(port);
        order.verify(port).requireExistingProject("project-1");
        order.verify(port).createSuite("project-1", "agent-1", "request", "alice");
        order.verify(port).requireExistingProject("project-1");
        order.verify(port).run("project-1", "agent-1", 2, "suite-1", "alice");
    }

    @Test
    void runCommandPreservesVersionValidationMessage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentDefinitionEvalRunCommand(
                        "project-1", "agent-1", (Integer) null, "suite", "alice"));

        assertEquals("version 必须大于 0", error.getMessage());
    }
}
