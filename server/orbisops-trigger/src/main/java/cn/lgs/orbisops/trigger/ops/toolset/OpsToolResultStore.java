package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.trigger.application.evidence.OpsToolResultMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpsToolResultStore {

    private final ToolResultApplicationService application;
    private final OpsToolResultMapper mapper;

    public OpsToolResultStore(
            ToolResultApplicationService application,
            OpsToolResultMapper mapper) {
        this.application = application;
        this.mapper = mapper;
    }

    public void init() {
        // Schema initialization is owned by Infrastructure.
    }

    public Map<String, Object> record(
            String projectId,
            String sessionId,
            String runId,
            String userId,
            String toolsetId,
            String toolName,
            String source,
            String query,
            String output,
            OpsToolOutputBudget budget,
            String actor) {
        return record(projectId, sessionId, runId, userId, toolsetId, toolName, source,
                query, output, budget, actor, "SUCCEEDED", 0L);
    }

    public Map<String, Object> record(
            String projectId,
            String sessionId,
            String runId,
            String userId,
            String toolsetId,
            String toolName,
            String source,
            String query,
            String output,
            OpsToolOutputBudget budget,
            String actor,
            String status,
            long durationMs) {
        return mapper.view(application.record(mapper.draft(
                projectId, sessionId, runId, userId, toolsetId, toolName, source,
                query, output, budget, actor, status, durationMs)));
    }

    public Map<String, Object> read(
            String resultId,
            String projectId,
            String userId,
            int offset,
            int limit) {
        return mapper.view(application.read(resultId, projectId, userId, offset, limit));
    }

    public Map<String, Object> grep(
            String resultId,
            String projectId,
            String userId,
            String pattern) {
        return mapper.view(application.grep(resultId, projectId, userId, pattern));
    }

    public Map<String, Object> slice(
            String resultId,
            String projectId,
            String userId,
            int startLine,
            int endLine) {
        return mapper.view(application.slice(resultId, projectId, userId, startLine, endLine));
    }

    public List<Map<String, Object>> listForRun(String projectId, String runId, int limit) {
        return mapper.views(application.listForRun(projectId, runId, limit));
    }

    public Map<String, Object> readiness() {
        return mapper.view(application.readiness());
    }

    public void ensureTables() {
        // Schema initialization is owned by Infrastructure.
    }
}
