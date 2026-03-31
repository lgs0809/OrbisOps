package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionRecordPort;
import cn.lgs.orbisops.domain.evidence.model.EvidenceDraft;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultBudget;
import cn.lgs.orbisops.domain.evidence.model.ToolResultDraft;
import cn.lgs.orbisops.domain.evidence.service.SensitiveDataRedactionPolicy;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class OpsToolExecutionRecordAdapter implements ToolExecutionRecordPort {

    private static final SensitiveDataRedactionPolicy REDACTION =
            new SensitiveDataRedactionPolicy();

    private final ToolResultApplicationService results;
    private final EvidenceApplicationService evidence;

    public OpsToolExecutionRecordAdapter(
            ToolResultApplicationService results,
            EvidenceApplicationService evidence) {
        this.results = results;
        this.evidence = evidence;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public ToolExecutionRecordedResult record(ToolExecutionRecordCommand command) {
        Object redactedOutput = REDACTION.redact(command.output());
        String outputText = redactedOutput instanceof String text
                ? text : JSON.toJSONString(redactedOutput);
        String inputText = JSON.toJSONString(
                REDACTION.redactMap(command.request().arguments()));
        ToolResult stored = results.record(new ToolResultDraft(
                command.request().projectId(),
                command.request().sessionId(),
                command.request().runId(),
                command.request().userId(),
                command.target().toolsetId(),
                command.target().toolName(),
                command.source(),
                command.outputStatus(),
                inputText,
                outputText,
                new ToolResultBudget(200, 32 * 1024, 400, 1000, 60),
                command.request().actor(),
                command.durationMs()));
        EvidenceRecord proof = evidence.record(new EvidenceDraft(
                command.request().projectId(),
                command.request().runId(),
                command.evidenceSourceType(),
                command.target().toolsetId() + "/" + command.target().toolName(),
                stored.resultId(),
                stored.outputHash(),
                stored.fullOutputRef(),
                stored.preview(),
                command.verifiedEvidence(),
                Map.of(
                        "toolsetId", command.target().toolsetId(),
                        "toolName", command.target().toolName(),
                        "source", command.source()),
                command.request().actor()));
        return new ToolExecutionRecordedResult(
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
