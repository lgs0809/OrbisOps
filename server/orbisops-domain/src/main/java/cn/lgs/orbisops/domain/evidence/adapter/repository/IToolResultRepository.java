package cn.lgs.orbisops.domain.evidence.adapter.repository;

import cn.lgs.orbisops.domain.evidence.model.ToolResult;

import java.util.List;
import java.util.Optional;

public interface IToolResultRepository {

    ToolResult save(ToolResult result);

    Optional<ToolResult> find(String resultId);

    List<ToolResult> listForRun(String projectId, String runId, int limit);

    long count();

    boolean persistent();

    boolean memoryFallbackAllowed();
}
