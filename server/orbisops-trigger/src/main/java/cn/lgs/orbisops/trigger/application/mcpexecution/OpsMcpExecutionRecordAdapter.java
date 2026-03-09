package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionRecordPort;
import cn.lgs.orbisops.domain.evidence.model.EvidenceDraft;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultBudget;
import cn.lgs.orbisops.domain.evidence.model.ToolResultDraft;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsMcpExecutionRecordAdapter implements McpExecutionRecordPort {

    private final ToolResultApplicationService results;
    private final EvidenceApplicationService evidence;

    public OpsMcpExecutionRecordAdapter(
            ToolResultApplicationService results,
            EvidenceApplicationService evidence) {
        this.results = results;
        this.evidence = evidence;
    }

    @Override
    public McpExecutionRecordedResult record(McpExecutionRecordCommand command) {
        Object output = command.output();
        String outputText = output instanceof String text ? text : JSON.toJSONString(output);
        ToolResult stored = results.record(new ToolResultDraft(
                command.request().config().projectId(),
                "",
                command.request().config().runId(),
                command.request().actor(),
                command.request().config().toolsetId(),
                command.toolName(),
                command.source(),
                command.status(),
                JSON.toJSONString(command.request().input()),
                outputText,
                ToolResultBudget.defaults(),
                command.request().actor(),
                command.durationMs()));
        EvidenceRecord proof = evidence.record(new EvidenceDraft(
                command.request().config().projectId(),
                command.request().config().runId(),
                "MCP",
                command.request().config().toolsetId() + "/" + command.toolName(),
                stored.resultId(),
                stored.outputHash(),
                stored.fullOutputRef(),
                stored.preview(),
                command.verified(),
                Map.of(
                        "mcpId", command.request().config().mcpId(),
                        "toolId", command.request().config().toolId(),
                        "toolName", command.toolName(),
                        "source", command.source()),
                command.request().actor()));
        return new McpExecutionRecordedResult(
                stored.resultId(),
                proof.evidenceId(),
                stored.preview(),
                stored.outputHash(),
                stored.truncated(),
                stored.fullOutputRef(),
                stored.inputHash(),
                stored.durationMs());
    }
}
