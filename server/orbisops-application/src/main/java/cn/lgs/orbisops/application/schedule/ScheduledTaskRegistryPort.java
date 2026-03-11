package cn.lgs.orbisops.application.schedule;

import java.util.List;

/** Read boundary used by the dynamic scheduler to register and retire scheduled tasks. */
public interface ScheduledTaskRegistryPort {

    List<ScheduledTaskRegistration> listEnabled();

    List<Long> listInvalidIds();
}
