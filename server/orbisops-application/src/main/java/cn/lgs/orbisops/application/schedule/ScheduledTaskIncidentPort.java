package cn.lgs.orbisops.application.schedule;

/** Incident aggregation boundary for anomalous scheduled inspections. */
public interface ScheduledTaskIncidentPort {

    String openForAnomaly(
            ScheduledTaskExecutionCommand command,
            Long executionId,
            ScheduledTaskScreeningResult screening);

    void linkInvestigation(
            String incidentId,
            ScheduledTaskExecutionCommand command,
            Long executionId);
}
