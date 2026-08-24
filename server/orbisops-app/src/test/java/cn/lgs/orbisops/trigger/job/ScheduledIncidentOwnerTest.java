package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.incident.*;
import cn.lgs.orbisops.application.schedule.*;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScheduledIncidentOwnerTest {
    @Test void unassignedIncidentInheritsScheduleCreator() { check("", true); }
    @Test void recurringCheckPreservesExplicitlyAssignedIncidentOwner() { check("oncall-b", false); }
    private void check(String existingOwner, boolean assign) {
        var incidents=mock(IncidentCommandApplicationService.class);
        var incident=mock(IncidentSnapshot.class);
        when(incident.incidentId()).thenReturn("incident-test");
        when(incident.ownerUserId()).thenReturn(existingOwner);
        when(incidents.openRecurring(anyString(),any(),anyString())).thenReturn(incident);
        var adapter=new OpsScheduledTaskIncidentAdapter(incidents,new OpsTaskScheduleRuntimeConfigurationCodec());
        var command=new ScheduledTaskExecutionCommand(7L,"synthetic","workflow","SCHEDULED",
                "{\"projectId\":\"project-a\"}","creator-a");
        assertEquals("incident-test",adapter.openForAnomaly(command,1L,
                ScheduledTaskScreeningResult.deepRequired("synthetic anomaly")));
        if(assign) verify(incidents).assignOwner("incident-test","creator-a","schedule");
        else verify(incidents,never()).assignOwner(anyString(),anyString(),anyString());
    }
    @Test void missingCreatorCannotOpenUnownedIncident() {
        var incidents=mock(IncidentCommandApplicationService.class);
        var adapter=new OpsScheduledTaskIncidentAdapter(incidents,new OpsTaskScheduleRuntimeConfigurationCodec());
        assertThrows(IllegalArgumentException.class,()->adapter.openForAnomaly(
                new ScheduledTaskExecutionCommand(7L,"test","workflow","MANUAL","{}"),1L,
                ScheduledTaskScreeningResult.deepRequired("test")));
        verifyNoInteractions(incidents);
    }
}
