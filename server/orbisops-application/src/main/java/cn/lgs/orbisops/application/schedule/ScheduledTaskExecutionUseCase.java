package cn.lgs.orbisops.application.schedule;

/**
 * Application process manager for one scheduled inspection lifecycle.
 *
 * <p>The production path first performs a cheap read-only screening. Healthy
 * slots are recorded without running a deep Agent. Only anomalous or uncertain
 * screening results enter the existing Agent runtime and Incident mainline.</p>
 */
public final class ScheduledTaskExecutionUseCase<Q, R> {

    private final ScheduledTaskExecutionCatalogPort catalogPort;
    private final ScheduledTaskExecutionExecutorPort executorPort;
    private final ScheduledTaskAnalysisPort<Q, R> analysisPort;
    private final ScheduledTaskScreeningPort screeningPort;
    private final ScheduledTaskIncidentPort incidentPort;

    public ScheduledTaskExecutionUseCase(
            ScheduledTaskExecutionCatalogPort catalogPort,
            ScheduledTaskExecutionExecutorPort executorPort,
            ScheduledTaskAnalysisPort<Q, R> analysisPort) {
        this(
                catalogPort,
                executorPort,
                analysisPort,
                command -> ScheduledTaskScreeningResult.deepRequired("LIGHTWEIGHT_SCREENING_NOT_CONFIGURED"),
                new ScheduledTaskIncidentPort() {
                    @Override
                    public String openForAnomaly(
                            ScheduledTaskExecutionCommand command,
                            Long executionId,
                            ScheduledTaskScreeningResult screening) {
                        return "";
                    }

                    @Override
                    public void linkInvestigation(
                            String incidentId,
                            ScheduledTaskExecutionCommand command,
                            Long executionId) {
                    }
                });
    }

    public ScheduledTaskExecutionUseCase(
            ScheduledTaskExecutionCatalogPort catalogPort,
            ScheduledTaskExecutionExecutorPort executorPort,
            ScheduledTaskAnalysisPort<Q, R> analysisPort,
            ScheduledTaskScreeningPort screeningPort,
            ScheduledTaskIncidentPort incidentPort) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_EXECUTION_CATALOG_PORT_REQUIRED");
        }
        if (executorPort == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_EXECUTOR_PORT_REQUIRED");
        }
        if (analysisPort == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_ANALYSIS_PORT_REQUIRED");
        }
        if (screeningPort == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_SCREENING_PORT_REQUIRED");
        }
        if (incidentPort == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_INCIDENT_PORT_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.executorPort = executorPort;
        this.analysisPort = analysisPort;
        this.screeningPort = screeningPort;
        this.incidentPort = incidentPort;
    }

    public void ensureStorage() {
        catalogPort.ensureStorage();
    }

    public Long submit(ScheduledTaskExecutionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_EXECUTION_COMMAND_REQUIRED");
        }
        Long executionId = catalogPort.create(command);
        try {
            executorPort.execute(() -> execute(executionId, command));
        } catch (RuntimeException error) {
            catalogPort.markFailed(
                    executionId,
                    "任务执行队列已满：" + error.getMessage(),
                    null);
            throw error;
        }
        return executionId;
    }

    private void execute(Long executionId, ScheduledTaskExecutionCommand command) {
        try {
            ScheduledTaskScreeningResult screening = safeScreen(command);
            catalogPort.updateInput(executionId, renderScreening(screening));
            if (screening.normal()) {
                catalogPort.markSucceeded(executionId, screening.summary());
                return;
            }
            if (screening.status() == ScheduledTaskScreeningResult.Status.CONFIG_ERROR) {
                catalogPort.markFailed(executionId, screening.summary(), null);
                return;
            }

            String incidentId = incidentPort.openForAnomaly(command, executionId, screening);
            Q request = analysisPort.prepare(command, executionId);
            catalogPort.updateInput(executionId, analysisPort.serializeInput(request));
            R response = analysisPort.execute(request);
            if (incidentId != null && !incidentId.isBlank()) {
                incidentPort.linkInvestigation(incidentId, command, executionId);
            }
            String status = analysisPort.runtimeStatus(response);
            String output = analysisPort.renderOutput(response);
            if ("SUCCEEDED".equals(status)) {
                catalogPort.markSucceeded(executionId, output);
            } else if ("WAITING_APPROVAL".equals(status) || "CANCELED".equals(status)) {
                catalogPort.markIncomplete(executionId, status, output);
            } else {
                catalogPort.markFailed(executionId, "SCHEDULED_ANALYSIS_NOT_SUCCEEDED", output);
            }
        } catch (Exception error) {
            catalogPort.markFailed(executionId, error.getMessage(), null);
        }
    }

    private ScheduledTaskScreeningResult safeScreen(ScheduledTaskExecutionCommand command) {
        try {
            ScheduledTaskScreeningResult result = screeningPort.screen(command);
            if (result == null) {
                return ScheduledTaskScreeningResult.deepRequired("LIGHTWEIGHT_SCREENING_RETURNED_NULL");
            }
            return result;
        } catch (RuntimeException error) {
            // Screening uncertainty must not be treated as healthy. Fall through to
            // the existing deep Agent rather than creating a false-normal slot.
            return ScheduledTaskScreeningResult.deepRequired(
                    "LIGHTWEIGHT_SCREENING_UNAVAILABLE:" + safeMessage(error));
        }
    }

    private String renderScreening(ScheduledTaskScreeningResult screening) {
        return "screening=" + screening.status().name()
                + ";summary=" + screening.summary()
                + ";abnormalSources=" + String.join(",", screening.abnormalSources());
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
        }
        return error.getMessage().trim();
    }
}
