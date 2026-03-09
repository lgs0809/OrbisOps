package cn.lgs.orbisops.application.source;

public interface SourceExecutionResourcePort {
    boolean supportsService(String projectId, String resourceId, String serviceId);
}
