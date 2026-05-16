package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

/**
 * Operations datasource sub-agent.
 */
public interface OpsSubAgent {

    String source();

    default String agentId() {
        return source() + "-agent";
    }

    default String displayName() {
        return agentId();
    }

    default String capability() {
        return "查询 " + source() + " 数据源并返回 evidence/gap/adjustment。";
    }

    default boolean realtime() {
        return true;
    }

    OpsAnalysisResponseDTO.InvestigationResultDTO investigate(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                      OpsAgentRunRequestDTO request,
                                                                      OpsAnalysisResponseDTO response,
                                                                      OpsQuestionContext questionContext);

}
