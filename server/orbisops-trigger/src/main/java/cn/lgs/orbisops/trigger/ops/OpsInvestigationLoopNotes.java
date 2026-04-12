package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** User-visible execution-note projection for the unified Investigation loop. */
final class OpsInvestigationLoopNotes {

    List<String> initial(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            boolean followUp,
            String label,
            Set<String> availableSources) {
        String selectedSources = Optional.ofNullable(plan.getTasks()).orElse(List.of()).stream()
                .map(task -> task.getSource() + "(" + task.getReason() + ")")
                .collect(Collectors.joining("；"));
        String selected = selectedSources.isEmpty() ? "无" : selectedSources;
        if (!followUp) {
            return List.of("初始选择数据源：" + selected);
        }
        String available = availableSources.isEmpty()
                ? "无"
                : String.join(",", availableSources);
        return List.of(
                label + " 初始选择数据源：" + selected,
                label + " 可用子 Agent 结果：" + available);
    }

    String parallelBatch(
            boolean followUp,
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch) {
        String agents = batch.stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getAgent)
                .collect(Collectors.joining(", "));
        return followUp
                ? "主 Agent follow-up 并发执行：" + agents
                : "并发执行同优先级子 Agent：" + agents;
    }

    String taskResult(
            boolean followUp,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        return task.getAgent()
                + (followUp ? " follow-up 返回 " : " 返回 ")
                + result.getStatus()
                + "："
                + result.getSummary();
    }

    String inspectedRetry(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO retryRequest) {
        return task.getAgent() + " 证据不足，主 Agent 调整参数后重试：rangeMinutes="
                + retryRequest.getRangeMinutes()
                + "，promWindow="
                + retryRequest.getPromWindow()
                + "。";
    }

    String queuedRetry(
            boolean followUp,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO retryRequest) {
        return followUp
                ? "主 Agent 根据 " + task.getAgent()
                + " 的 follow-up 缺口继续调整：rangeMinutes="
                + retryRequest.getRangeMinutes()
                + "，promWindow="
                + retryRequest.getPromWindow()
                + "。"
                : "主 Agent 根据 " + task.getAgent()
                + " 的缺口反馈调整查询参数：rangeMinutes="
                + retryRequest.getRangeMinutes()
                + "，promWindow="
                + retryRequest.getPromWindow()
                + "。";
    }

    String retryResult(
            boolean followUp,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        return task.getAgent()
                + (followUp ? " follow-up 调整查询返回 " : " 二次查询返回 ")
                + result.getStatus()
                + "："
                + result.getSummary();
    }

    String inspectedRetryResult(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        return task.getAgent()
                + " 调整查询返回 "
                + result.getStatus()
                + "："
                + result.getSummary();
    }

    String executionLimit(boolean followUp, int maxExecutions, String remainingSources) {
        return followUp
                ? "主 Agent follow-up 达到最大子任务执行次数 "
                + maxExecutions
                + "，停止继续派发；剩余数据源："
                + remainingSources
                : "主 Agent 达到最大子任务执行次数 "
                + maxExecutions
                + "，停止继续派发；剩余数据源："
                + remainingSources;
    }
}
