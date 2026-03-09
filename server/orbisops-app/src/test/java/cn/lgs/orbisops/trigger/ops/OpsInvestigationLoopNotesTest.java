package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsInvestigationLoopNotesTest {

    private final OpsInvestigationLoopNotes notes =
            new OpsInvestigationLoopNotes();

    @Test
    void preservesInitialAndFollowUpNoteWording() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(List.of(task("elasticsearch", "es-log-agent")))
                        .build();

        assertEquals(
                List.of("初始选择数据源：elasticsearch(reason)"),
                notes.initial(plan, false, "", Set.of()));
        assertEquals(
                List.of(
                        "StateGraph 初始选择数据源：elasticsearch(reason)",
                        "StateGraph 可用子 Agent 结果：elasticsearch"),
                notes.initial(plan, true, "StateGraph", Set.of("elasticsearch")));
    }

    @Test
    void preservesRetryAndExecutionLimitWording() {
        OpsAnalysisResponseDTO.InvestigationTaskDTO task =
                task("prometheus", "prometheus-agent");
        OpsAgentRunRequestDTO retryRequest = OpsAgentRunRequestDTO.builder()
                .rangeMinutes(40)
                .promWindow("15m")
                .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO result =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .status("FOUND")
                        .summary("metric evidence")
                        .build();

        assertEquals(
                "主 Agent 根据 prometheus-agent 的缺口反馈调整查询参数：rangeMinutes=40，promWindow=15m。",
                notes.queuedRetry(false, task, retryRequest));
        assertEquals(
                "主 Agent 根据 prometheus-agent 的 follow-up 缺口继续调整：rangeMinutes=40，promWindow=15m。",
                notes.queuedRetry(true, task, retryRequest));
        assertEquals(
                "prometheus-agent 二次查询返回 FOUND：metric evidence",
                notes.retryResult(false, task, result));
        assertEquals(
                "prometheus-agent follow-up 调整查询返回 FOUND：metric evidence",
                notes.retryResult(true, task, result));
        assertEquals(
                "主 Agent 达到最大子任务执行次数 8，停止继续派发；剩余数据源：rag",
                notes.executionLimit(false, 8, "rag"));
        assertEquals(
                "主 Agent follow-up 达到最大子任务执行次数 8，停止继续派发；剩余数据源：rag",
                notes.executionLimit(true, 8, "rag"));
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source, String agent) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .reason("reason")
                .build();
    }
}
