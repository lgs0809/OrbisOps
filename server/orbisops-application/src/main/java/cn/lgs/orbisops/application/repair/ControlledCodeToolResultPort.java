package cn.lgs.orbisops.application.repair;

public interface ControlledCodeToolResultPort {

    boolean available();

    StoredToolResult record(ToolResultRequest request);

    record ToolResultRequest(
            String projectId,
            String sessionId,
            String runId,
            String userId,
            String toolsetId,
            String toolName,
            String source,
            String query,
            String output,
            int maxBytes,
            int maxLines,
            String actor) {
    }

    record StoredToolResult(
            String resultId,
            boolean truncated,
            String fullOutputRef,
            String outputHash) {
    }
}
